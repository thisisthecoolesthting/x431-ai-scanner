package com.caseforge.scanner.transfer

import android.content.Context
import java.io.File
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Append-only session event log under `filesDir/sessions/<sessionId>/session_events.jsonl`.
 * Aggregated copy is included in LAN harvest zips at [ZIP_ENTRY].
 */
object SessionEventLogger {

    const val EVENTS_FILE = "session_events.jsonl"
    const val ZIP_ENTRY = "tcw-session-log/session_events.jsonl"

    private val json = Json { encodeDefaults = true }

    fun sessionEventsFile(context: Context, sessionId: String): File {
        val dir = File(context.filesDir, "sessions/$sessionId").also { it.mkdirs() }
        return File(dir, EVENTS_FILE)
    }

    fun log(
        context: Context,
        sessionId: String,
        kind: String,
        detail: String = "",
        extra: Map<String, String> = emptyMap(),
    ) {
        val line = buildJsonObject {
            put("ts", System.currentTimeMillis())
            put("sessionId", sessionId)
            put("kind", kind)
            if (detail.isNotBlank()) put("detail", detail)
            extra.forEach { (k, v) -> put(k, v) }
        }
        val file = sessionEventsFile(context, sessionId)
        file.appendText(json.encodeToString(line) + "\n")
        refreshAggregatedCopy(context)
    }

    /** Merges all per-session jsonl files into one harvest sidecar. */
    fun refreshAggregatedCopy(context: Context) {
        val sessionsRoot = File(context.filesDir, "sessions")
        if (!sessionsRoot.isDirectory) return
        val agg = aggregatedFile(context)
        agg.parentFile?.mkdirs()
        val lines = buildList {
            sessionsRoot.listFiles()?.filter { it.isDirectory }?.forEach { dir ->
                val f = File(dir, EVENTS_FILE)
                if (f.isFile && f.length() > 0L) {
                    f.readLines().filter { it.isNotBlank() }.forEach { add(it) }
                }
            }
        }
        if (lines.isEmpty()) {
            agg.delete()
        } else {
            agg.writeText(lines.joinToString("\n", postfix = "\n"))
        }
    }

    fun aggregatedFile(context: Context): File =
        File(context.filesDir, "tcw-session-log/$EVENTS_FILE")

    fun zipSidecarsIfPresent(context: Context): Map<String, ByteArray> {
        refreshAggregatedCopy(context)
        val f = aggregatedFile(context)
        if (!f.isFile || !f.canRead() || f.length() == 0L) return emptyMap()
        return mapOf(ZIP_ENTRY to f.readBytes())
    }
}
