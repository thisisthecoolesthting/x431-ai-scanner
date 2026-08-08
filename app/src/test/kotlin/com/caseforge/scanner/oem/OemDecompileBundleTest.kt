package com.caseforge.scanner.oem

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class OemDecompileBundleTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun parsesMinimalInlineJson() {
        val raw =
            """{"bundleVersion":1,"generatedAt":"2026-01-01T00:00:00Z","summary":{"brandCount":2,"fileCount":10,"totalBytes":100,"catalogTotals":{"ggp":1,"bin":2,"bnc":3}},"formatHints":{"ggp":"Proprietary"},"sqliteDatabases":[],"brands":[{"brand":"AUDI","versionDirs":["V1"],"onLine":"1","catalogCounts":{"ggp":0,"bin":0,"bnc":0},"fileCount":1,"totalBytes":2}]}"""

        val b = json.decodeFromString<OemDecompileBundle>(raw)
        assertEquals(1, b.bundleVersion)
        assertEquals(2, b.summary.brandCount)
        assertEquals(1, b.summary.catalogTotals.ggp)
        assertEquals(1, b.brands.size)
        assertEquals("AUDI", b.brands[0].brand)
    }
}
