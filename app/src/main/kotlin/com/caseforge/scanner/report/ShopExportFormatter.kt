package com.caseforge.scanner.report

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Renders [ShopExport] as CSV or plain text for shop systems, email, or file share.
 * Pure Kotlin — no PDF, no Android APIs.
 */
object ShopExportFormatter {

    enum class Format {
        CSV,
        PLAIN_TEXT,
    }

    fun format(export: ShopExport, format: Format): String = when (format) {
        Format.CSV -> toCsv(export)
        Format.PLAIN_TEXT -> toPlainText(export)
    }

    fun toCsv(export: ShopExport): String {
        val lines = mutableListOf<String>()
        lines += csvRow("section", "meta")
        lines += csvRow("shop", export.shopName)
        export.technicianName?.takeIf { it.isNotBlank() }?.let { lines += csvRow("technician", it) }
        lines += csvRow("vin", export.vin ?: "")
        lines += csvRow("vehicle", export.vehicleSummary ?: "")
        lines += csvRow("timestamp", formatTimestampIso(export.timestampMs))
        lines += csvRow("transport", export.transport ?: "")
        export.symptom?.takeIf { it.isNotBlank() }?.let { lines += csvRow("symptom", it) }
        export.rootCause?.takeIf { it.isNotBlank() }?.let { lines += csvRow("root_cause", it) }
        export.recommendedRepair?.takeIf { it.isNotBlank() }?.let { lines += csvRow("recommended_repair", it) }
        lines += csvRow("technician_notes", export.technicianNotes)
        export.repairStoryText?.takeIf { it.isNotBlank() }?.let { lines += csvRow("repair_story", it) }

        lines += ""
        lines += csvRow("section", "dtcs")
        lines += csvRow("code", "module", "status", "description")
        if (export.dtcs.isEmpty()) {
            lines += csvRow("", "", "", "No diagnostic trouble codes recorded.")
        } else {
            export.dtcs.forEach { row ->
                lines += csvRow(
                    row.code,
                    row.module ?: "",
                    row.status ?: "",
                    row.description ?: "",
                )
            }
        }

        lines += ""
        lines += csvRow("section", "live_data")
        lines += csvRow("pid", "value")
        if (export.liveData.isEmpty()) {
            lines += csvRow("", "No live data snapshot.")
        } else {
            export.liveData.forEach { (pid, value) ->
                lines += csvRow(pid, value)
            }
        }

        return lines.joinToString("\n").trimEnd() + "\n"
    }

    fun toPlainText(export: ShopExport): String = buildString {
        appendLine(export.shopName.uppercase(Locale.US))
        appendLine("Diagnostic Export")
        appendLine(formatTimestampHuman(export.timestampMs))
        appendLine(repeatChar('=', 48))
        appendLine()

        appendLine("VEHICLE")
        appendLine("  VIN:       ${export.vin ?: "-"}")
        appendLine("  Vehicle:   ${export.vehicleSummary ?: "-"}")
        appendLine("  Transport: ${export.transport ?: "-"}")
        export.technicianName?.takeIf { it.isNotBlank() }?.let {
            appendLine("  Technician: $it")
        }
        appendLine()

        export.symptom?.takeIf { it.isNotBlank() }?.let {
            appendLine("SYMPTOM")
            appendLine(wrapIndent(it))
            appendLine()
        }
        export.rootCause?.takeIf { it.isNotBlank() }?.let {
            appendLine("ROOT CAUSE")
            appendLine(wrapIndent(it))
            appendLine()
        }
        export.recommendedRepair?.takeIf { it.isNotBlank() }?.let {
            appendLine("RECOMMENDED REPAIR")
            appendLine(wrapIndent(it))
            appendLine()
        }

        appendLine("DIAGNOSTIC TROUBLE CODES")
        appendLine(repeatChar('-', 48))
        if (export.dtcs.isEmpty()) {
            appendLine("  No diagnostic trouble codes recorded.")
        } else {
            appendLine(String.format(Locale.US, "  %-8s %-14s %-10s %s", "CODE", "MODULE", "STATUS", "DESCRIPTION"))
            export.dtcs.forEach { row ->
                appendLine(
                    String.format(
                        Locale.US,
                        "  %-8s %-14s %-10s %s",
                        row.code,
                        (row.module ?: "-").take(14),
                        (row.status ?: "-").take(10),
                        row.description ?: "-",
                    ),
                )
            }
        }
        appendLine()

        appendLine("LIVE DATA SNAPSHOT")
        appendLine(repeatChar('-', 48))
        if (export.liveData.isEmpty()) {
            appendLine("  No live data snapshot.")
        } else {
            export.liveData.forEach { (pid, value) ->
                appendLine("  $pid: $value")
            }
        }
        appendLine()

        appendLine("TECHNICIAN NOTES")
        appendLine(repeatChar('-', 48))
        val notes = export.technicianNotes.ifBlank { "(none)" }
        appendLine(wrapIndent(notes))
        appendLine()

        export.repairStoryText?.takeIf { it.isNotBlank() }?.let { story ->
            appendLine("REPAIR STORY")
            appendLine(repeatChar('-', 48))
            appendLine(wrapIndent(story))
            appendLine()
        }

        appendLine(repeatChar('=', 48))
        append("Generated ${formatTimestampHuman(export.timestampMs)}")
    }.trimEnd() + "\n"

    // ---- CSV helpers -------------------------------------------------------

    internal fun csvRow(vararg fields: String): String =
        fields.joinToString(",") { escapeCsvField(it) }

    internal fun escapeCsvField(raw: String): String {
        if (raw.none { it == ',' || it == '"' || it == '\n' || it == '\r' }) return raw
        return "\"${raw.replace("\"", "\"\"")}\""
    }

    // ---- Timestamp helpers -------------------------------------------------

    private fun formatTimestampIso(epochMs: Long): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        fmt.timeZone = TimeZone.getTimeZone("UTC")
        return fmt.format(Date(epochMs))
    }

    private fun formatTimestampHuman(epochMs: Long): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm z", Locale.US)
        return fmt.format(Date(epochMs))
    }

    private fun wrapIndent(text: String): String =
        text.lines().joinToString("\n") { "  $it" }

    private fun repeatChar(ch: Char, count: Int): String =
        ch.toString().repeat(count)
}
