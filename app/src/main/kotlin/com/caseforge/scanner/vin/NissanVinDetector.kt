package com.caseforge.scanner.vin

/**
 * Heuristic Nissan-family identification (Nissan default, Infiniti alias).
 */
object NissanVinDetector {

    const val NISSAN_WEDGE_HINT = "Nissan wedge candidate"

    private val NISSAN_FAMILY_WMI: Set<String> = setOf(
        "1N4", "1N6", "3N1", "3N6", "5N1", // Nissan NA
        "JN1", "JN8", // Nissan/Infiniti Japan
    )

    fun isLikelyNissanVin(vin: String): Boolean {
        val v = normalizeWmiPrefix(vin)
        if (v.length < 3) return false
        return v.substring(0, 3) in NISSAN_FAMILY_WMI
    }

    fun marqueHint(vin: String): String? =
        if (isLikelyNissanVin(vin)) NISSAN_WEDGE_HINT else null

    private fun normalizeWmiPrefix(raw: String): String {
        if (raw.isBlank()) return ""
        val sb = StringBuilder()
        for (ch in raw.uppercase()) {
            if (ch.isLetterOrDigit()) sb.append(ch)
        }
        return sb.toString()
    }
}
