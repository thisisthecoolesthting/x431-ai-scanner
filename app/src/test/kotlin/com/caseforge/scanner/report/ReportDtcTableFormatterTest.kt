package com.caseforge.scanner.report

import com.caseforge.scanner.engine.ScrapedDtc
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportDtcTableFormatterTest {

    @Test
    fun formatForClipboard_empty() {
        assertEquals(
            "No diagnostic trouble codes recorded.",
            ReportDtcTableFormatter.formatForClipboard(emptyList()),
        )
    }

    @Test
    fun formatForClipboard_tabs() {
        val body = ReportDtcTableFormatter.formatForClipboard(
            listOf(
                ScrapedDtc("P0301", module = "ECM", status = "current", description = "Cylinder 1 misfire"),
            ),
        )
        assertTrue(body.startsWith("CODE\tMODULE\tSTATUS\tDESCRIPTION"))
        assertTrue(body.contains("P0301\tECM\tcurrent\tCylinder 1 misfire"))
    }
}
