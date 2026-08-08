package com.caseforge.scanner.planb.immo

import com.caseforge.scanner.obd.j1850.SkimReadOutcome
import com.caseforge.scanner.obd.j1850.SkreemReadResult
import com.caseforge.scanner.planb.MarqueWedgeConfig
import com.caseforge.scanner.planb.PlanbMarque

/**
 * Bus-protocol dispatch + result mapping for the J1850 VPW SKREEM read lane
 * ([com.caseforge.scanner.obd.j1850]), kept separate from [ImmoLiveReader]'s CAN UDS path so
 * that path stays byte-for-byte untouched.
 *
 * Enum-collision note: [com.caseforge.scanner.obd.j1850.ImmoReadStateAdapter] (copied verbatim
 * from fredlink-skreem-core) defines its own local `com.caseforge.scanner.obd.j1850.ImmoDataSource`
 * (LIVE / LIVE_FALLBACK_STATIC / STATIC) as a standalone-module mirror of this package's
 * [ImmoDataSource] — same three case names, different types, will not assign to each other. This
 * bridge sidesteps the collision entirely rather than converting between the two enums: it maps
 * [SkreemReadResult] straight to [ImmoLiveStatus] (attempted/success/summaryLine/rawHex — the
 * same shape [ImmoLiveReader.tryLiveRead]'s CAN path already produces), so the existing
 * `ImmoInfoService.buildReadState` performs the actual [ImmoDataSource] selection and
 * skreemBannerTitle/sourceBadge/stateSummary derivation exactly as it already does for the CAN
 * path — untouched, and not duplicated here. As a consequence,
 * [com.caseforge.scanner.obd.j1850.ImmoReadStateAdapter] / `ImmoReadStateDraft` / its local
 * `ImmoDataSource` are not called from anywhere in the app; they remain only because
 * fredlink-skreem-core's own README lists them as part of the drop-in module and its ported
 * test suite covers them. See RECONCILIATION.md for the full rationale.
 */
object J1850SkreemBridge {

    /**
     * SKREEM (Chrysler PCI-bus / SAE J1850 VPW) covers Stellantis marques on pre-2008 model
     * years; CAN UDS 0x22 (the existing, untouched [ImmoLiveReader.tryLiveRead] path) is assumed
     * correct from 2008 onward. Per task scope ("Stellantis pre-2008 (SKREEM era)"); the
     * clean-room core's own hardware/firmware research (fredlink-skreem-core/README.md) is
     * grounded specifically in 2004-2006 Jeep-era PCI-bus captures, so this cutoff may need
     * narrowing or platform-splitting once real-vehicle captures beyond that window exist.
     */
    const val SKREEM_ERA_LAST_MODEL_YEAR: Int = 2007

    /**
     * True when [marque] + [vin] should be read over J1850 VPW instead of the CAN path.
     *
     * Reuses [MarqueWedgeConfig.decodeVinModelYear] (VIN position 10, North American digit
     * codes '1'-'9' -> 2001-2009, unambiguous without needing the position-7 30-year-cycle
     * disambiguation that letter codes require) rather than adding a new decoder — the
     * SKREEM/pre-2008 window (2001-2007) is entirely digit-coded, so this existing helper is
     * exact for this gate; no new VIN-year utility needed here.
     */
    fun isJ1850SkreemCandidate(marque: PlanbMarque, vin: String?): Boolean {
        if (!SkreemModule.isStellantisMarque(marque)) return false
        val modelYear = vin?.let { MarqueWedgeConfig.decodeVinModelYear(it) } ?: return false
        return modelYear <= SKREEM_ERA_LAST_MODEL_YEAR
    }

    /**
     * Maps a [SkreemReadResult] onto the same [ImmoLiveStatus] shape
     * [ImmoLiveReader.tryLiveRead] (CAN) already produces: MODULE_PRESENT -> `success = true` ->
     * (via `ImmoInfoService.buildReadState`) [ImmoDataSource.LIVE] + `liveStatusLine`;
     * NO_RESPONSE / MALFORMED_RESPONSE -> `attempted = true, success = false` ->
     * [ImmoDataSource.LIVE_FALLBACK_STATIC] + `liveFallbackReason`.
     *
     * [knownVin], when available, is compared against [SkreemReadResult.vinEcho] and folded into
     * the summary line — the core module's own README flags this as open work ("Wire an actual
     * VIN comparison") since the clean-room core has no access to the app's known VIN by design.
     */
    fun toImmoLiveStatus(result: SkreemReadResult, knownVin: String?): ImmoLiveStatus {
        val vinPart = describeVinEcho(result.vinEcho, knownVin)
        return when (result.outcome) {
            SkimReadOutcome.MODULE_PRESENT -> {
                val statusWords = result.immobilizerStatus.lowercase().replace('_', ' ')
                ImmoLiveStatus(
                    attempted = true,
                    success = true,
                    summaryLine = "SKREEM present · immobilizer $statusWords · $vinPart",
                    rawHex = result.rawHex.takeIf { it.isNotBlank() },
                )
            }
            SkimReadOutcome.NO_RESPONSE, SkimReadOutcome.MALFORMED_RESPONSE -> ImmoLiveStatus(
                attempted = true,
                success = false,
                summaryLine = result.detail ?: when (result.outcome) {
                    SkimReadOutcome.NO_RESPONSE -> "No response from SKIM on the PCI-bus (J1850 VPW)."
                    else -> "SKIM response could not be parsed."
                },
                rawHex = result.rawHex.takeIf { it.isNotBlank() },
            )
        }
    }

    private fun describeVinEcho(vinEcho: String?, knownVin: String?): String = when {
        vinEcho == null -> "VIN not returned"
        knownVin.isNullOrBlank() -> "VIN echo $vinEcho"
        vinEcho.equals(knownVin, ignoreCase = true) -> "VIN echo $vinEcho (matches)"
        else -> "VIN echo $vinEcho (differs from known VIN)"
    }
}
