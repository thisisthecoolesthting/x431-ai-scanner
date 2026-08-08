package com.caseforge.scanner.obd.j1850

/**
 * dataSource discriminator mirroring the real app's ImmoReadState contract
 * (see TODO on [ImmoReadStateDraft]). LIVE = this module got a real answer
 * from the module over J1850 VPW; LIVE_FALLBACK_STATIC = a live read was
 * attempted but failed/was inconclusive so the UI should fall back to
 * static vehicle-database info; STATIC = no live attempt was made at all
 * (that decision is made upstream of this adapter - e.g. non-Stellantis
 * vehicle or pre-2008 gate not met - [ImmoReadStateAdapter] itself never
 * emits STATIC, see its class doc).
 */
enum class ImmoDataSource { LIVE, LIVE_FALLBACK_STATIC, STATIC }

/**
 * Local mirror of the app's ImmoReadState UI-facing fields, used because
 * this is a clean-room build with no dependency on the real app module.
 *
 * TODO(integration): bind this to the real
 * com.caseforge.scanner.planb.immo.ImmoReadState class when this module is
 * dropped into app/src/main/kotlin/com/caseforge/scanner/obd/j1850/. Field
 * names here were chosen to mirror that contract 1:1 (dataSource,
 * liveStatusLine, liveFallbackReason, sourceBadge, stateSummary,
 * skreemBannerTitle) so the binding should be a straight field-for-field
 * copy / constructor call - see README.md "Remaining integration wiring".
 */
data class ImmoReadStateDraft(
    val dataSource: ImmoDataSource,
    val liveStatusLine: String?,
    val liveFallbackReason: String?,
    val sourceBadge: String,
    val stateSummary: String,
    val skreemBannerTitle: String?
)

/**
 * Maps a [SkreemReadResult] from [SkimVpwReader] onto the fields the app's
 * existing Immo Info card consumes. This adapter always produces either
 * LIVE (successful read) or LIVE_FALLBACK_STATIC (attempted but failed) -
 * never bare STATIC, since "don't even attempt a live read" is an upstream
 * decision (e.g. vehicle-year/OEM gate in ImmoLiveReader / ImmoInfoService)
 * that happens before a SkreemReadResult exists at all.
 */
object ImmoReadStateAdapter {

    fun toDraft(result: SkreemReadResult): ImmoReadStateDraft {
        return if (result.outcome == SkimReadOutcome.MODULE_PRESENT) {
            ImmoReadStateDraft(
                dataSource = ImmoDataSource.LIVE,
                liveStatusLine = buildLiveStatusLine(result),
                liveFallbackReason = null,
                sourceBadge = "LIVE",
                stateSummary = "SKREEM read live over J1850 VPW (PCI-bus).",
                skreemBannerTitle = "SKREEM — Live Read (J1850 VPW)"
            )
        } else {
            val reason = result.detail ?: when (result.outcome) {
                SkimReadOutcome.NO_RESPONSE -> "No response from SKIM on the PCI-bus (J1850 VPW)."
                SkimReadOutcome.MALFORMED_RESPONSE -> "SKIM response could not be parsed."
                else -> "Live SKIM read unavailable."
            }
            ImmoReadStateDraft(
                dataSource = ImmoDataSource.LIVE_FALLBACK_STATIC,
                liveStatusLine = null,
                liveFallbackReason = reason,
                sourceBadge = "STATIC (fallback)",
                stateSummary = "Live SKREEM read unavailable - showing static vehicle data.",
                skreemBannerTitle = "SKREEM — Fallback (static data)"
            )
        }
    }

    /**
     * Example output: "SKREEM present · immobilizer armed · VIN echo 1J4...".
     * Note: the task-example phrase "VIN matches" implies comparing the
     * echoed VIN against the vehicle's already-known VIN (from the app's
     * existing vehicle profile) - this clean-room module has no access to
     * that value, so it reports the raw echo instead. See README.md
     * "Remaining integration wiring" for wiring an actual VIN comparison
     * in once this is bound to the real app.
     */
    private fun buildLiveStatusLine(result: SkreemReadResult): String {
        val vinPart = if (result.vinEcho != null) "VIN echo ${result.vinEcho}" else "VIN not returned"
        val statusWords = result.immobilizerStatus.lowercase().replace('_', ' ')
        return "SKREEM present · immobilizer $statusWords · $vinPart"
    }
}
