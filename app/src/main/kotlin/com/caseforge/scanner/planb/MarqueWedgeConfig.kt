package com.caseforge.scanner.planb

import android.content.Context
import com.caseforge.scanner.update.AssetOverlay
import com.caseforge.scanner.vin.DodgeVinDetector
import com.caseforge.scanner.vin.FordVinDetector
import com.caseforge.scanner.vin.GmVinDetector
import com.caseforge.scanner.vin.HondaVinDetector
import com.caseforge.scanner.vin.HyundaiVinDetector
import com.caseforge.scanner.vin.JeepVinDetector
import com.caseforge.scanner.vin.NissanVinDetector
import com.caseforge.scanner.vin.ToyotaVinDetector
import com.caseforge.scanner.vin.VinNormalizer
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.util.TreeSet

/**
 * Static multi-marque OBD-wedge matrix (PCM tier gating, gateway copy). Loaded from [ASSET_NAME].
 *
 * [findCardForVin] walks [MarqueWedgeMatrix.platformCards] in file order.
 * When a VIN is both Stellantis-truck and Stellantis-Jeep family (e.g. `1C6`), the Dodge/Ram card wins
 * if its model-year band matches first â€” a known ambiguity vs. Gladiator/JT; Tier-0 routing tolerates it.
 */
object MarqueWedgeConfig {

    const val ASSET_NAME = "marque-wedge-matrix.json"

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
    }

    @Volatile
    private var cachedMatrix: MarqueWedgeMatrix? = null

    fun invalidateCache() {
        cachedMatrix = null
    }

    /** Ram-line WMIs that share the broader Jeep-family WMI table; prefer the Ram card when MY fits that card. */
    private val RamTruckWmis: Set<String> = setOf("1C6", "3C6")

    private fun wmi3(vin: String): String {
        val v = VinNormalizer.normalizeOcrText(vin)
        return if (v.length >= 3) v.substring(0, 3) else ""
    }

    /**
     * North American VIN model year (position 10 / index 9). Returns null if unmapped or VIN too short.
     * Covers common 2001â€“2039 encodings used in bay diagnostics.
     */
    fun decodeVinModelYear(vin: String): Int? = vinModelYearNorthAmerica(vin)

    /** @see [vinModelYearNorthAmerica] */
    fun vinModelYearNorthAmerica(vin: String): Int? {
        val v = VinNormalizer.normalizeOcrText(vin)
        if (v.length < 10) return null
        return when (val c = v[9].uppercaseChar()) {
            in '1'..'9' -> 2000 + c.digitToInt()
            in 'A'..'H' -> 2010 + (c - 'A') // A=2010 â€¦ H=2017
            'J' -> 2018
            'K' -> 2019
            'L' -> 2020
            'M' -> 2021
            'N' -> 2022
            'P' -> 2023
            'R' -> 2024
            'S' -> 2025
            'T' -> 2026
            'V' -> 2027
            'W' -> 2028
            'X' -> 2029
            'Y' -> 2030
            else -> null
        }
    }

    fun isJeepVin(vin: String): Boolean = JeepVinDetector.isLikelyJeepVin(vin)

    fun load(context: Context): MarqueWedgeMatrix? {
        val hit = cachedMatrix
        if (hit != null) return hit
        val parsed = runCatching {
            val text = AssetOverlay.readText(context, ASSET_NAME) ?: return@runCatching null
            json.decodeFromString<MarqueWedgeMatrix>(text)
        }.getOrNull()
        cachedMatrix = parsed
        return parsed
    }

    /**
     * Resolves one platform card by [id] (`MarquePlatformCard.id`) **or** by marque slug
     * matching [MarquePlatformCard.marque] (e.g. `"jeep"` / `"Ford"`).
     */
    fun cardForMarque(matrix: MarqueWedgeMatrix, id: String): MarquePlatformCard? {
        val key = id.trim()
        val byId =
            matrix.platformCards.firstOrNull { it.id.equals(key, ignoreCase = true) }
        if (byId != null) return byId
        return matrix.platformCards.firstOrNull { it.marque.equals(key, ignoreCase = true) }
    }

    /**
     * Tier gate for UI / Plan B: [tier] must appear in effective tiers for [card]
     * (card override, else matrix). Indices are asset-driven; tier **4** denotes programming /
     * security-partner workflows (manual or authorized partner only) when listed in `supportedTiers`.
     *
     * **Requires [matrix]** so null [MarquePlatformCard.supportedTiers] inherits [MarqueWedgeMatrix.supportedTiers].
     */
    fun tierEnabled(card: MarquePlatformCard, tier: Int, matrix: MarqueWedgeMatrix): Boolean =
        tier in card.effectiveTiers(matrix)

    /**
     * One compact rollup line per marque card (`marque[T0,1,...;mode]`) suitable for technician logs.
     */
    fun fullTierSummary(matrix: MarqueWedgeMatrix): String =
        matrix.platformCards.joinToString(" Â· ") { card ->
            val tiers = card.effectiveTiers(matrix).sorted().joinToString(",")
            val mode =
                if (card.effectiveObdOnly(matrix)) "OBD-default" else "full-diag-default"
            "${card.marque.lowercase()}[T$tiers;$mode]"
        }

    /** Convenience: load bundled matrix once, then [findCardForVin] with WMI + model year. */
    fun findCardForVin(context: Context, vin: String): MarquePlatformCard? {
        val matrix = load(context) ?: return null
        return findCardForVin(vin, matrix)
    }

    /**
     * First matching platform card for [vin] within [matrix], using detector + model-year band per card.
     * Order follows [MarqueWedgeMatrix.platformCards] in the bundled asset.
     */
    fun findCardForVin(vin: String, matrix: MarqueWedgeMatrix): MarquePlatformCard? {
        val cleaned = VinNormalizer.normalizeOcrText(vin)
        val my = vinModelYearNorthAmerica(cleaned) ?: return null
        val dodgeRange = matrix.dodgeCardYearRange()
        for (card in matrix.platformCards) {
            when (card.marque.trim().lowercase()) {
                "ford" -> {
                    if (FordVinDetector.isLikelyFordVin(cleaned) && my in card.modelYearRange()) return card
                }
                "dodge", "ram" -> {
                    val truckWmi = wmi3(cleaned) in RamTruckWmis
                    val dodgeHit = DodgeVinDetector.isLikelyDodgeVin(cleaned) || truckWmi
                    if (dodgeHit && my in card.modelYearRange()) return card
                }
                "jeep" -> {
                    if (!JeepVinDetector.isLikelyJeepVin(cleaned) || my !in card.modelYearRange()) continue
                    if (dodgeRange != null && my in dodgeRange) {
                        val swallowedByRamLine =
                            DodgeVinDetector.isLikelyDodgeVin(cleaned) || wmi3(cleaned) in RamTruckWmis
                        if (swallowedByRamLine) continue
                    }
                    return card
                }
                "chevrolet", "gmc", "buick", "cadillac", "gm" -> {
                    if (GmVinDetector.isLikelyGmVin(cleaned) && my in card.modelYearRange()) return card
                }
                "toyota", "lexus" -> {
                    if (ToyotaVinDetector.isLikelyToyotaVin(cleaned) && my in card.modelYearRange()) return card
                }
                "honda", "acura" -> {
                    if (HondaVinDetector.isLikelyHondaVin(cleaned) && my in card.modelYearRange()) return card
                }
                "nissan", "infiniti" -> {
                    if (NissanVinDetector.isLikelyNissanVin(cleaned) && my in card.modelYearRange()) return card
                }
                "hyundai", "kia" -> {
                    if (HyundaiVinDetector.isLikelyHyundaiVin(cleaned) && my in card.modelYearRange()) return card
                }
            }
        }
        return null
    }
}

/** Documented meanings for wedge tiers â€” see `_x431-work/.../015-full-tiers-jeep-ford-dodge.md`. */
@Serializable
data class TieringSemantics(
    val tierIndices: List<Int> = emptyList(),
    val tier0: String = "",
    val tier1: String = "",
    val tier2: String = "",
    val tier3: String = "",
    val tier4: String = "",
    val obdOnlyDefault: String = "",
)

@Serializable
data class MarquePlatformCard(
    val id: String = "",
    val marque: String = "",
    val aliases: List<String> = emptyList(),
    val platformCode: String = "",
    val model: String = "",
    val modelYearStart: Int = 0,
    val modelYearEnd: Int = 0,
    val gatewayNote: String? = null,
    val obdOnlyDefault: Boolean? = null,
    val supportedTiers: List<Int>? = null,
    val tierNotes: Map<String, String> = emptyMap(),
) {
    fun modelYearRange(): IntRange = modelYearStart..modelYearEnd
}

@Serializable
data class MarqueWedgeMatrix(
    val schemaVersion: Int = 1,
    /** Global tier meanings for auditors (doc 015). */
    val tieringSemantics: TieringSemantics? = null,
    val gatewayNote: String = "",
    val obdOnlyDefault: Boolean = false,
    val supportedTiers: List<Int> = emptyList(),
    val platformCards: List<MarquePlatformCard> = emptyList(),
) {
    fun supportedMarques(): Set<String> {
        val out = TreeSet<String>(String.CASE_INSENSITIVE_ORDER)
        platformCards.mapNotNull { it.marque.trim().takeIf(String::isNotEmpty) }.forEach { out.add(it) }
        platformCards.flatMap { it.aliases }.mapNotNull { it.trim().takeIf(String::isNotEmpty) }.forEach { out.add(it) }
        return out
    }

    /** One technician-facing line per platform card (tiers + OBD mode). */
    fun matrixSummaryLines(): List<String> = platformCards.map { card ->
        val tierPart = card.effectiveTiers(this).sorted().joinToString(", ").ifBlank { "â€”" }
        val mode = if (card.effectiveObdOnly(this)) "OBD-only default" else "full diag default"
        "${card.marque} ${card.platformCode} ${card.model} ${card.modelYearStart}-${card.modelYearEnd} Â· tiers $tierPart Â· $mode"
    }

    /** Compact single line listing all cards (legacy / log friendly). */
    fun matrixSummaryLine(): String {
        val tierPart = supportedTiers.sorted().joinToString(", ").ifBlank { "â€”" }
        val mode = if (obdOnlyDefault) "OBD-only default" else "full diag default"
        val cards = platformCards.joinToString("; ") { card ->
            "${card.marque} ${card.platformCode} ${card.model} ${card.modelYearStart}-${card.modelYearEnd}"
        }.ifBlank { "no platform cards" }
        return "$cards Â· tiers $tierPart Â· $mode"
    }
}

fun MarquePlatformCard.effectiveTiers(matrix: MarqueWedgeMatrix): List<Int> =
    supportedTiers ?: matrix.supportedTiers

fun MarquePlatformCard.effectiveObdOnly(matrix: MarqueWedgeMatrix): Boolean =
    obdOnlyDefault ?: matrix.obdOnlyDefault

fun MarquePlatformCard.effectiveGatewayNote(matrix: MarqueWedgeMatrix): String =
    gatewayNote?.takeIf { it.isNotBlank() } ?: matrix.gatewayNote

fun MarqueWedgeMatrix.dodgeCardYearRange(): IntRange? =
    platformCards.firstOrNull {
        val m = it.marque.trim().lowercase()
        m == "dodge" || m == "ram"
    }?.modelYearRange()

@Deprecated("Use MarqueWedgeMatrix", ReplaceWith("MarqueWedgeMatrix", "com.caseforge.scanner.planb.MarqueWedgeMatrix"))
typealias JeepWedgeMatrix = MarqueWedgeMatrix

@Deprecated("Use MarquePlatformCard", ReplaceWith("MarquePlatformCard", "com.caseforge.scanner.planb.MarquePlatformCard"))
typealias JeepPlatformCard = MarquePlatformCard
