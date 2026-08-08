package com.caseforge.scanner.evidence

import android.content.Context
import android.net.Uri
import com.caseforge.scanner.data.AppDatabase
import com.caseforge.scanner.engine.EngineState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream

class EvidenceCapture(
    private val ctx: Context,
    private val db: AppDatabase,
) {
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    fun bookmark(
        state: EngineState,
        ticketId: String,
        type: EvidenceType,
        label: String,
    ): Evidence {
        val snapshot = json.encodeToString(
            EvidenceSnapshot(
                screenKind = state.screen::class.simpleName ?: "Unknown",
                vehicleVin = state.vehicleVin,
                dtcs = state.dtcs.map { it.code },
                livePids = state.liveData,
                capturedAtMs = System.currentTimeMillis(),
            ),
        )
        val row = Evidence(
            ticketId = ticketId,
            type = type,
            label = label,
            snapshotJson = snapshot,
        )
        db.evidenceDao().insert(row)
        return row
    }

    suspend fun attachPhoto(evidenceId: String, uri: Uri) = withContext(Dispatchers.IO) {
        val dest = File(ctx.filesDir, "evidence_photos").also { it.mkdirs() }
            .resolve("$evidenceId.jpg")
        ctx.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(dest).use { output -> input.copyTo(output) }
        }
        db.evidenceDao().updatePhotoUri(evidenceId, Uri.fromFile(dest).toString())
    }

    @Serializable
    private data class EvidenceSnapshot(
        val screenKind: String,
        val vehicleVin: String?,
        val dtcs: List<String>,
        val livePids: Map<String, Double>,
        val capturedAtMs: Long,
    )
}
