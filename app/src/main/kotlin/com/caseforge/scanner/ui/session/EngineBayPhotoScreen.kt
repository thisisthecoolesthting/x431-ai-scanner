package com.caseforge.scanner.ui.session

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.caseforge.scanner.data.session.CustomerSessionRepository
import com.caseforge.scanner.transfer.SessionEventLogger

@Composable
fun EngineBayPhotoScreen(
    sessionId: String,
    copy: WizardCopyRoot,
    onDone: (photoPath: String?, triage: EngineBayTriageResult) -> Unit,
) {
    val context = LocalContext.current
    val repo = remember { CustomerSessionRepository(context) }
    val file = remember(sessionId) { repo.photoFile(sessionId, "engine_bay.jpg") }

    SessionPhotoCapture(
        outputFile = file,
        hint = copy.engine_bay.hint,
        skipLabel = copy.engine_bay.skip_label,
        autoLaunch = true,
        onCaptured = { path ->
            val triage = EngineBayPhotoTriage.classify(path)
            SessionEventLogger.log(
                context,
                sessionId,
                "wizard_engine_bay",
                detail = path ?: "skipped",
                extra = mapOf(
                    "triage" to triage.classification,
                    "next_step" to triage.nextStep,
                    "vision_hook" to triage.visionPromptHook,
                ),
            )
            onDone(path, triage)
        },
        onSkip = {
            val triage = EngineBayPhotoTriage.classify(null)
            SessionEventLogger.log(
                context,
                sessionId,
                "wizard_engine_bay",
                detail = "skipped",
                extra = mapOf(
                    "triage" to triage.classification,
                    "next_step" to triage.nextStep,
                ),
            )
            onDone(null, triage)
        },
    )
}
