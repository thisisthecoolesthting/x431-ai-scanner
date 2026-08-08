@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.caseforge.scanner.ui.session

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import android.util.Log
import com.caseforge.scanner.App
import com.caseforge.scanner.R
import com.caseforge.scanner.agent.SessionBackgroundScanner
import com.caseforge.scanner.agent.SessionWorkflowEngine
import com.caseforge.scanner.agent.session.BackgroundObdSnapshot
import com.caseforge.scanner.agent.session.DiagnosticPhotoInsights
import com.caseforge.scanner.agent.session.DiagnosticPhotoInsightsCodec
import com.caseforge.scanner.agent.session.SessionCopilotRegistry
import com.caseforge.scanner.agent.session.SessionDiagnosticVision
import com.caseforge.scanner.agent.session.SessionLiveObdPoller
import com.caseforge.scanner.agent.session.SessionTokenAccounting
import com.caseforge.scanner.data.SettingsRepo
import com.caseforge.scanner.data.session.CustomerSessionRepository
import com.caseforge.scanner.transfer.SessionEventLogger
import kotlinx.coroutines.Dispatchers
import com.caseforge.scanner.ui.components.LoadingState
import com.caseforge.scanner.vin.VinNormalizer
import com.caseforge.scanner.voice.VoiceMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "SessionChatScreen"

/** VIN keys [customer_sessions] only for a normalized 17-character valid ISO VIN (wizard contract). */
private fun customerRollupVin(vin: String?): String? =
    vin?.trim()?.uppercase()?.takeIf { VinNormalizer.hasValidCharset(it) }

/**
 * Three-zone session chat: visual strip → transcript → voice-or-text input.
 * Wizard photos trigger Claude vision on enter; live OBD snapshot persists to Room by VIN.
 */
@Composable
fun SessionChatScreen(
    session: ActiveCustomerSession,
    settings: SettingsRepo,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val app = context.applicationContext as App
    val scope = rememberCoroutineScope()
    val repo = remember { CustomerSessionRepository(context) }
    val workflow = remember { SessionWorkflowEngine(context, settings) }
    val scanner = remember { SessionBackgroundScanner(context, settings) }
    val obdPoller = remember { SessionLiveObdPoller(context, settings) }

    var linkStatus by remember { mutableStateOf(context.getString(R.string.session_linking)) }
    var visionStatus by remember { mutableStateOf<String?>(null) }
    var dtcSummary by remember { mutableStateOf<String?>(null) }
    var discoveryReport by remember {
        mutableStateOf<com.caseforge.scanner.agent.discovery.DiscoveryReport?>(null)
    }
    var photoInsights by remember { mutableStateOf<DiagnosticPhotoInsights?>(null) }
    var obdSnapshot by remember { mutableStateOf(BackgroundObdSnapshot()) }
    var visualPane by remember {
        mutableStateOf(
            SessionVisualComposer.PaneState(
                primary = SessionVisualComposer.StripItem.PhotoThumbnails(session.photoPaths()),
            ),
        )
    }

    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var bootstrapLoading by remember { mutableStateOf(true) }
    var criticalChoice by remember { mutableStateOf<CriticalChoice?>(null) }
    val transcript = remember { mutableStateListOf<SessionChatMessage>() }
    val listState = rememberLazyListState()
    val voiceEnabled = settings.voiceEnabled
    val onTechMessage = remember { mutableStateOf<(String) -> Unit>({}) }

    DisposableEffect(session.sessionId) {
        SessionCopilotRegistry.bind(session)
        SessionTokenAccounting.beginSession(session.sessionId)
        onDispose {
            SessionGaugeUiState.clearSession(session.sessionId)
            SessionCopilotRegistry.bind(null)
            SessionTokenAccounting.endSession(
                context = context.applicationContext,
                settings = settings,
                sessionId = session.sessionId,
                vin = customerRollupVin(session.vin),
            )
        }
    }

    val voiceLoop = remember(voiceEnabled) {
        SessionVoiceLoop(
            context = context,
            tts = app.tts,
            enabled = voiceEnabled,
            onTechUtterance = { text -> onTechMessage.value(text) },
            onVoiceCriticalChoice = { opt ->
                criticalChoice = null
                onTechMessage.value(opt.label)
            },
        )
    }

    DisposableEffect(voiceEnabled) {
        if (voiceEnabled) voiceLoop.start() else voiceLoop.stop()
        onDispose { voiceLoop.stop() }
    }

    LaunchedEffect(criticalChoice) {
        voiceLoop.bindCriticalChoice(criticalChoice)
    }

    val voiceState by voiceLoop.voiceState.collectAsState()

    fun refreshVisualPane(agentText: String? = null) {
        val pane = SessionVisualComposer.compose(
            context = context,
            sessionId = session.sessionId,
            session = session,
            discoveryReport = discoveryReport,
            obd = obdSnapshot,
            lastAgentText = agentText,
            lastToolHint = null,
            photoInsights = photoInsights,
        )
        visualPane = pane
        SessionVisualComposer.logPaneState(context, session.sessionId, pane)
    }

    suspend fun handleTechTurn(text: String) {
        if (text.isBlank() || busy) return
        busy = true
        try {
            transcript.add(SessionChatMessage("tech", text = text))
            var streamingIndex = -1
            if (settings.deepseekStreamingEnabled) {
                streamingIndex = transcript.size
                transcript.add(
                    SessionChatMessage(
                        role = "assistant",
                        type = SessionMessageType.TEXT,
                        text = "",
                    ),
                )
            }
            customerRollupVin(session.vin)?.let { v ->
                repo.updateNeedAndDtc(v, session.sessionId, text, dtcSummary ?: obdSnapshot.dtcSummary)
                if (obdSnapshot.connected) {
                    repo.persistObdSnapshot(v, session.sessionId, obdSnapshot)
                }
            }
            val prior = customerRollupVin(session.vin)?.let { repo.loadByVin(it) }
            val turns = transcript.map { SessionWorkflowEngine.ChatTurn(it.role, it.text) }
            val reply = workflow.nextQuestion(
                sessionId = session.sessionId,
                vin = session.vin,
                photoPaths = session.photoPaths(),
                discoveryReport = discoveryReport,
                dtcSummary = dtcSummary ?: obdSnapshot.dtcSummary,
                priorVisits = prior,
                needDescription = text,
                transcript = turns,
                obdSnapshot = obdSnapshot,
                photoInsights = photoInsights
                    ?: DiagnosticPhotoInsightsCodec.decode(prior?.photoDiagnosticJson),
                onStreamingText = { partial ->
                    if (streamingIndex >= 0 && streamingIndex < transcript.size) {
                        transcript[streamingIndex] = transcript[streamingIndex].copy(text = partial)
                    }
                },
            )
            reply.detailExpansion?.let {
                transcript.add(SessionChatMessage("system", text = it))
            }
            val finalAssistantMessage = SessionChatMessage(
                role = "assistant",
                type = if (reply.visualAttachments.isNotEmpty()) {
                    SessionMessageType.VISUAL_CARD
                } else {
                    SessionMessageType.TEXT
                },
                text = reply.question,
                visualAttachments = reply.visualAttachments,
            )
            if (streamingIndex >= 0 && streamingIndex < transcript.size) {
                transcript[streamingIndex] = finalAssistantMessage
            } else {
                transcript.add(finalAssistantMessage)
            }
            refreshVisualPane(reply.question)
            if (voiceEnabled) voiceLoop.speakThenListen(reply.question)
            listState.animateScrollToItem(transcript.lastIndex.coerceAtLeast(0))
        } catch (t: Throwable) {
            Log.e(TAG, "Chat turn failed", t)
            transcript.add(
                SessionChatMessage(
                    role = "system",
                    text = context.getString(R.string.session_assistant_internal_error),
                ),
            )
        } finally {
            busy = false
        }
    }

    SideEffect {
        onTechMessage.value = { text -> scope.launch { handleTechTurn(text) } }
    }

    LaunchedEffect(session.sessionId, session.vin) {
        bootstrapLoading = true
        try {
            SessionEventLogger.log(context, session.sessionId, "chat_enter", detail = session.vin.orEmpty())
            val prior = customerRollupVin(session.vin)?.let { repo.loadByVin(it) }
            prior?.lastNeedDescription?.let { need ->
                transcript.add(SessionChatMessage("system", text = "Prior visit: $need"))
            }
            prior?.lastDtcSummary?.let { old ->
                transcript.add(SessionChatMessage("system", text = "Prior visit DTCs: $old"))
            }
            repo.loadObdSnapshot(prior)?.let { saved ->
                obdSnapshot = saved
                dtcSummary = saved.dtcSummary ?: dtcSummary
                linkStatus = saved.linkStatus
            }

            val snap = scanner.run(session.sessionId, session.vin)
            linkStatus = snap.linkStatus
            dtcSummary = snap.dtcSummary ?: dtcSummary
            discoveryReport = snap.discoveryReport
            if (snap.dtcSummary != null) {
                obdSnapshot = obdSnapshot.copy(
                    connected = true,
                    linkStatus = snap.linkStatus,
                    dtcSummary = snap.dtcSummary,
                    protocol = obdSnapshot.protocol ?: "ISO15765-4 CAN 11/500",
                    ecuAddress = obdSnapshot.ecuAddress ?: "7E0",
                )
            } else {
                obdSnapshot = obdSnapshot.copy(linkStatus = snap.linkStatus, dtcSummary = snap.dtcSummary)
            }
            snap.dtcSummary?.let {
                transcript.add(
                    SessionChatMessage(
                        role = "system",
                        type = SessionMessageType.DTC_TABLE,
                        text = "Background scan complete",
                        visualAttachments = SessionVisualComposer.attachmentsForAgentText("", obdSnapshot),
                    ),
                )
            }

            if (session.photoPaths().isNotEmpty()) {
                visionStatus = "Analyzing bay and dashboard photos…"
                val cached = DiagnosticPhotoInsightsCodec.decode(prior?.photoDiagnosticJson)
                val insights = workflow.runInitialPhotoVision(
                    sessionId = session.sessionId,
                    photoPaths = session.photoPaths(),
                    vin = session.vin,
                    priorVisits = prior,
                ) ?: cached
                photoInsights = insights
                session.vin?.let { v ->
                    insights?.let { repo.persistPhotoDiagnostics(v, session.sessionId, it) }
                }
                visionStatus = when {
                    insights != null -> "Photo analysis ready (${insights.confidence} confidence)"
                    settings.claudeApiKey.isBlank() -> "Photo analysis skipped — set API key in Settings"
                    else -> "Photo analysis unavailable"
                }
                insights?.let { ins ->
                    val vision = SessionDiagnosticVision(context, settings)
                    transcript.add(
                        SessionChatMessage(
                            role = "system",
                            text = vision.formatInsightsForChat(ins),
                            visualAttachments = SessionVisualComposer.attachmentsForPhotoInsights(ins),
                        ),
                    )
                }
            }
            session.engineBayTriageSummary?.takeIf { it.isNotBlank() }?.let { triage ->
                transcript.add(
                    SessionChatMessage(
                        role = "system",
                        text = buildString {
                            append("Engine bay triage: ")
                            append(triage)
                            session.engineBaySuggestedNextStep?.takeIf { it.isNotBlank() }?.let {
                                append("\nNext step: ")
                                append(it)
                            }
                        },
                    ),
                )
            }

            refreshVisualPane()
            customerRollupVin(session.vin)?.let { v ->
                if (obdSnapshot.connected) repo.persistObdSnapshot(v, session.sessionId, obdSnapshot)
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Session chat bootstrap failed", t)
            linkStatus = context.getString(R.string.session_init_error)
            transcript.add(
                SessionChatMessage(
                    role = "system",
                    text = context.getString(R.string.session_bootstrap_failed),
                ),
            )
        } finally {
            bootstrapLoading = false
        }
    }

    LaunchedEffect(session.sessionId, obdSnapshot.connected) {
        if (!settings.isPlanBTierEffective(0) && !settings.nativeObdExperimental) return@LaunchedEffect
        while (true) {
            try {
                val polled = obdPoller.poll(session.vin, obdSnapshot.linkStatus)
                obdSnapshot = polled
                session.vin?.let { v ->
                    if (polled.connected) repo.persistObdSnapshot(v, session.sessionId, polled)
                }
                refreshVisualPane(transcript.lastOrNull { it.role == "assistant" }?.text)
            } catch (t: Throwable) {
                Log.w(TAG, "OBD polling tick failed", t)
            }
            delay(3_000)
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        TopAppBar(
            title = { Text(stringResource(R.string.app_name)) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.a11y_nav_back),
                    )
                }
            },
        )
        val statusLine = listOfNotNull(visionStatus ?: linkStatus)
            .joinToString(" · ")
        Text(
            statusLine,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        session.vin?.let {
            Text(
                "VIN $it",
                modifier = Modifier.padding(horizontal = 16.dp),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }

        SessionVisualStrip(
            sessionId = session.sessionId,
            pane = visualPane,
            obdSnapshot = obdSnapshot,
            modifier = Modifier.fillMaxWidth(),
        )

        val hasUserOrAssistant = transcript.any { it.role == "tech" || it.role == "assistant" }
        val isVisualEmptyChat = !hasUserOrAssistant

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 12.dp),
        ) {
            if (isVisualEmptyChat) {
                item {
                    Column(
                        Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        SessionRpmTachLarge(
                            snapshot = obdSnapshot,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                        if (transcript.isEmpty()) {
                            if (bootstrapLoading) {
                                LoadingState(
                                    message = "Preparing session bootstrap",
                                    animatedDots = true,
                                    showLinearProgress = true,
                                    modifier = Modifier.padding(top = 16.dp),
                                )
                            } else {
                                Text(
                                    if (voiceEnabled) "Describe the need out loud…" else "Describe the need…",
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 16.dp),
                                )
                                Text(
                                    "e.g. crank no start, program key fob, check codes",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
            if (busy && !bootstrapLoading) {
                item {
                    LoadingState(
                        message = "AI thinking",
                        animatedDots = true,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
            itemsIndexed(
                transcript,
                key = { index, _ -> "session-chat-$index" },
            ) { _, line ->
                SessionMessageContent(message = line, modifier = Modifier.fillMaxWidth())
            }
        }

        if (voiceEnabled && voiceState == VoiceMode.State.CAPTURING) {
            Text(
                "Listening…",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        if (!voiceEnabled) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Describe the need…") },
                    maxLines = 4,
                    enabled = !busy,
                )
                IconButton(
                    onClick = {
                        val text = input.trim()
                        if (text.isBlank() || busy) return@IconButton
                        input = ""
                        scope.launch { handleTechTurn(text) }
                    },
                    enabled = !busy && input.isNotBlank(),
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = stringResource(R.string.a11y_send_message),
                    )
                }
            }
        }

        criticalChoice?.let { choice ->
            CriticalChoiceSheet(
                choice = choice,
                onSelect = { opt ->
                    criticalChoice = null
                    scope.launch { handleTechTurn(opt.label) }
                },
                onDismiss = { criticalChoice = null },
            )
        }
    }
}

@Composable
private fun CriticalChoiceSheet(
    choice: CriticalChoice,
    onSelect: (CriticalOption) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(choice.prompt, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            choice.options.take(3).forEach { opt ->
                Button(
                    onClick = { onSelect(opt) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(opt.label)
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
