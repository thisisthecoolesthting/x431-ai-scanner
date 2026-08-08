package com.caseforge.scanner.vin

/**
 * Heuristic Ford light-truck / van / passenger identification from ISO 3779 WMI (positions 1–3).
 * Plan B wedge hint routing; not a substitute for NHTSA decode.
 */
object FordVinDetector {

    const val FORD_WEDGE_HINT = "Ford wedge candidate"

    /**
     * Common Ford North American WMIs — trucks / vans / core passenger assemblies.
     * Extend only with documented WMIs.
     */
    private val FORD_FAMILY_WMI: Set<String> = setOf(
        "1FA", // Ford USA — cars
        "1FB", // Ford USA — bus / stripped van base
        "1FC", // Ford USA — incomplete chassis (van/truck lineage)
        "1FD", // Ford USA — completed truck
        "1FM", // Ford USA — MPV / SUVs / vans (product line varies)
        "1FT", // Ford USA — truck
        "2FA", // Ford Canada — passenger
        "2FD", // Ford Canada — truck
        "2FM", // Ford Canada — MPV
        "2FT", // Ford Canada — truck
        "3FA", // Ford Mexico — passenger vehicle
        "3FD", // Ford Mexico — truck
    )

    fun isLikelyFordVin(vin: String): Boolean {
        val v = normalizeWmiPrefix(vin)
        if (v.length < 3) return false
        return v.substring(0, 3) in FORD_FAMILY_WMI
    }

    fun marqueHint(vin: String): String? =
        if (isLikelyFordVin(vin)) FORD_WEDGE_HINT else null

    private fun normalizeWmiPrefix(raw: String): String {
        if (raw.isBlank()) return ""
        val sb = StringBuilder()
        for (ch in raw.uppercase()) {
            if (ch.isLetterOrDigit()) sb.append(ch)
        }
        return sb.toString()
    }
}
