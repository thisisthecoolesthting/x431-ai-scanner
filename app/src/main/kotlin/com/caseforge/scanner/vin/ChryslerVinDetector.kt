package com.caseforge.scanner.vin

/**
 * Heuristic Chrysler-family identification from ISO 3779 WMI (positions 1-3).
 * Used for Plan B wedge routing hints only.
 */
object ChryslerVinDetector {

    const val CHRYSLER_WEDGE_HINT = "Chrysler wedge candidate"

    private val CHRYSLER_FAMILY_WMI: Set<String> = setOf(
        "1C3", // Chrysler passenger (US)
        "2C3", // Chrysler passenger (CA)
        "3C3", // Chrysler passenger (MX)
        "1B3", // legacy Dodge/Chrysler passenger lineage
        "2B3", // legacy Dodge/Chrysler passenger lineage (CA)
    )

    fun isLikelyChryslerVin(vin: String): Boolean {
        val v = normalizeWmiPrefix(vin)
        if (v.length < 3) return false
        return v.substring(0, 3) in CHRYSLER_FAMILY_WMI
    }

    fun marqueHint(vin: String): String? =
        if (isLikelyChryslerVin(vin)) CHRYSLER_WEDGE_HINT else null

    private fun normalizeWmiPrefix(raw: String): String {
        if (raw.isBlank()) return ""
        val sb = StringBuilder()
        for (ch in raw.uppercase()) {
            if (ch.isLetterOrDigit()) sb.append(ch)
        }
        return sb.toString()
    }
}
