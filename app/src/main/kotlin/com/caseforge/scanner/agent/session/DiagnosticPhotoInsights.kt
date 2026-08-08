package com.caseforge.scanner.agent.session

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Structured output from Claude vision on wizard / session photos. */
@Serializable
data class DiagnosticPhotoInsights(
    val analyzedAtMs: Long = System.currentTimeMillis(),
    val model: String = "",
    val findings: List<PhotoFinding> = emptyList(),
    val confidence: String = "medium",
    val suggestedNextSteps: List<String> = emptyList(),
    val perPhoto: List<PhotoRoleInsight> = emptyList(),
    val disclaimer: String = SessionDiagnosticVision.VISUAL_DISCLAIMER,
)

@Serializable
data class PhotoFinding(
    val area: String,
    val observation: String,
    val severity: String? = null,
)

@Serializable
data class PhotoRoleInsight(
    val role: String,
    val bullets: List<String> = emptyList(),
)

object DiagnosticPhotoInsightsCodec {
    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(insights: DiagnosticPhotoInsights): String =
        json.encodeToString(DiagnosticPhotoInsights.serializer(), insights)

    fun decode(raw: String?): DiagnosticPhotoInsights? {
        if (raw.isNullOrBlank()) return null
        return runCatching {
            json.decodeFromString(DiagnosticPhotoInsights.serializer(), raw)
        }.getOrNull()
    }

    fun summaryBullets(insights: DiagnosticPhotoInsights): List<String> {
        val fromPerPhoto = insights.perPhoto.flatMap { it.bullets }
        if (fromPerPhoto.isNotEmpty()) return fromPerPhoto.take(8)
        return insights.findings.map { f ->
            val sev = f.severity?.let { " ($it)" }.orEmpty()
            "${f.area}: ${f.observation}$sev"
        }.take(8)
    }
}
