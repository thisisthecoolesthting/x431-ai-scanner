package com.caseforge.scanner.planb.golden

import org.junit.Assert.assertEquals
import org.junit.Test

class GoldenLogParserTest {

    @Test
    fun parse_bundled_jeep_jl_read_dtcs_golden() {
        val text =
            GoldenLogParserTest::class.java.getResourceAsStream("/golden/jeep-jl-read_dtcs-20260520.jsonl")!!
                .bufferedReader()
                .use { it.readText() }

        val lines = GoldenLogParser.parse(text)
        assertEquals(12, lines.size)

        assertEquals("2026-05-20T18:00:00.000Z", lines.first().ts)
        assertEquals("TX", lines.first().dir)
        assertEquals("0x7E0", lines.first().canId)
        assertEquals("0203000000000000", lines.first().payload)
        assertEquals("User clicks Read DTCs", lines.first().uiContext)

        val summary = GoldenFrameSummary.summarize(lines)
        assertEquals(12, summary.totalFrames)
        assertEquals(6, summary.txFrames)
        assertEquals(6, summary.rxFrames)
        assertEquals(2, summary.uniqueCanIds)
        assertEquals(6, summary.framesByCanId["0x7E0"])
        assertEquals(6, summary.framesByCanId["0x7E8"])
    }
}
