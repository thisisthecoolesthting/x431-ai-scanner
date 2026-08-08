package com.caseforge.scanner.planb.immo

import android.content.Context
import com.caseforge.scanner.agent.ObdElmEngine
import com.caseforge.scanner.planb.PlanbMarque
import com.caseforge.scanner.planb.coding.CodingChecklistLoader
import com.caseforge.scanner.update.AssetOverlay
import com.caseforge.scanner.vci.VciCommunicator
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/**
 * Loads marque-specific immobilizer info copy from `planb/immo-info-<marque>.json`.
 * When a connected [VciCommunicator] is supplied, attempts read-only gateway UDS immo probes
 * and falls back to bundled SKREEM/PATS reference copy.
 */
class ImmoInfoService(
    private val context: Context,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
    },
) {

    fun assetPath(marque: PlanbMarque): String =
        "${CodingChecklistLoader.ASSET_DIR}/immo-info-${marque.id}.json"

    fun load(marque: PlanbMarque): ImmoInfoBanner? = runCatching {
        val text = AssetOverlay.readText(context, assetPath(marque)) ?: return@runCatching null
        json.decodeFromString<ImmoInfoBanner>(text)
    }.getOrNull()

    /**
     * Stable read model for UI: bundled [ImmoInfoBanner] when present, plus static risk copy.
     */
    fun readState(marque: PlanbMarque): ImmoReadState =
        buildReadState(marque, banner = load(marque), live = null)

    /**
     * Bundled immo info plus optional live read when connected: J1850 VPW SKIM/SKREEM
     * ([elmEngine], pre-2008 Stellantis only - see [J1850SkreemBridge.isJ1850SkreemCandidate])
     * takes priority when applicable, otherwise the existing CAN UDS gateway read ([vci],
     * unchanged) when JSON defines [ImmoLiveReadConfig].
     */
    suspend fun readStateWithLive(
        marque: PlanbMarque,
        vci: VciCommunicator? = null,
        vin: String? = null,
        elmEngine: ObdElmEngine? = null,
    ): ImmoReadState {
        val banner = load(marque)
        val live = when {
            elmEngine != null && J1850SkreemBridge.isJ1850SkreemCandidate(marque, vin) ->
                ImmoLiveReader.tryLiveReadJ1850(elmEngine, vin)
            vci != null && banner?.liveRead?.enabled == true ->
                ImmoLiveReader.tryLiveRead(vci, marque, banner)
            else -> null
        }
        return buildReadState(marque, banner, live)
    }

    internal fun buildReadState(
        marque: PlanbMarque,
        banner: ImmoInfoBanner?,
        live: ImmoLiveStatus?,
    ): ImmoReadState {
        val dataSource = when {
            live?.success == true -> ImmoDataSource.LIVE
            live?.attempted == true -> ImmoDataSource.LIVE_FALLBACK_STATIC
            else -> ImmoDataSource.STATIC
        }
        val staticSummary = buildStaticSummary(marque, banner)
        val stateSummary = when (dataSource) {
            ImmoDataSource.LIVE -> {
                val line = ImmoBannerCopy.formatLiveStatusLine(marque, live!!.summaryLine)
                "$line\n\n$staticSummary"
            }
            ImmoDataSource.LIVE_FALLBACK_STATIC -> {
                val note = ImmoBannerCopy.liveUnavailableNote(live?.summaryLine)
                "$note\n\n$staticSummary"
            }
            ImmoDataSource.STATIC -> staticSummary
        }
        val isFordPats = marque == PlanbMarque.FORD || banner?.bannerKind == "pats_info_only"
        val skreemTitle = if (SkreemModule.isStellantisMarque(marque) && banner?.skreemModule != null) {
            ImmoBannerCopy.skreemBannerTitle(live = dataSource == ImmoDataSource.LIVE)
        } else {
            null
        }
        val patsTitle = if (isFordPats) {
            ImmoBannerCopy.patsBannerTitle(live = dataSource == ImmoDataSource.LIVE)
        } else {
            null
        }
        return ImmoReadState(
            marque = marque,
            banner = banner,
            stateSummary = stateSummary.trim(),
            riskBanner = ImmoRiskCopy.infoOnlyBanner,
            disclaimer = ImmoRiskCopy.fullDisclaimer,
            dataSource = dataSource,
            liveStatusLine = live?.takeIf { it.success }?.summaryLine,
            liveFallbackReason = live?.takeIf { it.attempted && !it.success }?.summaryLine,
            sourceBadge = ImmoBannerCopy.sourceBadge(dataSource),
            skreemBannerTitle = skreemTitle,
            patsBannerTitle = patsTitle,
        )
    }

    private fun buildStaticSummary(marque: PlanbMarque, banner: ImmoInfoBanner?): String =
        buildString {
            if (banner != null) {
                if (banner.title.isNotBlank()) appendLine(banner.title)
                if (banner.body.isNotBlank()) appendLine(banner.body)
                banner.skreemModule?.let { sk ->
                    if (sk.moduleName.isNotBlank()) appendLine("${sk.moduleName}: ${sk.role}")
                    if (sk.tier3Scope.isNotBlank()) appendLine(sk.tier3Scope)
                }
                if (banner.footnote.isNotBlank()) appendLine(banner.footnote)
                if (isEmpty()) append("Loaded immo info (${banner.bannerKind})")
            } else {
                append("No bundled immo asset for ${marque.id}.")
            }
            if (banner?.skreemModule == null && marque == PlanbMarque.FORD) {
                appendLine(SkreemModule.fordNotApplicableNote())
            }
        }.trim()
}

/**
 * Marque-specific immobilizer **information** tier; [banner] is optional when JSON is missing.
 */
data class ImmoReadState(
    val marque: PlanbMarque,
    val banner: ImmoInfoBanner?,
    val stateSummary: String,
    val riskBanner: String,
    val disclaimer: String,
    val dataSource: ImmoDataSource = ImmoDataSource.STATIC,
    /** Present when [dataSource] is [ImmoDataSource.LIVE]. */
    val liveStatusLine: String? = null,
    /** Present when live read was attempted but fell back to bundled copy. */
    val liveFallbackReason: String? = null,
    val sourceBadge: String = ImmoBannerCopy.sourceBadge(ImmoDataSource.STATIC),
    val skreemBannerTitle: String? = null,
    val patsBannerTitle: String? = null,
)
