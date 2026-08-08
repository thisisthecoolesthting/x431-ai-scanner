package com.caseforge.scanner.agent

import com.caseforge.scanner.ai.ClaudeClient
import com.caseforge.scanner.engine.EngineState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class NextTestSuggester(private val claude: ClaudeClient) {
    private val json = Json { ignoreUnknownKeys = true }

    private val systemPrompt = """
        You are an expert automotive diagnostician on a professional scan tool.
        Given DTCs and optional live-data values from a completed scan, respond with
        exactly one JSON object and no markdown fences using this schema:
        {
          "testName": "<short name of the next test>",
          "probability": <float 0.0-1.0>,
          "rationale": "<one or two sentences for the technician>",
          "capabilityId": "<one of: live_data, read_dtcs, actuation, full_scan, clear_dtcs, freeze_frame, or null>"
        }
        Prefer live_data when confirming sensor values and actuation when a bidirectional
        test is the logical next step. Be specific about PIDs, modules, or tests.
    """.trimIndent()

    suspend fun suggest(state: EngineState): NextTestSuggestion? = withContext(Dispatchers.IO) {
        if (state.dtcs.isEmpty()) return@withContext null

        val userPrompt = buildString {
            state.vehicleVin?.let { appendLine("VIN: $it") }
            state.vehicleSummary?.let { appendLine("Vehicle: $it") }
            appendLine("DTCs:")
            state.dtcs.forEach { dtc ->
                val module = dtc.module?.let { " [$it]" }.orEmpty()
                appendLine("  - ${dtc.code}$module: ${dtc.description ?: "no description"}")
            }
            if (state.liveData.isNotEmpty()) {
                appendLine("Freeze-frame / live data snapshot:")
                state.liveData.forEach { (name, value) -> appendLine("  - $name: $value") }
            }
            appendLine()
            append("Produce the JSON suggestion now.")
        }

        runCatching {
            val response = claude.sendMessages(
                system = systemPrompt,
                messages = listOf(ClaudeClient.userText(userPrompt)),
                tools = emptyList(),
                maxTokens = 512,
                temperature = 0.15,
            )
            parseSuggestion(response.firstText()?.trim().orEmpty())
        }.getOrNull()
    }

    private fun parseSuggestion(text: String): NextTestSuggestion? {
        val jsonStart = text.indexOf('{')
        val jsonEnd = text.lastIndexOf('}')
        if (jsonStart < 0 || jsonEnd <= jsonStart) return null

        val obj = json.parseToJsonElement(text.substring(jsonStart, jsonEnd + 1)).jsonObject
        val testName = obj["testName"]?.jsonPrimitive?.content?.trim().orEmpty()
        if (testName.isBlank()) return null

        return NextTestSuggestion(
            testName = testName,
            probability = obj["probability"]?.jsonPrimitive?.content?.toFloatOrNull()?.coerceIn(0f, 1f) ?: 0.5f,
            rationale = obj["rationale"]?.jsonPrimitive?.content?.trim()
                ?.ifBlank { null }
                ?: "Run this test to narrow the diagnosis.",
            capabilityId = obj["capabilityId"]?.jsonPrimitive?.content?.trim()
                ?.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) },
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
