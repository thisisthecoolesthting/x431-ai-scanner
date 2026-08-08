package com.caseforge.scanner.vin

/**
 * Heuristic Toyota-family identification (Toyota default, Lexus alias).
 */
object ToyotaVinDetector {

    const val TOYOTA_WEDGE_HINT = "Toyota wedge candidate"

    private val TOYOTA_FAMILY_WMI: Set<String> = setOf(
        "JT1", "JT2", "JT3", "JT4", "JT5", "JTA", "JTB", "JTC", "JTD", "JTE",
        "2T1", "2T2", "3TM", "4T1", "4T3", "5TD", "5TF", // Toyota/Lexus NA production
    )

    fun isLikelyToyotaVin(vin: String): Boolean {
        val v = normalizeWmiPrefix(vin)
        if (v.length < 3) return false
        return v.substring(0, 3) in TOYOTA_FAMILY_WMI
    }

    fun marqueHint(vin: String): String? =
        if (isLikelyToyotaVin(vin)) TOYOTA_WEDGE_HINT else null

    private fun normalizeWmiPrefix(raw: String): String {
        if (raw.isBlank()) return ""
        val sb = StringBuilder()
        for (ch in raw.uppercase()) {
            if (ch.isLetterOrDigit()) sb.append(ch)
        }
        return sb.toString()
    }
}
