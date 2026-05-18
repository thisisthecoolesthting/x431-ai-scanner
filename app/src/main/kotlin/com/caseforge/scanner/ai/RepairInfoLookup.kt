package com.caseforge.scanner.ai

/**
 * Performs a focused, one-shot Claude call to look up repair information for a DTC + vehicle.
 *
 * IMPORTANT: This intentionally uses a SEPARATE Claude conversation (a fresh single-turn
 * sendMessages call with its own system prompt) so the results don't bloat the main agent
 * loop's context window. The agent only sees the short structured string this returns.
 */
class RepairInfoLookup(private val claude: ClaudeClient) {

    private val systemPrompt = """
        You are an expert automotive diagnostician. Given a DTC code and a vehicle, output a
        short structured response with the following markdown sections, in this order:

        ## Common Causes
        3–6 bullets, most likely first.

        ## Recommended Tests
        Specific things the tech can run on the scanner — name the bidirectional tests or the
        exact live-data PIDs to watch, with the value range that would indicate a fault.

        ## TSBs
        Known TSBs or recall hints for this code on this vehicle. If you don't know of any,
        write exactly: none known.

        ## Wiring Hint
        One sentence about the relevant circuit (which module, which connector/pin family,
        common failure point).

        Keep the total response under 300 words. Be concrete; do not hedge with generic advice.
    """.trimIndent()

    /**
     * Look up repair info for [dtc] on [vehicle] (e.g. "2019 Chevrolet Silverado 5.3L"),
     * optionally scoped to a particular [module] (e.g. "ECM", "BCM").
     *
     * Returns the Claude response text. Throws on network/API errors — callers should handle.
     */
    suspend fun lookup(dtc: String, vehicle: String, module: String?): String {
        val userPrompt = buildString {
            appendLine("DTC code: $dtc")
            appendLine("Vehicle: $vehicle")
            if (!module.isNullOrBlank()) appendLine("Module: $module")
            appendLine()
            append("Produce the structured repair-info response now.")
        }

        val resp = claude.sendMessages(
            system = systemPrompt,
            messages = listOf(ClaudeClient.userText(userPrompt)),
            tools = emptyList(),
            maxTokens = 800,
            temperature = 0.1,
        )

        return resp.firstText()?.trim().orEmpty().ifBlank {
            "No repair info returned for $dtc on $vehicle."
        }
    }
}
