package com.caseforge.scanner.ai

import android.util.Log
import com.caseforge.scanner.engine.Dtc
import com.caseforge.scanner.engine.Severity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.float
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

// ---------------------------------------------------------------------------
// F2 — Cross-Module Fault Correlation: Claude client
// ---------------------------------------------------------------------------

/**
 * Calls the Anthropic Messages API to correlate DTCs from every scanned
 * module into a [CorrelationReport] of ranked root-cause groups.
 *
 * Wire-up:
 * ```kotlin
 * val correlator = DtcCorrelator(apiKey = BuildConfig.ANTHROPIC_API_KEY)
 * val report = correlator.correlate(dtcsByModule, vinDecode)
 * ```
 *
 * The prompt instructs Claude to:
 * - Identify CAN/network faults that mask actual sensor failures.
 * - Assign ONE most-likely root cause per group.
 * - List the supporting DTCs from any module.
 * - Suggest the next diagnostic action.
 *
 * Claude is asked to return structured JSON so parsing is deterministic.
 *
 * @param apiKey   Anthropic API key. Inject from `BuildConfig.ANTHROPIC_API_KEY`
 *                 or a secrets manager — never hard-code.
 * @param model    Claude model ID. Defaults to claude-3-5-sonnet-20241022.
 * @param http     OkHttpClient instance (share the app singleton).
 */
class DtcCorrelator(
    private val apiKey: String,
    private val model: String = "claude-3-5-sonnet-20241022",
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build(),
) {

    // ------------------------------------------------------------------
    // Public API
    // ------------------------------------------------------------------

    /**
     * Groups [dtcsByModule] into correlated root-cause clusters using Claude.
     *
     * @param dtcsByModule  Map of module-name → DTCs for that module, as
     *                      produced by [EngineDriver.fullScan].
     * @param vin           Optional decoded VIN metadata (year, make, model,
     *                      engine). Included in the prompt when available.
     * @return              A [CorrelationReport] with groups sorted by
     *                      descending confidence. Never throws — failures
     *                      are surfaced as a report with an error group.
     */
    suspend fun correlate(
        dtcsByModule: Map<String, List<Dtc>>,
        vin: VinDecode?,
    ): CorrelationReport = withContext(Dispatchers.IO) {
        if (dtcsByModule.isEmpty() || dtcsByModule.values.all { it.isEmpty() }) {
            return@withContext emptyReport()
        }

        val prompt = buildPrompt(dtcsByModule, vin)
        val responseJson = callClaude(prompt)
        parseGroups(responseJson, dtcsByModule)
    }

    // ------------------------------------------------------------------
    // Prompt construction
    // ------------------------------------------------------------------

    private fun buildPrompt(
        dtcsByModule: Map<String, List<Dtc>>,
        vin: VinDecode?,
    ): String = buildString {
        appendLine("You are an expert automotive diagnostic AI assistant integrated into a professional scan tool.")
        appendLine()

        // VIN context
        if (vin != null) {
            appendLine("## Vehicle")
            appendLine("VIN: ${vin.raw}")
            vin.year?.let { appendLine("Year: $it") }
            vin.make?.let { appendLine("Make: $it") }
            vin.model?.let { appendLine("Model: $it") }
            vin.engine?.let { appendLine("Engine: $it") }
            appendLine()
        }

        // DTC input
        appendLine("## Diagnostic Trouble Codes by Module")
        dtcsByModule.forEach { (module, dtcs) ->
            appendLine("### $module")
            dtcs.forEach { dtc ->
                val sev = when (dtc.severity) {
                    Severity.Red   -> "RED"
                    Severity.Amber -> "AMBER"
                    Severity.Gray  -> "GRAY"
                }
                appendLine("  - ${dtc.code} [$sev]: ${dtc.description}")
            }
        }
        appendLine()

        // Task + chain-of-thought request
        appendLine("## Task")
        appendLine(
            """
            Analyze ALL DTCs listed above across ALL modules.

            CRITICAL RULE: Identify network/CAN-bus faults that mask actual sensor failures.
            A U-code (network/communication fault) in one module often causes ghost P-codes or B-codes
            in other modules because the downstream module never received valid data. Always check
            whether a communication fault is the TRUE root cause before attributing the fault to the
            sensor itself.

            For each correlated group, you MUST:
            1. Name ONE most-likely root cause (the real failure, not the cascade symptom).
            2. List all supporting DTCs from any module that belong to this group.
            3. State a single next diagnostic action for the technician.
            4. Rate your confidence in [0.0, 1.0].
            5. Optionally name a capability_hint — one of: read_dtcs, live_data, actuation, full_scan,
               clear_dtcs — if a specific scan-tool function would help confirm the diagnosis.
            """.trimIndent()
        )
        appendLine()

        // Few-shot examples
        appendLine("## Few-Shot Examples")
        appendLine(
            """
            Example 1 — CAN dropout masking thermostat fault:
              Input DTCs: U0100 (ECM/PCM lost communication), P0128 (coolant below thermostat
                          regulating temperature) from Engine module.
              Root cause: Faulty engine coolant thermostat.
              Reasoning: P0128 is the primary fault (thermostat stuck open → coolant never reaches
                         setpoint). U0100 is a downstream cascade — the ECM intermittently dropped
                         off CAN because it was looping on the coolant fault. Replacing the thermostat
                         resolves both codes.
              Recommended action: Measure coolant temp with live data at normal operating temperature;
                                  replace thermostat if temp stays below 82°C.

            Example 2 — Battery/charging fault spawning ghost body codes:
              Input DTCs: P0562 (system voltage low) from Engine; B1000, B1001, B1005 from BCM.
              Root cause: Weak battery or failing alternator causing low system voltage.
              Reasoning: Low voltage trips the BCM internal watchdog, generating multiple B-codes that
                         have no independent cause. Clear all codes after charging/replacing the battery
                         — BCM codes will not return if voltage is healthy.
              Recommended action: Load-test battery; measure alternator output voltage at 2000 rpm;
                                  replace weak component before clearing codes.

            Example 3 — Single sensor failure across two modules:
              Input DTCs: P0102 (MAF sensor low) from Engine; P0299 (turbo underboost) from Engine;
                          U0401 (invalid data from ECM) from TCM.
              Root cause: Failed MAF sensor sending low-voltage signal.
              Reasoning: MAF signal too low → ECM reports boost fault (underboost since fuelling is
                         lean). TCM receives invalid torque-request data from ECM → logs U0401.
                         Replace MAF; transmission code clears on its own.
              Recommended action: Inspect MAF sensor wiring for chafing; measure MAF voltage at idle
                                  (should be ~0.9V); replace MAF sensor.
            """.trimIndent()
        )
        appendLine()

        // Output format
        appendLine("## Output Format")
        appendLine(
            """
            Respond with ONLY a JSON object — no markdown, no prose before or after.
            Schema:
            {
              "groups": [
                {
                  "rootCause": "<one sentence>",
                  "supportingDtcCodes": ["<CODE1>", "<CODE2>"],
                  "confidence": <float 0.0-1.0>,
                  "recommendedAction": "<one sentence technician instruction>",
                  "capabilityHint": "<capability_id or null>"
                }
              ]
            }
            Groups must be sorted by confidence descending.
            Every DTC code in supportingDtcCodes must appear verbatim in the input above.
            """.trimIndent()
        )
    }

    // ------------------------------------------------------------------
    // HTTP call
    // ------------------------------------------------------------------

    private fun callClaude(userPrompt: String): String {
        val body = buildJsonObject {
            put("model", model)
            put("max_tokens", 2048)
            put("messages", buildJsonArray {
                add(buildJsonObject {
                    put("role", "user")
                    put("content", userPrompt)
                })
            })
        }.toString()

        val request = Request.Builder()
            .url(MESSAGES_URL)
            .header("x-api-key", apiKey)
            .header("anthropic-version", "2023-06-01")
            .header("content-type", "application/json")
            .post(body.toRequestBody(JSON_MEDIA_TYPE))
            .build()

        http.newCall(request).execute().use { response ->
            val raw = response.body?.string()
                ?: throw IllegalStateException("Empty response body from Anthropic API")
            if (!response.isSuccessful) {
                throw IllegalStateException("Anthropic API error ${response.code}: $raw")
            }
            return raw
        }
    }

    // ------------------------------------------------------------------
    // Response parsing
    // ------------------------------------------------------------------

    /**
     * Extracts the assistant message text from the Anthropic response envelope,
     * then parses Claude's JSON output into [RootCauseGroup] instances.
     *
     * Falls back gracefully: if parsing fails for any group, that group is skipped
     * and a warning is logged rather than crashing.
     */
    private fun parseGroups(
        anthropicResponseJson: String,
        dtcsByModule: Map<String, List<Dtc>>,
    ): CorrelationReport {
        val allDtcs: Map<String, Dtc> = dtcsByModule.values
            .flatten()
            .associateBy { it.code.uppercase() }

        return try {
            val envelope = Json.parseToJsonElement(anthropicResponseJson).jsonObject
            val messageText = envelope["content"]
                ?.jsonArray
                ?.firstOrNull()
                ?.jsonObject
                ?.get("text")
                ?.jsonPrimitive
                ?.content
                ?: error("No text content in Anthropic response")

            val claudeJson = Json.parseToJsonElement(messageText.trim()).jsonObject
            val groupsArray: JsonArray = claudeJson["groups"]?.jsonArray
                ?: return emptyReport()

            val groups = groupsArray.mapNotNull { elem ->
                runCatching {
                    val g = elem.jsonObject
                    val codes = g["supportingDtcCodes"]?.jsonArray
                        ?.mapNotNull { it.jsonPrimitive.content.uppercase() }
                        ?: emptyList()
                    val dtcs = codes.mapNotNull { code -> allDtcs[code] }

                    RootCauseGroup(
                        rootCause = g["rootCause"]?.jsonPrimitive?.content ?: return@runCatching null,
                        supportingDtcs = dtcs,
                        confidence = g["confidence"]?.jsonPrimitive?.float ?: 0f,
                        recommendedAction = g["recommendedAction"]?.jsonPrimitive?.content ?: "",
                        capabilityHint = g["capabilityHint"]?.jsonPrimitive?.content
                            ?.takeIf { it != "null" },
                    )
                }.onFailure { e ->
                    Log.w(TAG, "Skipping malformed group: ${e.message}")
                }.getOrNull()
            }.sortedByDescending { it.confidence }

            CorrelationReport(groups = groups, generatedAtMs = System.currentTimeMillis())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse Claude correlation response", e)
            errorReport("Claude response could not be parsed: ${e.message}")
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private fun emptyReport(): CorrelationReport =
        CorrelationReport(groups = emptyList(), generatedAtMs = System.currentTimeMillis())

    private fun errorReport(reason: String): CorrelationReport =
        CorrelationReport(
            groups = listOf(
                RootCauseGroup(
                    rootCause = "Correlation failed: $reason",
                    supportingDtcs = emptyList(),
                    confidence = 0f,
                    recommendedAction = "Re-run correlation or review DTCs manually.",
                    capabilityHint = null,
                )
            ),
            generatedAtMs = System.currentTimeMillis(),
        )

    // ------------------------------------------------------------------
    // Constants
    // ------------------------------------------------------------------

    companion object {
        private const val TAG = "DtcCorrelator"
        private const val MESSAGES_URL = "https://api.anthropic.com/v1/messages"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}

// ---------------------------------------------------------------------------
// VinDecode — lightweight carrier for decoded VIN metadata.
//
// Populated by your VIN decoder (NHTSA, OEM lookup, or local DB).
// All fields are nullable — use whatever subset you have available.
// ---------------------------------------------------------------------------

/**
 * Decoded VIN metadata passed to [DtcCorrelator.correlate] as context for
 * Claude. All fields are optional — supply what you have.
 *
 * @param raw     Raw 17-character VIN string.
 * @param year    Model year (e.g. "2021").
 * @param make    Manufacturer name (e.g. "Ford").
 * @param model   Vehicle model (e.g. "F-150").
 * @param engine  Engine description (e.g. "3.5L EcoBoost V6").
 * @param trim    Trim level (e.g. "XLT"). Optional.
 */
data class VinDecode(
    val raw: String,
    val year: String? = null,
    val make: String? = null,
    val model: String? = null,
    val engine: String? = null,
    val trim: String? = null,
)
