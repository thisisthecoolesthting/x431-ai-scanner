package com.caseforge.scanner.ui.session

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.caseforge.scanner.R
import java.io.File

private const val TAG = "SessionPhotoCapture"

/**
 * Session camera capture: launches the system camera, saves JPEG to [outputFile].
 * Cancel and permission-deny stay on this screen (retry or explicit skip) — no instant dismiss.
 */
@Composable
fun SessionPhotoCapture(
    outputFile: File,
    hint: String,
    skipLabel: String = "Skip",
    autoLaunch: Boolean = true,
    onCaptured: (String?) -> Unit,
    onSkip: () -> Unit,
) {
    val context = LocalContext.current
    var launched by remember(outputFile) { mutableStateOf(false) }
    var awaitingCamera by remember(outputFile) { mutableStateOf(false) }
    var cameraError by remember(outputFile) { mutableStateOf<String?>(null) }

    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        awaitingCamera = false
        when {
            ok && outputFile.isFile && outputFile.length() > 0L -> {
                onCaptured(outputFile.absolutePath)
            }
            ok -> {
                val message = context.getString(R.string.session_camera_launch_failed)
                cameraError = message
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            }
            else -> {
                // User backed out of the system camera — stay on this screen for retry or explicit skip.
                cameraError = context.getString(R.string.session_camera_cancelled)
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            launchSessionCamera(context, outputFile, takePicture) { message ->
                awaitingCamera = false
                cameraError = message
            }
        } else {
            awaitingCamera = false
            val message = context.getString(R.string.session_camera_permission_denied)
            cameraError = message
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }

    fun launchCamera() {
        cameraError = null
        launched = true
        outputFile.parentFile?.mkdirs()
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            awaitingCamera = true
            permissionLauncher.launch(Manifest.permission.CAMERA)
            return
        }
        awaitingCamera = true
        launchSessionCamera(context, outputFile, takePicture) { message ->
            awaitingCamera = false
            cameraError = message
        }
    }

    LaunchedEffect(outputFile, autoLaunch) {
        if (autoLaunch && !launched) {
            launchCamera()
        }
    }

    val primaryCameraLabel = when {
        cameraError != null -> "Retry camera"
        launched -> "Retake"
        else -> "Open camera"
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (cameraError != null) {
                Text(
                    cameraError!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))
            } else if (awaitingCamera) {
                CircularProgressIndicator()
                Spacer(Modifier.height(16.dp))
            }
            Text(hint, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onSkip) { Text(skipLabel) }
                Button(onClick = { launchCamera() }) {
                    Text(primaryCameraLabel)
                }
            }
        }
        LowLuxAutoBrightnessBanner(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .background(Color(0xCC1A1A1A))
                .padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}

private fun launchSessionCamera(
    context: android.content.Context,
    outputFile: File,
    takePicture: androidx.activity.result.ActivityResultLauncher<Uri>,
    onError: (String) -> Unit,
) {
    try {
        outputFile.parentFile?.mkdirs()
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            outputFile,
        )
        takePicture.launch(uri)
    } catch (t: Throwable) {
        Log.e(TAG, "Camera launch failed for ${outputFile.absolutePath}", t)
        val message = context.getString(R.string.session_camera_launch_failed)
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        onError(message)
    }
}

@Composable
private fun LowLuxAutoBrightnessBanner(modifier: Modifier = Modifier) {
    Text(
        text = "Low-light tip: increase shop lighting. Auto-brightness can dim captures.",
        style = MaterialTheme.typography.labelMedium,
        color = Color.White,
        modifier = modifier,
    )
}
