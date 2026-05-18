package com.caseforge.scanner.agent

import com.caseforge.scanner.ai.ClaudeClient
import com.caseforge.scanner.engine.EngineState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.float
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * One-shot Claude call that suggests the highest-probability next diagnostic step
 * from the current [EngineState] (DTCs, live data snapshot, vehicle context).
 *
 * Uses the same [ClaudeClient] stack as [AgentRunner] without entering the tool-use
 * loop, so the overlay can suggest a test without driving X431 autonomously.
 */
class NextTestSuggester(private val claude: ClaudeClient) {

    private val json = Json { ignoreUnknownKeys = true }

    private val systemPrompt = """
        You are an expert automotive diagnostician on a professional scan tool.
        Given DTCs and optional live-data values from a completed scan, respond with
        exactly one JSON object (no markdown fences) with this schema:
        {
          "testName": "<short name of the next test>",
          "probability": <float 0.0-1.0>,
          "rationale": "<one or two sentences for the technician>",
          "capabilityId": "<one of: live_data, read_dtcs, actuation, full_scan, clear_dtcs, or null>"
        }
        Prefer live_data when confirming sensor values; actuation when a bidirectional
        test is the logical next step. Be specific (name PIDs or tests).
    """.trimIndent()

    suspend fun suggest(state: EngineState): NextTestSuggestion? = withContext(Dispatchers.IO) {
        if (state.dtcs.isEmpty()) return@withContext null

        val userPrompt = buildString {
            state.vehicleVin?.let { appendLine("VIN: $it") }
            state.vehicleSummary?.let { appendLine("Vehicle: $it") }
            appendLine("DTCs:")
            state.dtcs.forEach { dtc ->
                val mod = dtc.module?.let { " [$it]" }.orEmpty()
                appendLine("  - ${dtc.code}$mod: ${dtc.description ?: "no description"}")
            }
            if (state.liveData.isNotEmpty()) {
                appendLine("Freeze-frame / live data snapshot:")
                state.liveData.forEach { (k, v) -> appendLine("  - $k: $v") }
            }
            appendLine()
            append("Produce the JSON suggestion now.")
        }

        runCatching {
            val resp = claude.sendMessages(
                system = systemPrompt,
                messages = listOf(ClaudeClient.userText(userPrompt)),
                tools = emptyList(),
                maxTokens = 512,
                temperature = 0.15,
            )
            val text = resp.firstText()?.trim().orEmpty()
            if (text.isBlank()) return@runCatching null
            parseSuggestion(text)
        }.getOrNull()
    }

    private fun parseSuggestion(text: String): NextTestSuggestion? {
        val trimmed = text.trim()
        val jsonStart = trimmed.indexOf('{')
        val jsonEnd = trimmed.lastIndexOf('}')
        if (jsonStart < 0 || jsonEnd <= jsonStart) return null
        val obj = json.parseToJsonElement(trimmed.substring(jsonStart, jsonEnd + 1)).jsonObject
        val testName = obj["testName"]?.jsonPrimitive?.content?.trim().orEmpty()
        if (testName.isBlank()) return null
        val probability = obj["probability"]?.jsonPrimitive?.float?.coerceIn(0f, 1f) ?: 0.5f
        val rationale = obj["rationale"]?.jsonPrimitive?.content?.trim().orEmpty()
        val capabilityId = obj["capabilityId"]?.jsonPrimitive?.content
            ?.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
        return NextTestSuggestion(
            testName = testName,
            probability = probability,
            rationale = rationale.ifBlank { "Run this test to narrow the diagnosis." },
            capabilityId = capabilityId,
        )
    }
}

@Serializable
data class NextTestSuggestion(
    val testName: String,
    val probability: Float,
    val rationale: String,
    val capabilityId: String? = null,
)
