package com.caseforge.scanner.evidence

import android.content.Context
import android.net.Uri
import com.caseforge.scanner.db.AppDatabase
import com.caseforge.scanner.engine.EngineState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * Captures diagnostic evidence snapshots and links engine-bay photos
 * to an ongoing repair ticket.
 *
 * Construct once per OverlayService scope; inject AppDatabase via Hilt or
 * pass directly:
 *
 *   val capture = EvidenceCapture(applicationContext, AppDatabase.getInstance(applicationContext))
 *
 * All suspend functions are safe to call from a CoroutineScope(Dispatchers.Main).
 */
class EvidenceCapture(
    private val ctx: Context,
    private val db: AppDatabase,
) {

    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Snapshot the current engine state and persist it as an Evidence record.
     *
     * Captures:
     *  - active DTCs (code, description, status)
     *  - live PID map (pid name → value)
     *  - current ScreenKind (so we know what the tech was looking at)
     *
     * @param state  Latest EngineState from the EngineScraper / StateFlow.
     * @param label  Short human description shown on the PDF, e.g. "Before scan".
     * @param ticketId  The repair-ticket row this evidence belongs to.
     * @param type   BEFORE / FIX / AFTER — controls PDF section placement.
     * @return The persisted Evidence row (id can be used for attachPhoto).
     */
    fun bookmarkLiveData(
        state: EngineState,
        label: String,
        ticketId: String,
        type: EvidenceType,
    ): Evidence {
        val snapshot = buildSnapshotJson(state)
        val evidence = Evidence(
            id = UUID.randomUUID().toString(),
            ticketId = ticketId,
            type = type,
            label = label,
            snapshotJson = snapshot,
        )
        // Insert on the calling thread — callers are responsible for dispatching
        // off-main if needed, but Room allows synchronous calls on background threads.
        db.evidenceDao().insert(evidence)
        return evidence
    }

    /**
     * Copy the photo at [uri] into the app's private evidence directory,
     * then update the existing Evidence row to link the saved file URI.
     *
     * The file is renamed to `<evidenceId>.jpg` so it can be reliably
     * retrieved by RepairStoryGenerator later.
     *
     * @param evidenceId  Row id returned by bookmarkLiveData (or a standalone
     *                    Evidence row created just for a photo).
     * @param uri         content:// or file:// URI from CameraX / MediaStore.
     */
    suspend fun attachPhoto(evidenceId: String, uri: Uri) = withContext(Dispatchers.IO) {
        val dest = photoFile(evidenceId)
        ctx.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(dest).use { output -> input.copyTo(output) }
        }
        val savedUri = Uri.fromFile(dest).toString()
        db.evidenceDao().updatePhotoUri(evidenceId, savedUri)
    }

    /**
     * Create a photo-only Evidence row (no engine-state snapshot) and persist it.
     * Useful when the tech wants to capture a photo mid-repair without a data bookmark.
     */
    suspend fun capturePhotoEvidence(
        ticketId: String,
        type: EvidenceType,
        label: String,
        photoUri: Uri,
    ): Evidence = withContext(Dispatchers.IO) {
        val evidence = Evidence(
            ticketId = ticketId,
            type = type,
            label = label,
        )
        db.evidenceDao().insert(evidence)
        attachPhoto(evidence.id, photoUri)
        // Return the updated row
        db.evidenceDao().getById(evidence.id) ?: evidence
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private fun buildSnapshotJson(state: EngineState): String {
        val snapshot = EvidenceSnapshot(
            screenKind = state.screen::class.simpleName ?: "Unknown",
            vehicleVin = state.vehicleVin,
            vehicleSummary = state.vehicleSummary,
            dtcs = state.dtcs.map { DtcSnapshot(it.code, it.description, it.status) },
            livePids = state.liveData,
            capturedAtMs = System.currentTimeMillis(),
        )
        return json.encodeToString(snapshot)
    }

    private fun photoFile(evidenceId: String): File {
        val dir = File(ctx.filesDir, "evidence_photos").also { it.mkdirs() }
        return File(dir, "$evidenceId.jpg")
    }
}

// -------------------------------------------------------------------------
// Serialisable snapshot types (not Room entities — JSON blob only)
// -------------------------------------------------------------------------

import kotlinx.serialization.Serializable

@Serializable
internal data class EvidenceSnapshot(
    val screenKind: String,
    val vehicleVin: String?,
    val vehicleSummary: String?,
    val dtcs: List<DtcSnapshot>,
    val livePids: Map<String, Double>,
    val capturedAtMs: Long,
)

@Serializable
internal data class DtcSnapshot(
    val code: String,
    val description: String?,
    val status: String?,
)
