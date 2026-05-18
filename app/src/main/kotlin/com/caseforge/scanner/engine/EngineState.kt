package com.caseforge.scanner.engine

import com.caseforge.scanner.agent.ScreenSnapshot
import com.caseforge.scanner.ai.RecallMatch
import kotlinx.serialization.Serializable

/**
 * Typed model of what the underlying X431 engine app is currently showing.
 *
 * Built by [EngineScraper] from an accessibility ScreenSnapshot. The Launch AI overlay
 * UI reads this model — it never reads the raw accessibility tree directly.
 *
 * `Unknown` is a first-class state: when the scraper doesn't recognize the screen,
 * we fall back to passing the raw snapshot through so the BehindCurtainPane can
 * render the user-visible X431 text + the overlay can still let the user "peek".
 */
@Serializable
data class EngineState(
    val screen: ScreenKind,
    val vehicleVin: String? = null,
    val vehicleSummary: String? = null,
    val currentMenuPath: List<String> = emptyList(),
    val dtcs: List<ScrapedDtc> = emptyList(),
    val recallMatches: List<RecallMatch> = emptyList(),
    val liveData: Map<String, Double> = emptyMap(),
    val busy: Boolean = false,
    val errorBanner: String? = null,
    val updatedAtMs: Long = 0L,
    val raw: ScreenSnapshot? = null,
) {
    fun isStale(thresholdMs: Long = 3000L, now: Long = System.currentTimeMillis()): Boolean =
        now - updatedAtMs > thresholdMs

    companion object {
        val EMPTY = EngineState(screen = ScreenKind.NoEngine)
    }
}

@Serializable
sealed class ScreenKind {
    @Serializable object NoEngine : ScreenKind()
    @Serializable object HomeMenu : ScreenKind()
    @Serializable object VehicleSelect : ScreenKind()
    @Serializable object DiagnoseMenu : ScreenKind()
    @Serializable object FullScanProgress : ScreenKind()
    @Serializable object FullScanResults : ScreenKind()
    @Serializable object DtcDetail : ScreenKind()
    @Serializable object LiveDataView : ScreenKind()
    @Serializable object ActuationTest : ScreenKind()
    @Serializable data class SequenceRunner(val sequenceId: String) : ScreenKind()
    @Serializable object Settings : ScreenKind()
    @Serializable data class Dialog(val text: String) : ScreenKind()
    @Serializable data class Unknown(val hint: String) : ScreenKind()
}

@Serializable
data class ScrapedDtc(
    val code: String,
    val description: String? = null,
    val module: String? = null,
    val status: String? = null, // "current", "history", "pending"
)
