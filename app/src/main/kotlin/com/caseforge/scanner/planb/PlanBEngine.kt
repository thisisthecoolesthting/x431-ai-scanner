package com.caseforge.scanner.planb

import android.content.Context
import com.caseforge.scanner.data.SettingsRepo
import com.caseforge.scanner.vin.DodgeVinDetector
import com.caseforge.scanner.vin.FordVinDetector
import com.caseforge.scanner.vin.GmVinDetector
import com.caseforge.scanner.vin.HondaVinDetector
import com.caseforge.scanner.vin.HyundaiVinDetector
import com.caseforge.scanner.vin.JeepVinDetector
import com.caseforge.scanner.vin.NissanVinDetector
import com.caseforge.scanner.vin.ToyotaVinDetector

/**
 * Single entry for Plan B scaffolding: feature tiers are gated independently of the native OBD wedge.
 */
class PlanBEngine(
    private val settings: SettingsRepo,
) {
    /**
     * One human-readable status line per enabled tier (empty when all flags are off).
     * When [showMarqueSuffix] is true and [vin] resolves to a marque, each line ends with ` · <label>`.
     */
    fun tierStatusLines(
        vin: String? = null,
        wedgeContext: Context? = null,
        showMarqueSuffix: Boolean = true,
        applyConnectTierSafety: Boolean = false,
    ): List<String> {
        val suffix =
            if (showMarqueSuffix) {
                planbMarqueDisplay(wedgeContext, vin)?.let { " · $it" }.orEmpty()
            } else {
                ""
            }
        fun tier1On() = settings.planbBodyRead && (!applyConnectTierSafety || settings.isPlanBTierEffective(1))
        fun tier2On() = settings.planbCoding && (!applyConnectTierSafety || settings.isPlanBTierEffective(2))
        fun tier3On() = settings.planbImmoInfo && (!applyConnectTierSafety || settings.isPlanBTierEffective(3))
        fun tier4On() = settings.planbProgramming && (!applyConnectTierSafety || settings.isPlanBTierEffective(4))

        return buildList {
            if (tier1On()) {
                add("Plan B · Body read (tier 1): on — gateway/session stub until golden CAN logs$suffix")
            }
            if (tier2On()) {
                add("Plan B · Reversible coding (tier 2): on — pending G4$suffix")
            }
            if (tier3On()) {
                add("Plan B · Immobilizer info (tier 3): on — info only, no key programming$suffix")
            }
            if (tier4On()) {
                add("Programming: partner gate (Tier 4)$suffix")
            }
        }
    }

    /**
     * Compact marque wedge line for status headers (native OBD + Plan B). Pass [matrix] from a single
     * lazy [MarqueWedgeConfig.load]; when [vin] matches a matrix row -> `Marque: Ford F-150 wedge (beta)` style.
     */
    fun marqueWedgeStatusBanner(matrix: MarqueWedgeMatrix?, vin: String?): String? {
        if (matrix == null) return null
        val trimmed = vin?.trim()?.takeIf { it.isNotEmpty() }

        fun firstCard(marque: String): MarquePlatformCard? =
            matrix.platformCards.firstOrNull { it.marque.trim().equals(marque, ignoreCase = true) }

        if (trimmed == null) {
            val marques = matrix.supportedMarques().joinToString(", ").ifBlank { "-" }
            return "Marque: wedge matrix ($marques · beta)"
        }

        MarqueWedgeConfig.findCardForVin(trimmed, matrix)?.let { card ->
            return "Marque: ${card.marque} ${card.model} wedge (beta)"
        }

        return when {
            FordVinDetector.isLikelyFordVin(trimmed) ->
                firstCard("Ford")?.let { c ->
                    "Marque: ${c.marque} ${c.model} wedge (beta)"
                } ?: "Marque: Ford wedge (beta)"
            JeepVinDetector.isLikelyJeepVin(trimmed) ->
                firstCard("Jeep")?.let { c ->
                    "Marque: ${c.marque} ${c.model} wedge (beta)"
                } ?: "Marque: Jeep wedge (beta)"
            DodgeVinDetector.isLikelyDodgeVin(trimmed) ->
                (firstCard("Ram") ?: firstCard("Dodge"))?.let { c ->
                    "Marque: ${c.marque} ${c.model} wedge (beta)"
                } ?: "Marque: Dodge wedge (beta)"
            GmVinDetector.isLikelyGmVin(trimmed) ->
                "Marque: Chevrolet/GM wedge (beta)"
            ToyotaVinDetector.isLikelyToyotaVin(trimmed) ->
                "Marque: Toyota/Lexus wedge (beta)"
            HondaVinDetector.isLikelyHondaVin(trimmed) ->
                "Marque: Honda/Acura wedge (beta)"
            NissanVinDetector.isLikelyNissanVin(trimmed) ->
                "Marque: Nissan/Infiniti wedge (beta)"
            HyundaiVinDetector.isLikelyHyundaiVin(trimmed) ->
                "Marque: Hyundai/Kia wedge (beta)"
            else -> null
        }
    }
}

/** Bundled wedge matrix wins when [findCardForVin] matches; else deterministic WMI heuristics. */
fun detectPlanbMarque(context: Context?, vin: String?): PlanbMarque? {
    val v = vin?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    if (context != null) {
        val matrix = MarqueWedgeConfig.load(context)
        val card = matrix?.let { MarqueWedgeConfig.findCardForVin(v, it) }
        planbMarqueFromCard(card)?.let { return it }
    }
    return when {
        FordVinDetector.isLikelyFordVin(v) -> PlanbMarque.FORD
        JeepVinDetector.isLikelyJeepVin(v) -> PlanbMarque.JEEP
        DodgeVinDetector.isLikelyDodgeVin(v) -> PlanbMarque.DODGE
        GmVinDetector.isLikelyGmVin(v) -> PlanbMarque.CHEVROLET
        ToyotaVinDetector.isLikelyToyotaVin(v) -> PlanbMarque.TOYOTA
        HondaVinDetector.isLikelyHondaVin(v) -> PlanbMarque.HONDA
        NissanVinDetector.isLikelyNissanVin(v) -> PlanbMarque.NISSAN
        HyundaiVinDetector.isLikelyHyundaiVin(v) -> PlanbMarque.HYUNDAI
        else -> null
    }
}

private fun planbMarqueFromCard(card: MarquePlatformCard?): PlanbMarque? =
    when (card?.marque?.trim()?.lowercase().orEmpty()) {
        "ford" -> PlanbMarque.FORD
        "dodge" -> PlanbMarque.DODGE
        "jeep" -> PlanbMarque.JEEP
        "ram" -> PlanbMarque.RAM
        "chrysler" -> PlanbMarque.CHRYSLER
        "chevrolet", "gmc", "buick", "cadillac", "gm" -> PlanbMarque.CHEVROLET
        "toyota", "lexus" -> PlanbMarque.TOYOTA
        "honda", "acura" -> PlanbMarque.HONDA
        "nissan", "infiniti" -> PlanbMarque.NISSAN
        "hyundai", "kia" -> PlanbMarque.HYUNDAI
        else -> null
    }

private fun planbMarqueDisplay(context: Context?, vin: String?): String? {
    val v = vin?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    if (context != null) {
        val matrix = MarqueWedgeConfig.load(context)
        val card = matrix?.let { MarqueWedgeConfig.findCardForVin(v, it) }
        if (card != null) return "${card.marque} · ${card.platformCode} ${card.model}"
    }
    return when {
        FordVinDetector.isLikelyFordVin(v) -> "Ford"
        JeepVinDetector.isLikelyJeepVin(v) -> "Jeep"
        DodgeVinDetector.isLikelyDodgeVin(v) -> "Dodge"
        GmVinDetector.isLikelyGmVin(v) -> "Chevrolet / GM"
        ToyotaVinDetector.isLikelyToyotaVin(v) -> "Toyota / Lexus"
        HondaVinDetector.isLikelyHondaVin(v) -> "Honda / Acura"
        NissanVinDetector.isLikelyNissanVin(v) -> "Nissan / Infiniti"
        HyundaiVinDetector.isLikelyHyundaiVin(v) -> "Hyundai / Kia"
        else -> null
    }
}
