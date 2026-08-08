package com.caseforge.scanner.ui.session

import android.content.Context
import com.caseforge.scanner.agent.discovery.DiscoveryReport
import com.caseforge.scanner.agent.session.BackgroundObdSnapshot
import com.caseforge.scanner.agent.session.DiagnosticPhotoInsights
import com.caseforge.scanner.agent.session.DiagnosticPhotoInsightsCodec
import com.caseforge.scanner.planb.MarqueWedgeConfig
import com.caseforge.scanner.transfer.SessionEventLogger

/**
 * Chooses visual-strip content from session context. Priority when multiple signals apply:
 * 1. OBD connected → DIA status + live measurements
 * 2. Agent component mention → component hint card
 * 3. Marque wedge match → wedge card
 * 4. Session start / idle → wizard photo thumbnails
 */
object SessionVisualComposer {

    sealed class StripItem {
        data class PhotoThumbnails(val paths: List<String>) : StripItem()
        data class WedgeCard(
            val marque: String,
            val model: String,
            val platformCode: String,
            val gatewayNote: String?,
        ) : StripItem()

        data class DiaStatus(
            val connectionState: String,
            val protocol: String?,
            val ecuAddr: String?,
            val dtcCount: Int,
            val monitors: String?,
        ) : StripItem()

        data class LiveMeasurements(
            val rpm: Float?,
            val coolantC: Float?,
            val voltage: Float?,
            val rpmHistory: List<Float>,
        ) : StripItem()

        data class ComponentHint(val component: String, val hint: String) : StripItem()

        data class PhotoInsightCard(
            val confidence: String,
            val bullets: List<String>,
            val suggestedNextSteps: List<String>,
        ) : StripItem()
    }

    data class PaneState(
        val primary: StripItem,
        val secondary: StripItem? = null,
        /** Collapsible live gauge tiles (RPM / coolant / voltage) when "called". */
        val gaugeKinds: List<GaugeKind> = emptyList(),
    )

    private val componentPatterns = listOf(
        Regex("battery", RegexOption.IGNORE_CASE) to "battery",
        Regex("fuse", RegexOption.IGNORE_CASE) to "fuse",
        Regex("skreem|skim|pats|immobil", RegexOption.IGNORE_CASE) to "skreem",
        Regex("alternator", RegexOption.IGNORE_CASE) to "alternator",
        Regex("starter", RegexOption.IGNORE_CASE) to "starter",
    )

    private val gaugePatterns = listOf(
        GaugeKind.RPM to Regex("""\b(rpm|tach|engine\s*speed|revs?)\b""", RegexOption.IGNORE_CASE),
        GaugeKind.COOLANT to Regex("""\b(coolant|ect|cooling|temperature)\b""", RegexOption.IGNORE_CASE),
        GaugeKind.VOLTAGE to Regex("""\b(voltage|volts?|12v|battery\s*v)\b""", RegexOption.IGNORE_CASE),
    )

    private val gaugeAllPattern =
        Regex("""\b(live\s*data|gauges?|instrument\s*panel)\b""", RegexOption.IGNORE_CASE)

    /**
     * Gauges appear only when invoked: OBD live link, agent text keywords, or (RPM large only) empty chat.
     */
    fun resolveCalledGauges(
        obd: BackgroundObdSnapshot,
        lastAgentText: String?,
    ): List<GaugeKind> {
        val out = linkedSetOf<GaugeKind>()
        if (obd.connected) {
            out += GaugeKind.RPM
            out += GaugeKind.COOLANT
            out += GaugeKind.VOLTAGE
        }
        detectGaugesFromAgent(lastAgentText)?.let { out += it }
        return out.toList()
    }

    private fun detectGaugesFromAgent(text: String?): Set<GaugeKind>? {
        if (text.isNullOrBlank()) return null
        if (gaugeAllPattern.containsMatchIn(text)) {
            return setOf(GaugeKind.RPM, GaugeKind.COOLANT, GaugeKind.VOLTAGE)
        }
        val matched = gaugePatterns.filter { (_, rx) -> rx.containsMatchIn(text) }.map { it.first }
        return matched.takeIf { it.isNotEmpty() }?.toSet()
    }

    fun compose(
        context: Context,
        sessionId: String,
        session: ActiveCustomerSession,
        discoveryReport: DiscoveryReport?,
        obd: BackgroundObdSnapshot,
        lastAgentText: String?,
        lastToolHint: String?,
        photoInsights: DiagnosticPhotoInsights? = null,
    ): PaneState {
        val items = buildList {
            photoInsights?.let { insights ->
                val bullets = DiagnosticPhotoInsightsCodec.summaryBullets(insights)
                if (bullets.isNotEmpty()) {
                    add(
                        StripItem.PhotoInsightCard(
                            confidence = insights.confidence,
                            bullets = bullets,
                            suggestedNextSteps = insights.suggestedNextSteps.take(3),
                        ),
                    )
                }
            }

            if (obd.connected) {
                add(
                    StripItem.DiaStatus(
                        connectionState = obd.linkStatus,
                        protocol = obd.protocol,
                        ecuAddr = obd.ecuAddress,
                        dtcCount = obd.storedDtcCount + obd.pendingDtcCount,
                        monitors = obd.monitorsReady,
                    ),
                )
                // Live PIDs render as [SessionGaugePane] tiles, not a fixed strip row.
            }

            detectComponent(lastAgentText)?.let { add(StripItem.ComponentHint(it, componentHint(it))) }
            lastToolHint?.let { add(StripItem.ComponentHint("capability", it)) }

            session.vin?.let { vin ->
                MarqueWedgeConfig.findCardForVin(context, vin)?.let { card ->
                    add(
                        StripItem.WedgeCard(
                            marque = card.marque,
                            model = card.model,
                            platformCode = card.platformCode,
                            gatewayNote = card.gatewayNote,
                        ),
                    )
                }
            }

            val photos = session.photoPaths().filter { it.isNotBlank() }
            if (photos.isNotEmpty() && !obd.connected) {
                add(StripItem.PhotoThumbnails(photos))
            } else if (photos.isNotEmpty() && obd.connected) {
                // Thumbnails as secondary when OBD live
            }
        }

        val primary = items.firstOrNull() ?: StripItem.PhotoThumbnails(session.photoPaths())
        val secondary = when {
            obd.connected && session.photoPaths().isNotEmpty() ->
                StripItem.PhotoThumbnails(session.photoPaths())
            items.size > 1 -> items[1].takeIf { it != primary }
            else -> null
        }

        val gaugeKinds = resolveCalledGauges(obd, lastAgentText)
        val state = PaneState(primary = primary, secondary = secondary, gaugeKinds = gaugeKinds)
        return state
    }

    fun logPaneState(context: Context, sessionId: String, state: PaneState) {
        SessionEventLogger.log(
            context,
            sessionId,
            "visual_pane",
            detail = state.primary::class.simpleName.orEmpty(),
            extra = buildMap {
                put("primary", state.primary::class.simpleName.orEmpty())
                state.secondary?.let { put("secondary", it::class.simpleName.orEmpty()) }
                if (state.gaugeKinds.isNotEmpty()) {
                    put("gauges", state.gaugeKinds.joinToString(",") { it.name })
                }
            },
        )
    }

    fun attachmentsForPhotoInsights(insights: DiagnosticPhotoInsights): List<VisualAttachment> {
        val bullets = DiagnosticPhotoInsightsCodec.summaryBullets(insights)
        if (bullets.isEmpty()) return emptyList()
        return listOf(
            VisualAttachment(
                kind = "visual_card",
                title = "Photo analysis (${insights.confidence})",
                subtitle = insights.disclaimer,
                bullets = bullets,
            ),
        )
    }

    fun attachmentsForAgentText(text: String, obd: BackgroundObdSnapshot?): List<VisualAttachment> {
        val out = mutableListOf<VisualAttachment>()
        detectComponent(text)?.let { comp ->
            out += VisualAttachment(
                kind = "visual_card",
                title = comp.replaceFirstChar { it.uppercase() },
                subtitle = componentHint(comp),
            )
        }
        if (obd?.connected == true && (obd.storedDtcCount + obd.pendingDtcCount) > 0) {
            val rows = obd.dtcSummary?.let { parseDtcRows(it) }.orEmpty()
            if (rows.isNotEmpty()) {
                out += VisualAttachment(
                    kind = "dtc_table",
                    title = "Stored / pending DTCs",
                    dtcRows = rows,
                )
            }
        }
        if (obd?.connected == true && obd.rpmHistory.size >= 2) {
            out += VisualAttachment(
                kind = "chart",
                title = "RPM trend",
                chartLabel = "RPM",
                chartSeries = obd.rpmHistory,
            )
        }
        return out
    }

    private fun detectComponent(text: String?): String? {
        if (text.isNullOrBlank()) return null
        return componentPatterns.firstOrNull { (rx, _) -> rx.containsMatchIn(text) }?.second
    }

    private fun componentHint(component: String): String = when (component) {
        "battery" -> "Check terminal torque, corrosion, and resting voltage ≥12.4 V."
        "fuse" -> "Verify fuse rating and supply side with test light — swap only same amperage."
        "skreem" -> "SKREEM/PATS path — no in-app programming; partner workflow only."
        "alternator" -> "Measure charge voltage running: 13.5–14.5 V at battery."
        "starter" -> "Listen for click vs. spin — battery feed and ground first."
        else -> "See capability row for OEM-specific steps."
    }

    private fun parseDtcRows(summary: String): List<DtcRow> {
        val stored = Regex("Stored:\\s*([^;]+)").find(summary)?.groupValues?.get(1)?.trim().orEmpty()
        val pending = Regex("Pending:\\s*(.+)").find(summary)?.groupValues?.get(1)?.trim().orEmpty()
        val rows = mutableListOf<DtcRow>()
        if (!stored.equals("none", true)) {
            stored.split(',').map { it.trim() }.filter { it.isNotBlank() }.forEach {
                rows += DtcRow(it, "stored")
            }
        }
        if (!pending.equals("none", true)) {
            pending.split(',').map { it.trim() }.filter { it.isNotBlank() }.forEach {
                rows += DtcRow(it, "pending")
            }
        }
        return rows
    }
}
