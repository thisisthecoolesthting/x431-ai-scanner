package com.caseforge.scanner.vin

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File

/**
 * Camera capture scaffold for VIN scanning. Uses [ActivityResultContracts.TakePicture]
 * (no new dependencies) and ML Kit text recognition already on the classpath.
 *
 * UI wiring is owned by a separate lane; use [createIntent] / [parseResult] from callers.
 */
class VinCameraScanActivity : ComponentActivity() {

    private var photoUri: Uri? = null
    private val textRecognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) launchCamera() else finishCanceled()
    }

    private val takePicture = registerForActivityResult(
        ActivityResultContracts.TakePicture(),
    ) { success ->
        if (!success) {
            finishCanceled()
            return@registerForActivityResult
        }
        val uri = photoUri
        if (uri == null) {
            finishCanceled()
            return@registerForActivityResult
        }
        runOcrOnImage(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            launchCamera()
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun launchCamera() {
        val photoFile = File(cacheDir, "vin_scan_${System.currentTimeMillis()}.jpg").apply {
            parentFile?.mkdirs()
        }
        val uri = FileProvider.getUriForFile(
            this,
            "${packageName}.fileprovider",
            photoFile,
        )
        photoUri = uri
        takePicture.launch(uri)
    }

    private fun runOcrOnImage(uri: Uri) {
        val image = try {
            InputImage.fromFilePath(this, uri)
        } catch (t: Throwable) {
            Log.e(TAG, "InputImage failed", t)
            finishCanceled()
            return
        }
        textRecognizer.process(image)
            .addOnSuccessListener { visionText ->
                val raw = visionText.text.orEmpty()
                val candidates = VinNormalizer.extractCandidates(raw)
                val best = VinNormalizer.pickBest(candidates)
                if (best != null && VinNormalizer.isValidVin(best.normalizedVin)) {
                    finishWithVin(best.normalizedVin, raw, candidates.size)
                } else if (best != null && VinNormalizer.hasValidCharset(best.normalizedVin)) {
                    finishWithVin(best.normalizedVin, raw, candidates.size)
                } else {
                    Toast.makeText(this, R.string.vin_scan_no_vin_found, Toast.LENGTH_LONG).show()
                    finishCanceled()
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "OCR failed", e)
                Toast.makeText(this, R.string.vin_scan_ocr_failed, Toast.LENGTH_LONG).show()
                finishCanceled()
            }
    }

    private fun finishWithVin(vin: String, rawOcr: String, candidateCount: Int) {
        setResult(
            Activity.RESULT_OK,
            Intent().apply {
                putExtra(EXTRA_VIN, vin)
                putExtra(EXTRA_RAW_OCR, rawOcr)
                putExtra(EXTRA_CANDIDATE_COUNT, candidateCount)
            },
        )
        finish()
    }

    private fun finishCanceled() {
        setResult(Activity.RESULT_CANCELED)
        finish()
    }

    override fun onDestroy() {
        textRecognizer.close()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "VinCameraScan"

        const val EXTRA_VIN = "vin"
        const val EXTRA_RAW_OCR = "raw_ocr"
        const val EXTRA_CANDIDATE_COUNT = "candidate_count"

        fun createIntent(context: Context): Intent =
            Intent(context, VinCameraScanActivity::class.java)

        fun parseResult(data: Intent?): VinScanResult? {
            if (data == null) return null
            val vin = data.getStringExtra(EXTRA_VIN) ?: return null
            return VinScanResult(
                vin = vin,
                rawOcr = data.getStringExtra(EXTRA_RAW_OCR).orEmpty(),
                candidateCount = data.getIntExtra(EXTRA_CANDIDATE_COUNT, 0),
            )
        }
    }
}

data class VinScanResult(
    val vin: String,
    val rawOcr: String,
    val candidateCount: Int,
)
