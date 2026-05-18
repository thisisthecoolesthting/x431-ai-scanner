package com.caseforge.scanner.ui.camera

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.caseforge.scanner.agent.CameraTool
import com.caseforge.scanner.ui.theme.CaseForgeTheme
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors

/**
 * Full-screen camera capture used by the agent's `look_at` tool. Renders a
 * CameraX preview, a shutter, and a cancel button. On capture, encodes the
 * frame as JPEG (quality 80) base64 and hands it back to [CameraTool] before
 * finishing. If the user cancels (or denies permission), delivers null.
 */
class CameraCaptureActivity : ComponentActivity() {

    private val executor by lazy { Executors.newSingleThreadExecutor() }
    private var imageCapture: ImageCapture? = null
    private var delivered = false

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            cancelAndFinish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }

        setContent {
            CaseForgeTheme {
                CaptureUi(
                    onShutter = { takePhoto() },
                    onCancel = { cancelAndFinish() },
                    bindPreview = { previewView -> startCamera(previewView) },
                )
            }
        }
    }

    private fun startCamera(previewView: PreviewView) {
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            try {
                val provider = providerFuture.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                val capture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()
                imageCapture = capture
                provider.unbindAll()
                provider.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    capture,
                )
            } catch (t: Throwable) {
                Log.e(TAG, "Camera bind failed", t)
                cancelAndFinish()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun takePhoto() {
        val capture = imageCapture ?: return
        capture.takePicture(
            executor,
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    try {
                        val bitmap = image.toBitmap()
                        val base64 = encodeJpegBase64(bitmap, 80)
                        deliverAndFinish(base64)
                    } catch (t: Throwable) {
                        Log.e(TAG, "Encode failed", t)
                        deliverAndFinish(null)
                    } finally {
                        image.close()
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e(TAG, "Capture failed", exception)
                    deliverAndFinish(null)
                }
            },
        )
    }

    private fun encodeJpegBase64(bitmap: Bitmap, quality: Int): String {
        val baos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, baos)
        return Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
    }

    private fun deliverAndFinish(base64: String?) {
        if (delivered) return
        delivered = true
        CameraTool.deliver(base64)
        runOnUiThread { finish() }
    }

    private fun cancelAndFinish() {
        deliverAndFinish(null)
    }

    override fun onDestroy() {
        // If the activity is dismissed (back press, system kill) before we delivered,
        // make sure the suspended tool call doesn't hang forever.
        if (!delivered) {
            delivered = true
            CameraTool.deliver(null)
        }
        executor.shutdown()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "CameraCaptureActivity"
    }
}

@Composable
private fun CaptureUi(
    onShutter: () -> Unit,
    onCancel: () -> Unit,
    bindPreview: (PreviewView) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    LaunchedEffect(lifecycleOwner) {
        bindPreview(previewView)
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize(),
        )

        // Header hint
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .align(Alignment.TopCenter),
        ) {
            Text(
                text = "Point the tablet at the engine bay, then tap the shutter.",
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        // Bottom controls
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
                .align(Alignment.BottomCenter),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            OutlinedButton(onClick = onCancel) {
                Text("Cancel")
            }

            Button(
                onClick = onShutter,
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape),
            ) {
                Text("Snap")
            }

            Spacer(modifier = Modifier.height(1.dp).size(72.dp))
        }
    }
}
