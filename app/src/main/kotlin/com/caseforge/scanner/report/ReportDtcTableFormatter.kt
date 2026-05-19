package com.caseforge.scanner.report

import com.caseforge.scanner.engine.ScrapedDtc
import java.util.Locale

/**
 * Tab-separated DTC table for clipboard / shop paste. Pure Kotlin — no Android APIs.
 */
object ReportDtcTableFormatter {

    fun formatForClipboard(dtcs: List<ScrapedDtc>): String {
        if (dtcs.isEmpty()) return "No diagnostic trouble codes recorded."
        val lines = buildList {
            add("CODE\tMODULE\tSTATUS\tDESCRIPTION")
            dtcs.forEach { row ->
                add(
                    listOf(
                        row.code,
                        row.module.orEmpty(),
                        row.status.orEmpty(),
                        row.description.orEmpty(),
                    ).joinToString("\t"),
                )
            }
        }
        return lines.joinToString("\n")
    }

    /** Monospace-friendly fixed-width preview (optional UI hint). */
    fun formatAligned(dtcs: List<ScrapedDtc>): String {
        if (dtcs.isEmpty()) return "No diagnostic trouble codes recorded."
        val header = String.format(Locale.US, "%-8s %-14s %-10s %s", "CODE", "MODULE", "STATUS", "DESCRIPTION")
        val rows = dtcs.map { row ->
            String.format(
                Locale.US,
                "%-8s %-14s %-10s %s",
                row.code,
                (row.module ?: "-").take(14),
                (row.status ?: "-").take(10),
                row.description ?: "-",
            )
        }
        return (listOf(header) + rows).joinToString("\n")
    }
}
