package com.caseforge.scanner.vin

/**
 * Heuristic Hyundai-family identification (Hyundai default, Kia alias).
 */
object HyundaiVinDetector {

    const val HYUNDAI_WEDGE_HINT = "Hyundai wedge candidate"

    private val HYUNDAI_FAMILY_WMI: Set<String> = setOf(
        "KM8", // Hyundai SUV line
        "KMH", "KMF", "KMT", // Hyundai
        "KNA", "KND", "KNE", "KNM", // Kia
        "5NP", "5NM", // Hyundai US
    ).map { it.take(3) }.toSet()

    fun isLikelyHyundaiVin(vin: String): Boolean {
        val v = normalizeWmiPrefix(vin)
        if (v.length < 3) return false
        return v.substring(0, 3) in HYUNDAI_FAMILY_WMI
    }

    fun marqueHint(vin: String): String? =
        if (isLikelyHyundaiVin(vin)) HYUNDAI_WEDGE_HINT else null

    private fun normalizeWmiPrefix(raw: String): String {
        if (raw.isBlank()) return ""
        val sb = StringBuilder()
        for (ch in raw.uppercase()) {
            if (ch.isLetterOrDigit()) sb.append(ch)
        }
        return sb.toString()
    }
}
