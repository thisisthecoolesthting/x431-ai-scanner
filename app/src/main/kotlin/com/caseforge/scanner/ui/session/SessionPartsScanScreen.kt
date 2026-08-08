@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.caseforge.scanner.ui.session

import android.graphics.BitmapFactory
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.caseforge.scanner.data.session.CustomerSessionRepository
import com.caseforge.scanner.transfer.SessionEventLogger
import com.caseforge.scanner.ui.components.LoadingState
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import android.util.Log
import java.io.File
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

private const val TAG = "SessionPartsScanScreen"

@Composable
fun SessionPartsScanScreen(
    sessionId: String,
    onDone: () -> Unit,
) {
    val context = LocalContext.current
    val repo = remember { CustomerSessionRepository(context) }
    val captureFile = remember(sessionId) { repo.photoFile(sessionId, "parts_label.jpg") }
    var scannedText by remember { mutableStateOf("") }
    var phase by remember { mutableStateOf("intro") }
    var decodeLoading by remember { mutableStateOf(false) }

    when (phase) {
        "camera" -> SessionPhotoCapture(
            outputFile = captureFile,
            hint = "Capture a part label QR/barcode.",
            autoLaunch = true,
            onCaptured = { path ->
                decodeLoading = true
                val decoded = decodeBarcodeFromImage(path)
                scannedText = decoded.orEmpty()
                SessionEventLogger.log(
                    context = context,
                    sessionId = sessionId,
                    kind = "wizard_parts_scan_capture",
                    detail = path ?: "capture_failed",
                    extra = mapOf("decoded" to (decoded ?: "none")),
                )
                decodeLoading = false
                phase = "entry"
            },
            onSkip = {
                decodeLoading = false
                phase = "entry"
            },
        )
        else -> Column(
            Modifier.fillMaxSize().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Parts QR / barcode", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Stub flow: capture a part label code from camera scan or manual entry. " +
                    "The value is logged to session events and routed to a placeholder lookup URL.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (scannedText.isBlank()) {
                if (decodeLoading) {
                    LoadingState(
                        message = "Processing label scan",
                        animatedDots = true,
                        showLinearProgress = true,
                    )
                } else {
                    Text(
                        "No code decoded yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                Text(
                    "Decoded: $scannedText",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            OutlinedTextField(
                value = scannedText,
                onValueChange = { scannedText = it.trim() },
                label = { Text("Scanned code") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Button(
                onClick = { phase = "camera" },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Scan with camera")
            }
            Button(
                onClick = {
                    val code = scannedText.ifBlank { "none" }
                    val lookupUrl = if (code == "none") {
                        "https://parts.example/lookup"
                    } else {
                        "https://parts.example/lookup?code=${
                            URLEncoder.encode(code, StandardCharsets.UTF_8.toString())
                        }"
                    }
                    SessionEventLogger.log(
                        context = context,
                        sessionId = sessionId,
                        kind = "wizard_parts_scan",
                        detail = code,
                        extra = mapOf("lookup_url" to lookupUrl),
                    )
                    onDone()
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Log scan and continue")
            }
            OutlinedButton(
                onClick = {
                    SessionEventLogger.log(
                        context = context,
                        sessionId = sessionId,
                        kind = "wizard_parts_scan",
                        detail = "skipped",
                        extra = mapOf("lookup_url" to "https://parts.example/lookup"),
                    )
                    onDone()
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Skip parts scan")
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "Lookup opens later in tooling integration lane.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun decodeBarcodeFromImage(photoPath: String?): String? {
    return runCatching {
        if (photoPath.isNullOrBlank()) return@runCatching null
        val file = File(photoPath)
        if (!file.isFile) return@runCatching null
        val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return@runCatching null
        val width = bitmap.width
        val height = bitmap.height
        if (width <= 0 || height <= 0) {
            bitmap.recycle()
            return@runCatching null
        }
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        bitmap.recycle()

        val source = RGBLuminanceSource(width, height, pixels)
        val binary = BinaryBitmap(HybridBinarizer(source))
        val hints = mapOf(
            DecodeHintType.TRY_HARDER to true,
        )
        try {
            MultiFormatReader().decode(binary, hints).text
        } catch (_: NotFoundException) {
            null
        }
    }.getOrElse {
        Log.w(TAG, "Barcode decode failed", it)
        null
    }
}
