package com.caseforge.scanner.transfer

import android.content.Context
import java.io.File

/**
 * App-private golden log lines written by the agent ([append_golden_event]).
 * Included in LAN upload zips under [ZIP_ENTRY] alongside `vehicle-database/` and `harvest-batch/`.
 */
object GoldenCaptureStorage {

    const val DIR_NAME = "golden_capture"
    private const val EVENTS_FILE = "golden_events.jsonl"

    /** Zip path used by [VehicleDatabaseZipper] sidecars */
    const val ZIP_ENTRY = "tcw-golden-capture/golden_events.jsonl"

    fun eventsFile(context: Context): File {
        val dir = File(context.filesDir, DIR_NAME).also { it.mkdirs() }
        return File(dir, EVENTS_FILE)
    }

    fun zipSidecarsIfPresent(context: Context): Map<String, ByteArray> {
        val f = eventsFile(context)
        if (!f.isFile || !f.canRead() || f.length() == 0L) return emptyMap()
        return mapOf(ZIP_ENTRY to f.readBytes())
    }
}
