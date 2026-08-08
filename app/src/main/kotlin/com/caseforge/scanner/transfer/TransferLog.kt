package com.caseforge.scanner.transfer

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class TransferLogEntry(val ts: Long, val stage: String, val message: String)

/**
 * Thread-safe ring buffer of the last [MAX_ENTRIES] transfer log entries.
 * Backed by a [StateFlow] so the UI can observe changes without polling.
 */
object TransferLog {

    private const val MAX_ENTRIES = 500

    private val _flow = MutableStateFlow<List<TransferLogEntry>>(emptyList())
    val flow: StateFlow<List<TransferLogEntry>> = _flow.asStateFlow()

    @Synchronized
    fun append(stage: String, message: String) {
        val entry = TransferLogEntry(System.currentTimeMillis(), stage, message)
        val current = _flow.value
        _flow.value = if (current.size >= MAX_ENTRIES) {
            current.subList(current.size - MAX_ENTRIES + 1, current.size) + entry
        } else {
            current + entry
        }
    }

    fun snapshot(): List<TransferLogEntry> = _flow.value

    @Synchronized
    fun clearAll() {
        _flow.value = emptyList()
    }

    fun isoTimestamp(ts: Long): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.US).format(Date(ts))

    fun formatEntry(e: TransferLogEntry): String =
        "[${isoTimestamp(e.ts)}] [${e.stage.padEnd(10)}] ${e.message}"

    fun allAsText(): String = snapshot().joinToString("\n") { formatEntry(it) }
}
