package com.caseforge.scanner.agent.session

import android.content.Context
import com.caseforge.scanner.obd.ObdDtcReader
import com.caseforge.scanner.planb.golden.GoldenLogParser
import com.caseforge.scanner.transfer.GoldenCaptureStorage

/**
 * Reads DTCs from Launch/X431 accessibility golden captures (Plan A fallback path).
 */
object LaunchAccessibilityGoldenPath {

    data class Snapshot(
        val dtcSummary: String,
        val storedCount: Int,
        val pendingCount: Int,
    )

    /** Agent-readable one-liner from a golden-path DTC snapshot. */
    fun formatSnapshot(snapshot: Snapshot): String = snapshot.dtcSummary

    /** Latest golden-capture DTC summary, or null when no readable OBD payload exists. */
    fun readDtcSummaryLineOrNull(context: Context): String? =
        readLatestDtcSnapshotOrNull(context)?.let(::formatSnapshot)

    fun readLatestDtcSnapshotOrNull(context: Context): Snapshot? {
        val file = GoldenCaptureStorage.eventsFile(context)
        if (!file.isFile || !file.canRead() || file.length() <= 0L) return null
        val rows = runCatching { GoldenLogParser.parse(file.readText()) }.getOrNull() ?: return null
        if (rows.isEmpty()) return null

        val stored = linkedSetOf<String>()
        val pending = linkedSetOf<String>()
        var sawObdPayload = false
        for (row in rows.asReversed()) {
            if (!row.dir.equals("RX", ignoreCase = true)) continue
            val obd = isoTpSingleFrameObdSlice(row.payload) ?: continue
            if (obd.isEmpty()) continue
            val service = obd[0].toInt() and 0xFF
            when (service) {
                0x43 -> {
                    sawObdPayload = true
                    runCatching { ObdDtcReader.parseStored(obd) }
                        .getOrDefault(emptyList())
                        .forEach { dtc -> stored.add(dtc.code) }
                }
                0x47 -> {
                    sawObdPayload = true
                    runCatching { ObdDtcReader.parsePending(obd) }
                        .getOrDefault(emptyList())
                        .forEach { dtc -> pending.add(dtc.code) }
                }
            }
            if (stored.isNotEmpty() || pending.isNotEmpty()) break
        }
        if (!sawObdPayload && stored.isEmpty() && pending.isEmpty()) return null

        val storedText = if (stored.isEmpty()) "none" else stored.joinToString(", ")
        val pendingText = if (pending.isEmpty()) "none" else pending.joinToString(", ")
        return Snapshot(
            dtcSummary = "Stored: $storedText; Pending: $pendingText",
            storedCount = stored.size,
            pendingCount = pending.size,
        )
    }

    private fun isoTpSingleFrameObdSlice(payloadRaw: String): ByteArray? {
        val cleaned = payloadRaw.filter { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }
        if (cleaned.length < 4 || cleaned.length % 2 != 0) return null
        val full = cleaned.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val pci = full[0].toInt() and 0xFF
        if (pci and 0xF0 != 0x00) return null
        val len = pci and 0x0F
        if (len <= 0 || 1 + len > full.size) return null
        return full.copyOfRange(1, 1 + len)
    }
}
