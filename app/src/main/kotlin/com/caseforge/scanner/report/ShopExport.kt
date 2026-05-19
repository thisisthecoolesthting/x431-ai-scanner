package com.caseforge.scanner.report

import com.caseforge.scanner.data.DtcEntity
import com.caseforge.scanner.data.SessionEntity
import com.caseforge.scanner.engine.EngineState
import com.caseforge.scanner.engine.ScrapedDtc

/**
 * Portable shop export payload — no PDF or Android UI required.
 *
 * Built from [EngineState], persisted [SessionEntity] rows, or hand-assembled for tests.
 * Pass to [ShopExportFormatter] for CSV or plain-text bodies the share lane can write to disk.
 */
data class ShopExport(
    val vin: String?,
    val vehicleSummary: String?,
    /** Epoch millis for the scan / export moment. */
    val timestampMs: Long,
    /** Human-readable link label, e.g. "ELM327 USB" or "OEM Bluetooth". */
    val transport: String?,
    val dtcs: List<ShopExportDtcRow>,
    /** PID or label → formatted reading (unit included when known). */
    val liveData: Map<String, String>,
    /** Free-form technician notes; empty string when not yet filled in. */
    val technicianNotes: String = TECHNICIAN_NOTES_PLACEHOLDER,
    /** Optional narrative repair story; omitted from output when null or blank. */
    val repairStoryText: String? = null,
    val symptom: String? = null,
    val rootCause: String? = null,
    val recommendedRepair: String? = null,
    val shopName: String = DEFAULT_SHOP_NAME,
    val technicianName: String? = null,
) {
    companion object {
        const val DEFAULT_SHOP_NAME = "Together Car Works"
        const val TECHNICIAN_NOTES_PLACEHOLDER = ""

        /** Build from a live engine scrape (overlay / OEM path). */
        fun fromEngineState(
            state: EngineState,
            transport: String? = null,
            timestampMs: Long = state.updatedAtMs.takeIf { it > 0L } ?: System.currentTimeMillis(),
            technicianNotes: String = TECHNICIAN_NOTES_PLACEHOLDER,
            repairStoryText: String? = null,
            shopName: String = DEFAULT_SHOP_NAME,
            technicianName: String? = null,
        ): ShopExport = ShopExport(
            vin = state.vehicleVin,
            vehicleSummary = state.vehicleSummary,
            timestampMs = timestampMs,
            transport = transport,
            dtcs = state.dtcs.map { it.toExportRow() },
            liveData = state.liveData.formatLiveReadings(),
            technicianNotes = technicianNotes,
            repairStoryText = repairStoryText,
            shopName = shopName,
            technicianName = technicianName,
        )

        /** Build from a persisted diagnostic session + DTC rows. */
        fun fromSession(
            session: SessionEntity,
            dtcs: List<DtcEntity>,
            transport: String? = null,
            vehicleSummary: String? = null,
            liveData: Map<String, String> = emptyMap(),
            technicianNotes: String = TECHNICIAN_NOTES_PLACEHOLDER,
            repairStoryText: String? = null,
            shopName: String = DEFAULT_SHOP_NAME,
            technicianName: String? = null,
        ): ShopExport {
            val ended = session.endedAt ?: session.startedAt
            val story = repairStoryText?.takeIf { it.isNotBlank() }
                ?: buildRepairStoryNarrative(session)
            return ShopExport(
                vin = session.vin,
                vehicleSummary = vehicleSummary,
                timestampMs = ended,
                transport = transport,
                dtcs = dtcs.map { it.toExportRow() },
                liveData = liveData,
                technicianNotes = technicianNotes,
                repairStoryText = story,
                symptom = session.symptom,
                rootCause = session.rootCause,
                recommendedRepair = session.recommendedRepair,
                shopName = shopName,
                technicianName = technicianName,
            )
        }

        /** Merge session persistence with a fresh engine snapshot (live PIDs + scraped DTCs). */
        fun fromSessionAndEngine(
            session: SessionEntity,
            dtcs: List<DtcEntity>,
            state: EngineState,
            transport: String? = null,
            technicianNotes: String = TECHNICIAN_NOTES_PLACEHOLDER,
            repairStoryText: String? = null,
            shopName: String = DEFAULT_SHOP_NAME,
            technicianName: String? = null,
        ): ShopExport {
            val mergedDtcs = if (state.dtcs.isNotEmpty()) {
                state.dtcs.map { it.toExportRow() }
            } else {
                dtcs.map { it.toExportRow() }
            }
            val ended = session.endedAt ?: session.startedAt
            val story = repairStoryText?.takeIf { it.isNotBlank() }
                ?: buildRepairStoryNarrative(session)
            return ShopExport(
                vin = session.vin ?: state.vehicleVin,
                vehicleSummary = state.vehicleSummary,
                timestampMs = ended,
                transport = transport,
                dtcs = mergedDtcs,
                liveData = state.liveData.formatLiveReadings(),
                technicianNotes = technicianNotes,
                repairStoryText = story,
                symptom = session.symptom,
                rootCause = session.rootCause,
                recommendedRepair = session.recommendedRepair,
                shopName = shopName,
                technicianName = technicianName,
            )
        }

        /** Map persisted transport keys (settings / link kind) to a display label. */
        fun transportLabel(key: String?): String? {
            if (key.isNullOrBlank()) return null
            return when (key.lowercase()) {
                "elm327_usb", "usb_obd", "usb_cable" -> "ELM327 USB"
                "oem_usb", "launch_usb", "vci_usb" -> "OEM USB"
                "oem_bt", "launch_bt", "vci_bt", "bluetooth" -> "OEM Bluetooth"
                "elm327_bt", "obd_bt" -> "ELM327 Bluetooth"
                "auto" -> "Auto"
                else -> key
            }
        }

        private fun buildRepairStoryNarrative(session: SessionEntity): String? {
            val parts = buildList {
                session.symptom?.takeIf { it.isNotBlank() }?.let { add("Symptom: $it") }
                session.rootCause?.takeIf { it.isNotBlank() }?.let { add("Root cause: $it") }
                session.recommendedRepair?.takeIf { it.isNotBlank() }?.let { add("Recommended repair: $it") }
            }
            return parts.joinToString("\n\n").ifBlank { null }
        }
    }
}

data class ShopExportDtcRow(
    val code: String,
    val module: String?,
    val description: String?,
    val status: String?,
)

private fun ScrapedDtc.toExportRow() = ShopExportDtcRow(
    code = code,
    module = module,
    description = description,
    status = status,
)

private fun DtcEntity.toExportRow() = ShopExportDtcRow(
    code = code,
    module = module,
    description = description,
    status = status,
)

private fun Map<String, Double>.formatLiveReadings(): Map<String, String> =
    entries.sortedBy { it.key }
        .associate { (pid, value) ->
            pid to formatLiveValue(value)
        }

private fun formatLiveValue(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString()
    else String.format(java.util.Locale.US, "%.2f", value)
