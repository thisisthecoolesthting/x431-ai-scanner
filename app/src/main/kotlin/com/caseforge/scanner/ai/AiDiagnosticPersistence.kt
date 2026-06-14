package com.caseforge.scanner.ai

import com.caseforge.scanner.data.AppDatabase
import com.caseforge.scanner.data.DtcEntity
import com.caseforge.scanner.data.SessionEntity
import org.json.JSONArray
import org.json.JSONObject

/** Saves an AI Diagnostic result into the existing session history. */
object AiDiagnosticPersistence {

    /** Returns the new session id, or null on failure (non-fatal). */
    suspend fun save(
        db: AppDatabase,
        result: AiDiagnosticResult,
        thoughtFeed: List<AiThoughtEvent>,
    ): Long? = runCatching {
        val transcript = JSONObject().apply {
            put("rootCause", result.rootCause)
            put("confidence", result.confidence.toDouble())
            put("recommendedRepair", result.recommendedRepair)
            result.summary?.let { put("summary", it) }
            put("mileage", result.mileage ?: JSONObject.NULL)
            put("warningLights", JSONArray(result.warningLights))
            put("supportingEvidence", JSONArray(result.supportingEvidence))
            put(
                "thoughtFeed",
                JSONArray().apply {
                    thoughtFeed.forEach { e ->
                        put(
                            JSONObject().apply {
                                put("at", e.at)
                                put("kind", e.kind.name)
                                put("title", e.title)
                                e.detail?.let { put("detail", it) }
                            },
                        )
                    }
                },
            )
        }.toString()

        val dao = db.sessionDao()
        val sessionId = dao.insertSession(
            SessionEntity(
                vin = result.vin,
                startedAt = result.startedAt,
                endedAt = result.endedAt,
                symptom = result.symptoms,
                rootCause = result.rootCause,
                recommendedRepair = result.recommendedRepair,
                transcriptJson = transcript,
                scope = "ai_diagnostic",
            ),
        )
        result.codes.forEach { c ->
            dao.insertDtc(
                DtcEntity(
                    sessionId = sessionId,
                    code = c.code,
                    module = c.module,
                    description = c.description,
                    status = c.status,
                ),
            )
        }
        sessionId
    }.getOrNull()
}
