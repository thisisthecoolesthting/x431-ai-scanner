package com.caseforge.scanner.vin

/**
 * Heuristic Jeep / Stellantis-Jeep identification from ISO 3779 WMI (first three VIN positions).
 * Used for Plan B gateway hints (e.g. Jeep wedge); not a substitute for NHTSA decode.
 */
object JeepVinDetector {

    const val JEEP_WEDGE_HINT = "Jeep wedge candidate"

    /**
     * Common WMIs for Jeep and FCA/Stellantis US/MX light trucks where Jeep models routinely appear.
     * Single source of truth for this lane — extend here only with documented WMIs.
     */
    private val JEEP_FAMILY_WMI: Set<String> = setOf(
        // Chrysler LLC / FCA US — Jeep (USA)
        "1J4",
        "1J8",
        // Chrysler Group LLC — Jeep, Dodge, Chrysler (USA); many Grand Cherokee / Wrangler / etc.
        "1C3",
        "1C4",
        "1C6",
        // Chrysler de México — Jeep builds (e.g. Compass)
        "3C4",
    )

    /**
     * True when the cleaned VIN has at least three alphanumeric characters and the WMI matches
     * [JEEP_FAMILY_WMI].
     */
    fun isLikelyJeepVin(vin: String): Boolean {
        val v = normalizeWmiPrefix(vin)
        if (v.length < 3) return false
        return v.substring(0, 3) in JEEP_FAMILY_WMI
    }

    /**
     * Returns [JEEP_WEDGE_HINT] when [isLikelyJeepVin] is true; otherwise null.
     */
    fun marqueHint(vin: String): String? =
        if (isLikelyJeepVin(vin)) JEEP_WEDGE_HINT else null

    private fun normalizeWmiPrefix(raw: String): String {
        if (raw.isBlank()) return ""
        val sb = StringBuilder()
        for (ch in raw.uppercase()) {
            if (ch.isLetterOrDigit()) sb.append(ch)
        }
        return sb.toString()
    }
}
