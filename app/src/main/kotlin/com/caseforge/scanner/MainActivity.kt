@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.caseforge.scanner

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.caseforge.scanner.agent.AgentRunner
import com.caseforge.scanner.agent.ScannerAccessibilityService
import com.caseforge.scanner.ai.ClaudeClient
import com.caseforge.scanner.ai.Prompts
import com.caseforge.scanner.overlay.OverlayService
import com.caseforge.scanner.overlay.ScreenCaptureService
import com.caseforge.scanner.data.DtcEntity
import com.caseforge.scanner.data.SessionEntity
import com.caseforge.scanner.ui.fullscan.FullScanResultsScreen
import com.caseforge.scanner.ui.history.HistoryScreen
import com.caseforge.scanner.ui.notes.AgentNotesScreen
import com.caseforge.scanner.ui.log.ActionLogScreen
import com.caseforge.scanner.ui.home.HomeScreen
import com.caseforge.scanner.ui.dashboard.DashboardScreen
import com.caseforge.scanner.ui.pending.PendingApprovalsScreen
import com.caseforge.scanner.ui.settings.SettingsScreen
import com.caseforge.scanner.ui.theme.CaseForgeTheme
import com.caseforge.scanner.ui.talk.TalkToAgentScreen
import com.caseforge.scanner.ui.triage.TriageScreen
import com.caseforge.scanner.ui.wizard.SetupWizardScreen
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    companion object {
        const val EXTRA_REPORT_TEXT = "report_text"
        const val EXTRA_REPORT_SOURCE = "report_source"
    }

    private val app: App by lazy { application as App }
    private var latestDetectedVin: String? = null

    private val mediaProjectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val svc = Intent(this, ScreenCaptureService::class.java).apply {
                putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, result.resultCode)
                putExtra(ScreenCaptureService.EXTRA_RESULT_DATA, result.data)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(svc)
            else startService(svc)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sharedReport = intent.getStringExtra(EXTRA_REPORT_TEXT)

        ScannerAccessibilityService.onVinDetected = { vin ->
            latestDetectedVin = vin
            if (app.settings.autoStartOnVin && !app.settings.killSwitch) {
                lifecycleScope.launch { runAgent(vin = vin, symptom = null) }
            }
        }

        setContent {
            CaseForgeTheme(mode = app.settings.themeMode) {
                val initialRoute = when {
                    sharedReport != null -> "triage"
                    !app.settings.wizardComplete -> "wizard"
                    else -> "dashboard"
                }
                var route by remember { mutableStateOf(initialRoute) }
                var triageInput by remember { mutableStateOf(sharedReport.orEmpty()) }
                var triageOutput by remember { mutableStateOf("") }
                var busy by remember { mutableStateOf(false) }
                var lastVin by remember { mutableStateOf<String?>(null) }
                var lastVehicleSummary by remember { mutableStateOf<String?>(null) }

                Scaffold(topBar = { TopAppBar(title = { Text("Launch AI") }) }) { pad ->
                    Box(Modifier.padding(pad).fillMaxSize()) {
                        when (route) {
                            "wizard" -> SetupWizardScreen(
                                settings = app.settings,
                                onOpenA11y = { openAccessibilitySettings() },
                                onGrantOverlay = {
                                    if (!Settings.canDrawOverlays(this@MainActivity)) {
                                        startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                            android.net.Uri.parse("package:$packageName")))
                                    }
                                },
                                onGrantCapture = { requestProjection() },
                                onStartBubble = { startBubble() },
                                onFinish = {
                                    app.settings.wizardComplete = true
                                    route = "dashboard"
                                },
                            )
                            "dashboard" -> DashboardScreen(
                                detectedVin = latestDetectedVin ?: lastVin,
                                vehicleSummary = lastVehicleSummary,
                                speakEnabled = app.settings.speakEnabled,
                                onSpeakToggle = { app.settings.speakEnabled = it },
                                onAgentStart = { symptom ->
                                    lifecycleScope.launch {
                                        runAgent(vin = latestDetectedVin, symptom = symptom)
                                    }
                                },
                                onAgentStop = {
                                    // The agent runner observes the parent Job;
                                    // for now route them to the kill switch.
                                    app.settings.killSwitch = true
                                    app.settings.killSwitch = false
                                },
                                onFullScan = {
                                    lifecycleScope.launch {
                                        runFullScan(vin = latestDetectedVin)
                                        route = "fullscan_results"
                                    }
                                },
                                onQuickProcedure = { id, label ->
                                    val symptom = "Perform the \"$label\" procedure on this vehicle. " +
                                        "Use the X431 app's matching menu. Walk through any prompts. " +
                                        "Procedure id: $id."
                                    lifecycleScope.launch {
                                        runAgent(vin = latestDetectedVin, symptom = symptom)
                                    }
                                },
                                onOpenSetup = { route = "home" },
                                onOpenHistory = { route = "history" },
                                onOpenLog = { route = "log" },
                                onOpenNotes = { route = "notes" },
                                onCheckUpdate = {
                                    lifecycleScope.launch(Dispatchers.IO) {
                                        try {
                                            com.caseforge.scanner.agent.AgentStatus.setActivity("Checking for update...")
                                            val info = com.caseforge.scanner.agent.Updater.checkLatest()
                                            if (com.caseforge.scanner.agent.Updater.isNewer(info)) {
                                                com.caseforge.scanner.agent.AgentStatus.setActivity("New build ${info.sha} available — downloading...")
                                                com.caseforge.scanner.agent.Updater.downloadAndInstall(applicationContext) { msg ->
                                                    com.caseforge.scanner.agent.AgentStatus.setActivity(msg)
                                                }
                                            } else {
                                                com.caseforge.scanner.agent.AgentStatus.setActivity("Already on latest (${info.sha})")
                                            }
                                        } catch (t: Throwable) {
                                            com.caseforge.scanner.agent.AgentStatus.setActivity("Update check failed: ${t.message?.take(120) ?: t.javaClass.simpleName}")
                                        }
                                    }
                                },
                            )
                            "home" -> HomeScreen(
                                onOpenSettings = { route = "settings" },
                                onStartBubble = { startBubble() },
                                onGrantCapture = { requestProjection() },
                                onOpenA11y = { openAccessibilitySettings() },
                                onManualTriage = {
                                    route = "triage"
                                    triageInput = ""; triageOutput = ""
                                },
                                onRunAgentNow = {
                                    lifecycleScope.launch { runAgent(vin = null, symptom = null) }
                                },
                                onRunFullScan = {
                                    lifecycleScope.launch {
                                        runFullScan(vin = null)
                                        route = "fullscan_results"
                                    }
                                },
                                onOpenHistory = { route = "history" },
                                onOpenApprovals = { route = "approvals" },
                                onTalkToAgent = { route = "talk" },
                                onOpenLog = { route = "log" },
                                onBack = { route = "dashboard" },
                            )
                            "settings" -> SettingsScreen(
                                settings = app.settings,
                                onBack = { route = "home" },
                            )
                            "history" -> HistoryScreen(db = app.db, onBack = { route = "home" })
                            "log" -> ActionLogScreen(actionLog = app.actionLog, onBack = { route = "home" })
                            "notes" -> AgentNotesScreen(settings = app.settings, onBack = { route = "dashboard" })
                            "approvals" -> PendingApprovalsScreen(onBack = { route = "home" })
                            "talk" -> TalkToAgentScreen(
                                onSend = { symptom ->
                                    lifecycleScope.launch {
                                        runAgent(vin = null, symptom = symptom.ifBlank { null })
                                    }
                                    route = "home"
                                },
                                onBack = { route = "home" },
                            )
                            "fullscan_results" -> FullScanResultsScreen(
                                db = app.db,
                                onBack = { route = "home" },
                            )
                            "triage" -> TriageScreen(
                                initialText = triageInput,
                                output = triageOutput,
                                busy = busy,
                                onRun = { text ->
                                    busy = true
                                    lifecycleScope.launch {
                                        val out = runReportTriage(text)
                                        triageOutput = out
                                        busy = false
                                    }
                                },
                                onBack = { route = "home" },
                            )
                        }
                    }
                }
            }
        }
    }

    private fun startBubble() {
        val canDraw = Settings.canDrawOverlays(this)
        if (!canDraw) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:$packageName")))
            return
        }
        val svc = Intent(this, OverlayService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(svc) else startService(svc)
    }

    private fun requestProjection() {
        val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjectionLauncher.launch(mpm.createScreenCaptureIntent())
    }

    private fun openAccessibilitySettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    private suspend fun runReportTriage(reportText: String): String {
        val key = app.settings.claudeApiKey
        if (key.isBlank()) return "Set a Claude API key in Settings first."
        return withContext(Dispatchers.IO) {
            try {
                val client = ClaudeClient(apiKey = key, model = app.settings.model)
                val resp = client.sendMessages(
                    system = Prompts.DTC_TRIAGE_FROM_REPORT,
                    messages = listOf(ClaudeClient.userText(reportText)),
                    maxTokens = 2048,
                )
                resp.firstText().orEmpty()
            } catch (t: Throwable) {
                "Error: ${t.message}"
            }
        }
    }

    private suspend fun runAgent(vin: String?, symptom: String?) {
        runAgentSession(vin = vin, symptom = symptom, scope = "diagnostic")
    }

    private suspend fun runFullScan(vin: String?) {
        runAgentSession(vin = vin, symptom = Prompts.FULL_SCAN_SENTINEL, scope = "fullscan")
    }

    private suspend fun runAgentSession(vin: String?, symptom: String?, scope: String) {
        val key = app.settings.claudeApiKey
        if (key.isBlank()) { toast("Set a Claude API key in Settings first."); return }
        if (app.settings.killSwitch) { toast("Kill switch is on — disable in Settings."); return }
        if (ScannerAccessibilityService.instance() == null) {
            toast("Enable the CaseForge accessibility service first.")
            app.actionLog.event("session.aborted", "a11y service not running")
            return
        }
        try {
            withContext(Dispatchers.IO) {
                val client = ClaudeClient(apiKey = key, model = app.settings.model)
                val runner = AgentRunner(
                    context = applicationContext,
                    claude = client,
                    log = app.actionLog,
                    screenshot = {
                        val base64 = ScreenCaptureService.captureJpegBase64()
                        if (base64 != null) AgentRunner.ImagePayload("image/jpeg", base64) else null
                    },
                    requireApproval = app.settings.requireApproval,
                    agentNotes = app.settings.agentNotes,
                )
                val started = System.currentTimeMillis()
                val outcome = runner.run(vin = vin, symptom = symptom)
                runCatching {
                    val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
                    val summary = outcome.summary
                    val symptomToPersist = if (symptom == Prompts.FULL_SCAN_SENTINEL) null else symptom
                    val sessionId = app.db.sessionDao().insertSession(
                        SessionEntity(
                            vin = vin,
                            startedAt = started,
                            endedAt = System.currentTimeMillis(),
                            symptom = symptomToPersist,
                            rootCause = jsonStringOrNull(summary, "root_cause"),
                            recommendedRepair = jsonStringOrNull(summary, "recommended_repair"),
                            transcriptJson = json.encodeToString(
                                kotlinx.serialization.builtins.ListSerializer(ClaudeClient.Message.serializer()),
                                outcome.transcript
                            ),
                            scope = scope,
                        )
                    )
                    extractDtcs(summary).forEach { dtc ->
                        app.db.sessionDao().insertDtc(dtc.copy(sessionId = sessionId))
                    }
                    app.actionLog.event("session.persisted", "id=$sessionId scope=$scope reason=${outcome.stoppedReason}")
                }.onFailure { app.actionLog.event("session.persist_error", it.message.orEmpty()) }

                lifecycleScope.launch(Dispatchers.Main) {
                    toast(
                        if (outcome.finished) "Agent finished — see History."
                        else "Agent stopped: ${outcome.stoppedReason.take(220)}"
                    )
                }
            }
        } catch (t: Throwable) {
            app.actionLog.event("session.error", t.message.orEmpty())
            com.caseforge.scanner.agent.AgentStatus.setActivity("Agent error: ${t.message?.take(220) ?: t.javaClass.simpleName}")
            lifecycleScope.launch(Dispatchers.Main) {
                toast("Agent error: ${t.message?.take(100) ?: t.javaClass.simpleName}")
            }
        }
    }

    private fun toast(msg: String) {
        runOnUiThread { Toast.makeText(this, msg, Toast.LENGTH_LONG).show() }
    }

    private fun jsonStringOrNull(obj: JsonObject?, key: String): String? {
        val el = obj?.get(key) ?: return null
        val s = (el as? JsonPrimitive)?.contentOrNullSafe ?: return null
        return s.ifBlank { null }
    }

    private fun extractDtcs(summary: JsonObject?): List<DtcEntity> {
        val arr = (summary?.get("dtcs_found") as? JsonArray) ?: return emptyList()
        return arr.mapNotNull { el ->
            val obj = (el as? JsonObject) ?: return@mapNotNull null
            val code = (obj["code"] as? JsonPrimitive)?.contentOrNullSafe?.takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            DtcEntity(
                sessionId = 0L,
                code = code,
                module = (obj["module"] as? JsonPrimitive)?.contentOrNullSafe,
                description = (obj["description"] as? JsonPrimitive)?.contentOrNullSafe,
                status = (obj["status"] as? JsonPrimitive)?.contentOrNullSafe,
            )
        }
    }
}

private val JsonPrimitive.contentOrNullSafe: String?
    get() = try { if (this is kotlinx.serialization.json.JsonNull) null else content } catch (_: Throwable) { null }
