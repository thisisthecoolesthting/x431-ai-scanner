package com.caseforge.scanner.planb.immo

import com.caseforge.scanner.planb.PlanbMarque

/**
 * SKREEM / PATS banner labels with live vs bundled-reference fallback copy.
 */
object ImmoBannerCopy {

    fun skreemBannerTitle(live: Boolean): String =
        if (live) "SKIM / SKREEM · live read" else "SKIM / SKREEM · reference"

    fun patsBannerTitle(live: Boolean): String =
        if (live) "PATS · live read" else "PATS · reference"

    fun sourceBadge(source: ImmoDataSource): String = when (source) {
        ImmoDataSource.LIVE -> "Live"
        ImmoDataSource.LIVE_FALLBACK_STATIC -> "Reference (live unavailable)"
        ImmoDataSource.STATIC -> "Reference"
    }

    fun liveUnavailableNote(reason: String?): String =
        reason?.takeIf { it.isNotBlank() }
            ?: "Connect VCI and select a marque with live-read config for gateway UDS status."

    fun formatLiveStatusLine(marque: PlanbMarque, summary: String): String = when {
        marque == PlanbMarque.FORD ->
            "PATS probe: $summary"
        SkreemModule.isStellantisMarque(marque) ->
            "SKREEM probe: $summary"
        else ->
            "Immo probe: $summary"
    }
}

enum class ImmoDataSource {
    /** Bundled JSON only — no VCI or no live-read config. */
    STATIC,
    /** Gateway UDS read succeeded. */
    LIVE,
    /** VCI connected and live read attempted but fell back to bundled copy. */
    LIVE_FALLBACK_STATIC,
}
