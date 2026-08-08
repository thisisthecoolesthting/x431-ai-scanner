package com.caseforge.scanner.vin

/**
 * Heuristic GM-family identification (Chevrolet default, plus GMC/Buick/Cadillac aliases).
 * Used for Plan B wedge routing hints only.
 */
object GmVinDetector {

    const val GM_WEDGE_HINT = "GM wedge candidate"

    private val GM_FAMILY_WMI: Set<String> = setOf(
        "1G1", "1G2", "1G3", "1G4", "1G6", "1GC", "1GD", "1GT",
        "2G1", "2G2", "2G3", "2G4", "2G6", "2GC", "2GD", "2GT",
        "3G1", "3G2", "3G3", "3G4", "3G6", "3GC", "3GD", "3GT",
        "KL1", "KL4", // Daewoo/Korea GM lines often sold under Chevy/Buick brands
    )

    fun isLikelyGmVin(vin: String): Boolean {
        val v = normalizeWmiPrefix(vin)
        if (v.length < 3) return false
        return v.substring(0, 3) in GM_FAMILY_WMI
    }

    fun marqueHint(vin: String): String? =
        if (isLikelyGmVin(vin)) GM_WEDGE_HINT else null

    private fun normalizeWmiPrefix(raw: String): String {
        if (raw.isBlank()) return ""
        val sb = StringBuilder()
        for (ch in raw.uppercase()) {
            if (ch.isLetterOrDigit()) sb.append(ch)
        }
        return sb.toString()
    }
}
