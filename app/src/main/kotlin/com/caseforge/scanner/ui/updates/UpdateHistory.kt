package com.caseforge.scanner.ui.updates

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class UpdateHistoryEntry(
    val versionName: String,
    val versionCode: Int,
    val sha: String,
    val timestampMs: Long,
    val downloadBytes: Long,
) {
    fun formattedTime(): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).apply {
            timeZone = TimeZone.getDefault()
        }
        return fmt.format(Date(timestampMs))
    }
}

object UpdateHistory {
    private const val FILE_NAME = "update-history.json"
    private const val MAX_ENTRIES = 10

    fun load(context: Context): List<UpdateHistoryEntry> {
        val file = historyFile(context)
        if (!file.exists()) return emptyList()
        return try {
            val arr = JSONArray(file.readText())
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(
                        UpdateHistoryEntry(
                            versionName = o.optString("versionName", "—"),
                            versionCode = o.optInt("versionCode", 0),
                            sha = o.optString("sha", "—"),
                            timestampMs = o.optLong("timestampMs", 0L),
                            downloadBytes = o.optLong("downloadBytes", 0L),
                        ),
                    )
                }
            }.sortedByDescending { it.timestampMs }.take(MAX_ENTRIES)
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun append(
        context: Context,
        versionName: String,
        versionCode: Int,
        sha: String,
        downloadBytes: Long,
    ) {
        val existing = load(context).toMutableList()
        existing.add(
            0,
            UpdateHistoryEntry(
                versionName = versionName,
                versionCode = versionCode,
                sha = sha,
                timestampMs = System.currentTimeMillis(),
                downloadBytes = downloadBytes,
            ),
        )
        val trimmed = existing.take(MAX_ENTRIES)
        val arr = JSONArray()
        for (entry in trimmed) {
            arr.put(
                JSONObject()
                    .put("versionName", entry.versionName)
                    .put("versionCode", entry.versionCode)
                    .put("sha", entry.sha)
                    .put("timestampMs", entry.timestampMs)
                    .put("downloadBytes", entry.downloadBytes),
            )
        }
        historyFile(context).writeText(arr.toString())
    }

    private fun historyFile(context: Context): File =
        File(context.filesDir, FILE_NAME)
}
