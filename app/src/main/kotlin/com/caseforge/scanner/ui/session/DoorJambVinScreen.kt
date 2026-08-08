@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.caseforge.scanner.ui.session

import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.caseforge.scanner.data.SettingsRepo
import com.caseforge.scanner.data.session.CustomerSessionRepository
import com.caseforge.scanner.transfer.SessionEventLogger
import com.caseforge.scanner.ui.components.LoadingState
import com.caseforge.scanner.vin.VinNormalizer
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun DoorJambVinScreen(
    sessionId: String,
    copy: WizardCopyRoot,
    onDone: (photoPath: String?, vin: String?) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember { CustomerSessionRepository(context) }
    val settings = remember { SettingsRepo(context) }
    val file = remember(sessionId) { repo.photoFile(sessionId, "door_jamb.jpg") }
    var phase by remember { mutableStateOf("camera") }
    var vinDraft by remember { mutableStateOf("") }
    var ocrBusy by remember { mutableStateOf(false) }
    var showSkipWarn by remember { mutableStateOf(false) }

    fun runOcr() {
        if (!file.isFile) {
            phase = "confirm"
            return
        }
        if (!settings.deepseekOcrEnabled) {
            phase = "confirm"
            return
        }
        ocrBusy = true
        scope.launch {
            val recognized = withContext(Dispatchers.Default) {
                runCatching {
                    val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                    val image = InputImage.fromFilePath(context, Uri.fromFile(file))
                    val task = recognizer.process(image)
                    val visionText = com.google.android.gms.tasks.Tasks.await(task)
                    val candidates = VinNormalizer.extractCandidates(visionText.text.orEmpty())
                    VinNormalizer.pickBest(candidates)?.normalizedVin
                }.getOrNull()
            }
            if (!recognized.isNullOrBlank()) vinDraft = recognized
            ocrBusy = false
            phase = "confirm"
        }
    }

    when (phase) {
        "camera" -> SessionPhotoCapture(
            outputFile = file,
            hint = copy.door_jamb.hint,
            skipLabel = copy.engine_bay.skip_label,
            autoLaunch = true,
            onCaptured = { path ->
                SessionEventLogger.log(context, sessionId, "wizard_door_jamb", detail = path ?: "skipped")
                if (path != null) runOcr() else phase = "confirm"
            },
            onSkip = { showSkipWarn = true },
        )
        "confirm" -> Column(
            Modifier.fillMaxSize().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(copy.door_jamb.title, style = MaterialTheme.typography.headlineSmall)
            if (ocrBusy) {
                LoadingState(
                    message = "Reading VIN",
                    animatedDots = true,
                    showLinearProgress = true,
                )
            }
            OutlinedTextField(
                value = vinDraft,
                onValueChange = { vinDraft = VinNormalizer.normalizeOcrText(it).take(17) },
                label = { Text("VIN") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Button(
                onClick = {
                    val raw = vinDraft.trim()
                    val normalized = VinNormalizer.normalizeToVin(raw)
                        ?: raw.takeIf { VinNormalizer.hasValidCharset(it) }
                    val v = normalized?.take(17)
                    SessionEventLogger.log(context, sessionId, "wizard_vin_confirmed", detail = v ?: "none")
                    onDone(file.takeIf { it.isFile }?.absolutePath, v)
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(copy.door_jamb.confirm_label)
            }
            OutlinedButton(
                onClick = {
                    SessionEventLogger.log(context, sessionId, "wizard_vin_confirmed", detail = "none")
                    onDone(file.takeIf { it.isFile }?.absolutePath, null)
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Continue without VIN")
            }
        }
    }

    if (showSkipWarn) {
        AlertDialog(
            onDismissRequest = { showSkipWarn = false },
            title = { Text("Skip VIN?") },
            text = { Text(copy.door_jamb.skip_warning) },
            confirmButton = {
                TextButton(onClick = {
                    showSkipWarn = false
                    SessionEventLogger.log(context, sessionId, "wizard_door_jamb", detail = "skipped_warned")
                    onDone(null, null)
                }) { Text("Skip anyway") }
            },
            dismissButton = {
                TextButton(onClick = { showSkipWarn = false }) { Text("Go back") }
            },
        )
    }
}
