package com.caseforge.scanner.planb.gateway

import android.content.Context
import com.caseforge.scanner.agent.discovery.VehicleProfileLoader
import com.caseforge.scanner.planb.MarqueWedgeConfig

/**
 * One ECU entry for aftermarket CAN-ID routing scaffolding (logical id + nominal request/response IDs).
 *
 * @property id Stable internal slug (examples: `"pcm"`).
 * @property name Technician-facing neutral label — prefer resource-backed strings before UI.
 */
data class EcuEntry(
    val id: String,
    val name: String,
    val reqId: Int,
    val respId: Int,
)

/**
 * Minimal defaults for PCM on common OBD powertrain wedge IDs (functional request / physical response baseline).
 *
 * Vehicles with active gateway policies may still block these IDs from reaching the PCM; compare
 * [StellantisGatewayNotes].
 */
fun jeepWedgeDefaults(): List<EcuEntry> = listOf(
    EcuEntry(
        id = "pcm",
        name = "Powertrain control module",
        reqId = 0x7E0,
        respId = 0x7E8,
    ),
)

/**
 * Nominal Ford PCM OBD-II powertrain wedge: request **0x7E0**, response **0x7E8**.
 *
 * Ford (and Lincoln) platforms sometimes use different functional or physical addressing — e.g. some
 * heavy-duty, non-North-American, or gateway-segmented buses may not present the PCM on these IDs from
 * the OBD port. Treat this as the default **aftermarket scan-tool baseline**, not a universal guarantee.
 */
fun fordWedgeDefaults(): List<EcuEntry> = listOf(
    EcuEntry(
        id = "pcm",
        name = "Powertrain control module",
        reqId = 0x7E0,
        respId = 0x7E8,
    ),
)

/**
 * When the wedge matrix resolves **`ford-windstar-2000`** for [vin], Tier 1 gateway scaffolding uses this
 * slice (same nominal OBD PCM IDs as [fordWedgeDefaults]; copy calls out Windstar / pre-SGW notes from the matrix).
 */
fun fordWindstarWedgeDefaults(): List<EcuEntry> = listOf(
    EcuEntry(
        id = "pcm",
        name = "PCM — Windstar profile (ISO 9141-2 / KWP baseline; Tier 1 gateway scaffold)",
        reqId = 0x7E0,
        respId = 0x7E8,
    ),
)

/**
 * Ford gateway defaults keyed off bundled wedge + vehicle profile: Windstar card → [fordWindstarWedgeDefaults],
 * otherwise [fordWedgeDefaults]. No VIN → generic Ford wedge.
 */
fun fordGatewayDefaultsForVin(context: Context, vin: String?): List<EcuEntry> {
    val v = vin?.trim()?.takeIf { it.isNotEmpty() } ?: return fordWedgeDefaults()
    val cardId = MarqueWedgeConfig.findCardForVin(context, v)?.id
    return if (cardId.equals(VehicleProfileLoader.DEFAULT_WINDSTAR_ID, ignoreCase = true)) {
        fordWindstarWedgeDefaults()
    } else {
        fordWedgeDefaults()
    }
}

/**
 * Nominal Dodge / Ram PCM wedge aligned with common Stellantis (ex-FCA) OBD powertrain IDs:
 * request **0x7E0**, response **0x7E8**. Gateway and SGW behavior still apply; see [StellantisGatewayNotes].
 */
fun dodgeWedgeDefaults(): List<EcuEntry> = listOf(
    EcuEntry(
        id = "pcm",
        name = "Powertrain control module",
        reqId = 0x7E0,
        respId = 0x7E8,
    ),
)

/**
 * Marque-keyed default ECU maps for Plan B gateway scaffolding.
 *
 * [forMarque] is case-insensitive and trims whitespace. Unknown [marqueId] values fall back to [jeepWedgeDefaults]
 * (historical default for this lane).
 */
object GatewayMap {
    const val MARQUE_JEEP = "jeep"
    const val MARQUE_FORD = "ford"
    const val MARQUE_DODGE = "dodge"
    const val MARQUE_RAM = "ram"
    const val MARQUE_CHRYSLER = "chrysler"
    const val MARQUE_CHEVROLET = "chevrolet"
    const val MARQUE_GMC = "gmc"
    const val MARQUE_BUICK = "buick"
    const val MARQUE_CADILLAC = "cadillac"
    const val MARQUE_TOYOTA = "toyota"
    const val MARQUE_LEXUS = "lexus"
    const val MARQUE_HONDA = "honda"
    const val MARQUE_ACURA = "acura"
    const val MARQUE_NISSAN = "nissan"
    const val MARQUE_INFINITI = "infiniti"
    const val MARQUE_HYUNDAI = "hyundai"
    const val MARQUE_KIA = "kia"

    fun forMarque(marqueId: String): List<EcuEntry> {
        val key = marqueId.trim().lowercase()
        return when (key) {
            MARQUE_FORD -> fordWedgeDefaults()
            MARQUE_DODGE, MARQUE_RAM, MARQUE_CHRYSLER -> dodgeWedgeDefaults()
            MARQUE_CHEVROLET, MARQUE_GMC, MARQUE_BUICK, MARQUE_CADILLAC -> jeepWedgeDefaults()
            MARQUE_TOYOTA, MARQUE_LEXUS -> jeepWedgeDefaults()
            MARQUE_HONDA, MARQUE_ACURA -> jeepWedgeDefaults()
            MARQUE_NISSAN, MARQUE_INFINITI -> jeepWedgeDefaults()
            MARQUE_HYUNDAI, MARQUE_KIA -> jeepWedgeDefaults()
            MARQUE_JEEP -> jeepWedgeDefaults()
            else -> jeepWedgeDefaults()
        }
    }

    /** Resolve a logical ECU slug against a marque-keyed default map (case-insensitive [ecuId]). */
    fun lookupEntry(marqueId: String, ecuId: String): EcuEntry? =
        forMarque(marqueId).firstOrNull { it.id.equals(ecuId.trim(), ignoreCase = true) }
}

