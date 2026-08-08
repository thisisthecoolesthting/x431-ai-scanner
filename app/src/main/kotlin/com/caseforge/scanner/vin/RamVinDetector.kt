package com.caseforge.scanner.vin

/**
 * Heuristic Ram-family identification from ISO 3779 WMI (positions 1-3).
 * Used for Plan B wedge routing hints only.
 */
object RamVinDetector {

    const val RAM_WEDGE_HINT = "Ram wedge candidate"

    private val RAM_FAMILY_WMI: Set<String> = setOf(
        "1C6", // Ram trucks (US)
        "3C6", // Ram trucks (MX)
        "1B7", // legacy Dodge/Ram truck lineage
        "3B7", // legacy Dodge/Ram truck lineage (MX)
    )

    fun isLikelyRamVin(vin: String): Boolean {
        val v = normalizeWmiPrefix(vin)
        if (v.length < 3) return false
        return v.substring(0, 3) in RAM_FAMILY_WMI
    }

    fun marqueHint(vin: String): String? =
        if (isLikelyRamVin(vin)) RAM_WEDGE_HINT else null

    private fun normalizeWmiPrefix(raw: String): String {
        if (raw.isBlank()) return ""
        val sb = StringBuilder()
        for (ch in raw.uppercase()) {
            if (ch.isLetterOrDigit()) sb.append(ch)
        }
        return sb.toString()
    }
}
