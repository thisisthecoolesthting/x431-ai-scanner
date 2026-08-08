package com.caseforge.scanner.evidence

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Thin wrapper around the system camera intent and CameraX (optional).
 *
 * ## Usage — from a Fragment or ComponentActivity:
 *
 *   private val photoCapture = PhotoCapture(requireContext())
 *
 *   // Register ONCE in onCreate / init block, before the fragment reaches STARTED:
 *   photoCapture.registerLauncher(this) { uri ->
 *       uri?.let { viewModel.attachPhoto(pendingEvidenceId, it) }
 *   }
 *
 *   // Launch when the user taps "Take Photo":
 *   photoCapture.launch()
 *
 * The companion factory [buildCaptureUri] is also called from OverlayService
 * when constructing a photo URI to pass to CameraCaptureActivity (which already
 * exists per AndroidManifest: .ui.camera.CameraCaptureActivity).
 */
class PhotoCapture(private val ctx: Context) {

    private var launcher: ActivityResultLauncher<Uri>? = null
    private var pendingUri: Uri? = null

    // -------------------------------------------------------------------------
    // Registration (call once in Activity/Fragment onCreate)
    // -------------------------------------------------------------------------

    /**
     * Register the take-picture launcher. Must be called before the host
     * lifecycle reaches STARTED (i.e. in onCreate or field initialisation).
     *
     * @param owner  The ActivityResultCaller (Activity or Fragment).
     * @param onResult  Called with the content URI on success, or null on cancel.
     */
    fun registerLauncher(
        owner: androidx.activity.result.ActivityResultCaller,
        onResult: (Uri?) -> Unit,
    ) {
        launcher = owner.registerForActivityResult(
            ActivityResultContracts.TakePicture()
        ) { success ->
            onResult(if (success) pendingUri else null)
        }
    }

    // -------------------------------------------------------------------------
    // Launch
    // -------------------------------------------------------------------------

    /**
     * Open the system camera. Saves the full-resolution image to a private
     * temp file and returns the URI that will be delivered to [onResult].
     *
     * Requires CAMERA permission to be granted before calling.
     */
    fun launch(): Uri {
        val uri = buildCaptureUri(ctx)
        pendingUri = uri
        launcher?.launch(uri) ?: error(
            "PhotoCapture.registerLauncher() must be called before launch()"
        )
        return uri
    }

    // -------------------------------------------------------------------------
    // Intent-based helper (Overlay / Service context, no ActivityResult API)
    // -------------------------------------------------------------------------

    /**
     * Build a camera intent that writes to a FileProvider URI.
     * Use this when launching from a Service context that cannot use
     * the ActivityResult API (e.g. OverlayService → CameraCaptureActivity).
     *
     *   val (intent, uri) = PhotoCapture.buildCameraIntent(ctx)
     *   startActivity(intent)   // CameraCaptureActivity passes uri back via LocalBroadcast
     */
    companion object {

        /**
         * Build a FileProvider URI for a new, uniquely-named JPEG.
         * The file lives in [Context.cacheDir]/evidence_photos/ and is
         * later moved to permanent storage by EvidenceCapture.attachPhoto().
         */
        fun buildCaptureUri(ctx: Context): Uri {
            val dir = File(ctx.cacheDir, "evidence_photos").also { it.mkdirs() }
            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val file = File(dir, "EVIDENCE_$stamp.jpg")
            return FileProvider.getUriForFile(
                ctx,
                "${ctx.packageName}.fileprovider",
                file,
            )
        }

        /**
         * Build a fully-configured ACTION_IMAGE_CAPTURE intent targeting
         * a FileProvider URI. Pass the returned [Uri] to CameraCaptureActivity
         * via the Intent extra [EXTRA_OUTPUT_URI] so it can relay the result.
         */
        fun buildCameraIntent(ctx: Context): Pair<Intent, Uri> {
            val uri = buildCaptureUri(ctx)
            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                putExtra(MediaStore.EXTRA_OUTPUT, uri)
                addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }
            return intent to uri
        }

        /** Intent extra key used between OverlayService and CameraCaptureActivity. */
        const val EXTRA_OUTPUT_URI = "com.caseforge.scanner.EXTRA_OUTPUT_URI"

        /** LocalBroadcast action sent by CameraCaptureActivity on success. */
        const val ACTION_PHOTO_CAPTURED = "com.caseforge.scanner.ACTION_PHOTO_CAPTURED"
    }
}
