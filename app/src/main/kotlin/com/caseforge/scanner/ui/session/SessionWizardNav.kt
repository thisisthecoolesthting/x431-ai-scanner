@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.caseforge.scanner.ui.session

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.caseforge.scanner.R
import com.caseforge.scanner.data.session.CustomerSessionRepository
import com.caseforge.scanner.transfer.SessionEventLogger
import com.caseforge.scanner.ui.components.LoadingState
import com.caseforge.scanner.ui.components.SessionLinkFailureBanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SessionWizardNav(
    sessionId: String,
    onComplete: (ActiveCustomerSession) -> Unit,
) {
    val context = LocalContext.current
    val copy = remember { SessionWizardCopy.load(context) }
    val repo = remember { CustomerSessionRepository(context) }
    val scope = rememberCoroutineScope()

    var step by remember { mutableStateOf(WizardStep.ENGINE_BAY) }
    var engineBayPath by remember { mutableStateOf<String?>(null) }
    var engineBayTriageSummary by remember { mutableStateOf<String?>(null) }
    var engineBayNextStep by remember { mutableStateOf<String?>(null) }
    var engineBayVisionHook by remember { mutableStateOf<String?>(null) }
    var doorJambPath by remember { mutableStateOf<String?>(null) }
    var vin by remember { mutableStateOf<String?>(null) }
    var dashboardPath by remember { mutableStateOf<String?>(null) }
    var isFinishing by remember { mutableStateOf(false) }
    var linkFailureBanner by remember { mutableStateOf<String?>(null) }
    var linkProbeBusy by remember { mutableStateOf(false) }

    LaunchedEffect(step, vin) {
        if (step != WizardStep.DASHBOARD && step != WizardStep.PARTS_SCAN) return@LaunchedEffect
        linkProbeBusy = true
        linkFailureBanner = withContext(Dispatchers.IO) {
            SessionWizardLinkProbe.probe(context, sessionId, vin)
        }
        linkProbeBusy = false
    }

    LaunchedEffect(sessionId) {
        SessionEventLogger.log(context, sessionId, "wizard_start")
        repo.sessionDir(sessionId)
    }

    Column(Modifier.fillMaxSize()) {
        LinearProgressIndicator(
            progress = {
                when (step) {
                    WizardStep.ENGINE_BAY -> 0.25f
                    WizardStep.DOOR_JAMB -> 0.5f
                    WizardStep.DASHBOARD -> 0.75f
                    WizardStep.PARTS_SCAN -> 1f
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            when (step) {
                WizardStep.ENGINE_BAY -> copy.engine_bay.title
                WizardStep.DOOR_JAMB -> copy.door_jamb.title
                WizardStep.DASHBOARD -> copy.dashboard.title
                WizardStep.PARTS_SCAN -> "Parts QR / barcode"
            },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            style = MaterialTheme.typography.titleMedium,
        )
        if (step == WizardStep.ENGINE_BAY || step == WizardStep.DASHBOARD) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                ),
            ) {
                Text(
                    text = stringResource(R.string.wizard_low_lux_photo_hint),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
        if (linkProbeBusy && (step == WizardStep.DASHBOARD || step == WizardStep.PARTS_SCAN)) {
            LoadingState(
                message = "Checking adapter link",
                animatedDots = true,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
        linkFailureBanner?.let { banner ->
            if (step == WizardStep.DASHBOARD || step == WizardStep.PARTS_SCAN) {
                SessionLinkFailureBanner(message = banner)
            }
        }
        Box(Modifier.weight(1f)) {
            if (isFinishing) {
                LoadingState(
                    message = "Finalizing session",
                    animatedDots = true,
                    showLinearProgress = true,
                    progress = 1f,
                    modifier = Modifier.padding(16.dp),
                )
            } else {
                when (step) {
                    WizardStep.ENGINE_BAY -> EngineBayPhotoScreen(
                        sessionId = sessionId,
                        copy = copy,
                        onDone = { path, triage ->
                            engineBayPath = path
                            engineBayTriageSummary = triage.summary
                            engineBayNextStep = triage.nextStep
                            engineBayVisionHook = triage.visionPromptHook
                            step = WizardStep.DOOR_JAMB
                        },
                    )
                    WizardStep.DOOR_JAMB -> DoorJambVinScreen(
                        sessionId = sessionId,
                        copy = copy,
                        onDone = { path, recognizedVin ->
                            doorJambPath = path
                            vin = recognizedVin
                            step = WizardStep.DASHBOARD
                        },
                    )
                    WizardStep.DASHBOARD -> DashboardPhotoScreen(
                        sessionId = sessionId,
                        copy = copy,
                        onDone = { path ->
                            dashboardPath = path
                            step = WizardStep.PARTS_SCAN
                        },
                    )
                    WizardStep.PARTS_SCAN -> SessionPartsScanScreen(
                        sessionId = sessionId,
                        onDone = {
                            isFinishing = true
                            scope.launch {
                                try {
                                    repo.persistWizardResult(
                                        sessionId = sessionId,
                                        vin = vin,
                                        engineBayPath = engineBayPath,
                                        doorJambPath = doorJambPath,
                                        dashboardPath = dashboardPath,
                                    )
                                    SessionEventLogger.log(
                                        context,
                                        sessionId,
                                        "wizard_complete",
                                        detail = vin.orEmpty(),
                                        extra = mapOf(
                                            "engine_bay_triage" to engineBayTriageSummary.orEmpty(),
                                            "engine_bay_next_step" to engineBayNextStep.orEmpty(),
                                            "engine_bay_vision_hook" to engineBayVisionHook.orEmpty(),
                                        ),
                                    )
                                    onComplete(
                                        ActiveCustomerSession(
                                            sessionId = sessionId,
                                            vin = vin,
                                            engineBayPhotoPath = engineBayPath,
                                            doorJambPhotoPath = doorJambPath,
                                            dashboardPhotoPath = dashboardPath,
                                            engineBayTriageSummary = engineBayTriageSummary,
                                            engineBaySuggestedNextStep = engineBayNextStep,
                                            engineBayVisionPromptHook = engineBayVisionHook,
                                        ),
                                    )
                                } finally {
                                    isFinishing = false
                                }
                            }
                        },
                    )
                }
            }
        }
    }
}
