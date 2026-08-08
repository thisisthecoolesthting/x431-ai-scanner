package com.caseforge.scanner.planb.immo

import kotlinx.serialization.Serializable

/**
 * Technician-facing immobilizer **information** only (no key programming).
 */
@Serializable
data class ImmoInfoBanner(
    val schemaVersion: Int = 1,
    val marqueId: String = "",
    /** e.g. `neutral`, `pats_info_only`, `immo_info_only` */
    val bannerKind: String = "neutral",
    val title: String = "",
    val body: String = "",
    val footnote: String = "",
    /** Tier 3 SKIM/SKREEM module role (Stellantis); omitted on Ford (PATS). */
    val skreemModule: SkreemModuleNote? = null,
    /** Optional routed UDS ReadDataByIdentifier probe when VCI is connected. */
    val liveRead: ImmoLiveReadConfig? = null,
)

/**
 * Marque-scoped immobilizer live-read framing (ISO-TP / UDS 0x22). Omit or set [enabled] false to stay static-only.
 */
@Serializable
data class ImmoLiveReadConfig(
    val enabled: Boolean = true,
    /** Request arbitration ID (decimal or hex string in JSON — kotlinx parses Int). */
    val reqCanId: Int = 0x7E0,
    val respCanId: Int = 0x7E8,
    /** UDS data identifier as 4-char hex without prefix, e.g. `"FD01"`. */
    val dataIdentifierHex: String = "",
)

@Serializable
data class SkreemModuleNote(
    val moduleName: String = "SKIM / SKREEM",
    val role: String = "",
    val tier3Scope: String = "",
)
