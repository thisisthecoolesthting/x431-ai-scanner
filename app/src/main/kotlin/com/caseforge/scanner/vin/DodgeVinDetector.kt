package com.caseforge.scanner.vin

/**
 * Heuristic Dodge / Ram WMI identification (positions 1–3).
 * Plan B wedge hints; overlaps with Chrysler Group production — ordered after Jeep/Ford heuristics in callers.
 */
object DodgeVinDetector {

    const val DODGE_WEDGE_HINT = "Dodge wedge candidate"

    /**
     * Dodge / Ram family WMIs (passenger / light truck lanes); Ram trucks often trace through these.
     */
    private val DODGE_FAMILY_WMI: Set<String> = setOf(
        "1B3",
        "1B4",
        "1B6",
        "1B7",
        "1C6", // Chrysler / Ram trucks (US) — overlaps Jeep Gladiator; callers order Ram vs Jeep wedge cards
        "2B3",
        "3B7",
        "3C6", // Chrysler Group / Ram — Mexico assemblies
    )

    fun isLikelyDodgeVin(vin: String): Boolean {
        val v = normalizeWmiPrefix(vin)
        if (v.length < 3) return false
        return v.substring(0, 3) in DODGE_FAMILY_WMI
    }

    fun marqueHint(vin: String): String? =
        if (isLikelyDodgeVin(vin)) DODGE_WEDGE_HINT else null

    private fun normalizeWmiPrefix(raw: String): String {
        if (raw.isBlank()) return ""
        val sb = StringBuilder()
        for (ch in raw.uppercase()) {
            if (ch.isLetterOrDigit()) sb.append(ch)
        }
        return sb.toString()
    }
}
