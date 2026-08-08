package com.caseforge.scanner.data.session

import android.content.Context
import com.caseforge.scanner.agent.session.BackgroundObdSnapshot
import com.caseforge.scanner.agent.session.DiagnosticPhotoInsights
import com.caseforge.scanner.agent.session.DiagnosticPhotoInsightsCodec
import com.caseforge.scanner.agent.session.SessionTokenAccounting
import com.caseforge.scanner.data.AppDatabase
import java.io.File
import java.util.UUID
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * File-backed session storage + Room rollup keyed by VIN.
 */
class CustomerSessionRepository(
    private val context: Context,
    private val dao: CustomerSessionDao = AppDatabase.get(context).customerSessionDao(),
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun newSessionId(): String = UUID.randomUUID().toString()

    fun sessionDir(sessionId: String): File =
        File(context.filesDir, "sessions/$sessionId").also { it.mkdirs() }

    suspend fun loadByVin(vin: String): CustomerSessionEntity? =
        dao.loadByVin(vin.trim().uppercase())

    suspend fun persistWizardResult(
        sessionId: String,
        vin: String?,
        engineBayPath: String?,
        doorJambPath: String?,
        dashboardPath: String?,
    ) {
        val normalizedVin = vin?.trim()?.uppercase()?.takeIf { it.length == 17 } ?: return
        val prior = dao.loadByVin(normalizedVin)
        val summary = buildMap {
            put("lastSessionId", sessionId)
            put("visitCount", (prior?.summaryJson?.let { parseVisitCount(it) } ?: 0) + 1)
            engineBayPath?.let { put("engineBayPhoto", it) }
            doorJambPath?.let { put("doorJambPhoto", it) }
            dashboardPath?.let { put("dashboardPhoto", it) }
        }
        dao.upsert(
            CustomerSessionEntity(
                vin = normalizedVin,
                lastSessionId = sessionId,
                summaryJson = json.encodeToString(summary),
                engineBayPhotoPath = engineBayPath ?: prior?.engineBayPhotoPath,
                doorJambPhotoPath = doorJambPath ?: prior?.doorJambPhotoPath,
                dashboardPhotoPath = dashboardPath ?: prior?.dashboardPhotoPath,
                lastNeedDescription = prior?.lastNeedDescription,
                lastDtcSummary = prior?.lastDtcSummary,
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun updateNeedAndDtc(
        vin: String,
        sessionId: String,
        needDescription: String?,
        dtcSummary: String?,
    ) {
        val normalizedVin = vin.trim().uppercase()
        val prior = dao.loadByVin(normalizedVin) ?: CustomerSessionEntity(
            vin = normalizedVin,
            lastSessionId = sessionId,
        )
        dao.upsert(
            prior.copy(
                lastSessionId = sessionId,
                lastNeedDescription = needDescription ?: prior.lastNeedDescription,
                lastDtcSummary = dtcSummary ?: prior.lastDtcSummary,
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun persistPhotoDiagnostics(vin: String, sessionId: String, insights: DiagnosticPhotoInsights) {
        val normalizedVin = vin.trim().uppercase()
        val prior = dao.loadByVin(normalizedVin) ?: CustomerSessionEntity(
            vin = normalizedVin,
            lastSessionId = sessionId,
        )
        dao.upsert(
            prior.copy(
                lastSessionId = sessionId,
                photoDiagnosticJson = DiagnosticPhotoInsightsCodec.encode(insights),
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    fun loadPhotoDiagnostics(entity: CustomerSessionEntity?): DiagnosticPhotoInsights? =
        DiagnosticPhotoInsightsCodec.decode(entity?.photoDiagnosticJson)

    fun loadObdSnapshot(entity: CustomerSessionEntity?): BackgroundObdSnapshot? {
        val raw = entity?.lastObdSnapshotJson ?: return null
        return runCatching {
            json.decodeFromString(BackgroundObdSnapshot.serializer(), raw)
        }.getOrNull()
    }

    suspend fun persistObdSnapshot(vin: String, sessionId: String, snapshot: BackgroundObdSnapshot) {
        val normalizedVin = vin.trim().uppercase()
        val prior = dao.loadByVin(normalizedVin) ?: CustomerSessionEntity(
            vin = normalizedVin,
            lastSessionId = sessionId,
        )
        dao.upsert(
            prior.copy(
                lastSessionId = sessionId,
                lastObdSnapshotJson = json.encodeToString(snapshot),
                lastDtcSummary = snapshot.dtcSummary ?: prior.lastDtcSummary,
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    fun photoFile(sessionId: String, name: String): File =
        File(sessionDir(sessionId), name)

    suspend fun persistAiUsage(
        vin: String,
        sessionId: String,
        totals: SessionTokenAccounting.Totals,
        endedAtMs: Long,
    ) {
        val normalizedVin = vin.trim().uppercase()
        val prior = dao.loadByVin(normalizedVin) ?: CustomerSessionEntity(
            vin = normalizedVin,
            lastSessionId = sessionId,
        )
        dao.upsert(
            prior.copy(
                lastSessionId = sessionId.ifBlank { prior.lastSessionId },
                lastAiUsageJson = SessionTokenAccounting.encodeSnapshot(totals, endedAtMs),
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    private fun parseVisitCount(summaryJson: String): Int = runCatching {
        Regex(""""visitCount"\s*:\s*(\d+)""").find(summaryJson)?.groupValues?.get(1)?.toIntOrNull() ?: 0
    }.getOrDefault(0)
}
