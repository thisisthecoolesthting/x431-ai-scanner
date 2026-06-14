package com.caseforge.scanner.ai

/** Prompt copy for AI Diagnostic Mode. Iterate here without touching logic. */
object AiDiagnosticPrompts {

    val SYSTEM = """
        You are the diagnostic assistant for "Together Car Works", working live with an automotive
        technician who has a scan tool connected to a real vehicle.

        Your job: find the most likely root cause of the vehicle's problem, then report it.

        Rules:
        - Use the provided tools to inspect REAL vehicle data. Never invent codes, live values, or readings.
        - Work in a sensible order: review symptoms and any trouble codes first, then measure the live
          data that would confirm or rule out your leading hypotheses.
        - Prefer cheap, non-invasive checks first.
        - If a tool reports it is not supported, adapt — do not retry it repeatedly.
        - Ask the technician a question ONLY when their answer would change your diagnosis (e.g. "does it
          happen cold or warm?", "any recent work done?"). Keep questions short and offer answer chips.
        - Narrate concisely as you go; the technician sees your steps.
        - When you are confident, call finish_session with a clear root cause, a confidence from 0 to 1,
          a concrete recommended repair, and the evidence you relied on.
        - End ONLY by calling finish_session.
    """.trimIndent()

    /** Built once at the start of the loop and sent as the first user turn (text part). */
    fun openingContext(intake: AiDiagnosticIntake): String {
        val sb = StringBuilder()
        sb.appendLine("Vehicle diagnostic session.")
        val ex = intake.extracted
        sb.appendLine("VIN: ${ex.vin ?: intake.vinPhoto?.vin ?: "unknown"}")
        ex.mileage?.let { sb.appendLine("Mileage: $it") }
        if (ex.warningLights.isNotEmpty()) sb.appendLine("Dash warning lights on: ${ex.warningLights.joinToString(", ")}")
        if (ex.visibleEngineFindings.isNotEmpty()) {
            sb.appendLine("Visible in engine bay: ${ex.visibleEngineFindings.joinToString(", ")}")
        }
        val complaint = buildString {
            intake.symptoms?.takeIf { it.isNotBlank() }?.let { append(it) }
            if (intake.complaintChips.isNotEmpty()) {
                if (isNotEmpty()) append(". ")
                append("Reported: ${intake.complaintChips.joinToString(", ")}")
            }
        }
        sb.appendLine("Technician's complaint: ${complaint.ifBlank { "none stated" }}")
        sb.appendLine()
        sb.appendLine("Begin. Read any trouble codes first, then measure live data to confirm the cause.")
        return sb.toString()
    }

    /** Vision prompt — extract structured vehicle context from whatever photos exist. */
    val VISION_EXTRACT = """
        You are looking at photos a technician took of a vehicle. Return ONLY a compact JSON object
        with these keys (omit a key if you cannot determine it):
        {
          "mileage": <integer odometer reading from the dash, if visible>,
          "warningLights": [<names of illuminated dashboard warning lights, e.g. "Check Engine", "ABS", "Battery">],
          "visibleEngineFindings": [<notable things in the engine bay: leaks, disconnected hoses, corrosion, modifications>],
          "notes": [<any other relevant observation>]
        }
        Do not include any text outside the JSON object.
    """.trimIndent()
}
