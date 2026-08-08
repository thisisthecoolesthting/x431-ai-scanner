package com.caseforge.scanner.agent

import android.content.Context
import android.content.Intent
import com.caseforge.scanner.ui.camera.CameraCaptureActivity
import kotlinx.coroutines.CompletableDeferred

/**
 * Bridges the agent's `look_at` tool call to a foreground camera capture.
 *
 * Flow:
 *   1. Agent calls the tool → [capturePhoto] is invoked.
 *   2. We park a process-wide [CompletableDeferred] in [pending] and launch
 *      [CameraCaptureActivity] as a NEW_TASK from app context.
 *   3. The technician aims the tablet at the engine bay and taps the shutter
 *      (or cancels). The activity calls [deliver] with the base64 JPEG (or null).
 *   4. [capturePhoto] suspends until [deliver] completes the deferred, then returns.
 *
 * Only one capture can be in flight at a time; a second concurrent call will
 * see [pending] already set and immediately return null rather than racing.
 */
object CameraTool {

    /** Set while a CameraCaptureActivity is running. Process-wide handoff. */
    @Volatile
    private var pending: CompletableDeferred<String?>? = null

    /**
     * Open the camera UI and wait for the tech to capture (or cancel).
     * @return base64-encoded JPEG (quality 80) of the captured frame, or null
     *         if the tech cancelled or another capture was already in flight.
     */
    suspend fun capturePhoto(context: Context): String? {
        // Refuse to overlap captures — the activity is a singleton-ish flow.
        val existing = pending
        if (existing != null && !existing.isCompleted) {
            return null
        }
        val deferred = CompletableDeferred<String?>()
        pending = deferred
        try {
            val intent = Intent(context, CameraCaptureActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            return deferred.await()
        } finally {
            // Don't leave a stale deferred dangling if anything goes sideways.
            if (pending === deferred) pending = null
        }
    }

    /** Called by CameraCaptureActivity when it finishes (success or cancel). */
    internal fun deliver(base64Jpeg: String?) {
        val d = pending
        pending = null
        d?.complete(base64Jpeg)
    }
}
