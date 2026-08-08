package com.caseforge.scanner.vin

/**
 * Heuristic Honda-family identification (Honda default, Acura alias).
 */
object HondaVinDetector {

    const val HONDA_WEDGE_HINT = "Honda wedge candidate"

    private val HONDA_FAMILY_WMI: Set<String> = setOf(
        "1HG", "1HM", "19U", "19X", "2HG", "2HK", "5FN", "5J6", "5J8", // Honda/Acura NA
        "JH4", "JH8", "JHM", // Japan Honda/Acura
    )

    fun isLikelyHondaVin(vin: String): Boolean {
        val v = normalizeWmiPrefix(vin)
        if (v.length < 3) return false
        return v.substring(0, 3) in HONDA_FAMILY_WMI
    }

    fun marqueHint(vin: String): String? =
        if (isLikelyHondaVin(vin)) HONDA_WEDGE_HINT else null

    private fun normalizeWmiPrefix(raw: String): String {
        if (raw.isBlank()) return ""
        val sb = StringBuilder()
        for (ch in raw.uppercase()) {
            if (ch.isLetterOrDigit()) sb.append(ch)
        }
        return sb.toString()
    }
}
