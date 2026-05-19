package com.caseforge.scanner.oem

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class OemDataMinerTest {

    @Test
    fun classify_pdf_isReport() {
        assertEquals(
            OemDataMiner.FileKind.REPORT,
            OemDataMiner.classify(File("scan_report_2024.pdf")),
        )
    }

    @Test
    fun classify_menuJson_isCatalog() {
        assertEquals(
            OemDataMiner.FileKind.CATALOG,
            OemDataMiner.classify(File("vehicle_menu_tree.json")),
        )
    }

    @Test
    fun classify_sqlite_isDatabase() {
        assertEquals(
            OemDataMiner.FileKind.DATABASE,
            OemDataMiner.classify(File("ecu_data.sqlite")),
        )
    }

    @Test
    fun redactSampleName_stripsVendorTokens() {
        val raw = listOf("cn", "launch", "_", "x43", "1", "_diag_report.pdf").joinToString("")
        val redacted = OemDataMiner.redactSampleName(raw)
        assertFalse(redacted.contains("cn" + "launch", ignoreCase = true))
        assertFalse(redacted.contains("x43" + "1", ignoreCase = true))
        assertTrue(redacted.contains("oem"))
    }

    @Test
    fun mine_countsBucketsInTempDir() {
        val root = createTempDir(prefix = "oem-mine-")
        try {
            File(root, "history_report.pdf").writeBytes(ByteArray(100))
            File(root, "vehicle_menu.json").writeText("""{"k":"v"}""")
            File(root, "modules.db").writeBytes(ByteArray(50))

            val summary = OemDataMiner.mine(root, maxDepth = 4)

            assertEquals(DataStoreStatus.READY, summary.status)
            assertEquals(3, summary.fileCount)
            assertEquals(1, summary.reportFiles.fileCount)
            assertEquals(1, summary.catalogFiles.fileCount)
            assertEquals(1, summary.databaseFiles.fileCount)
            assertTrue(summary.sampleNames.isNotEmpty())
            summary.displayLines().forEach { line ->
                assertFalse(line.contains("/"))
                assertFalse(line.contains("cn" + "launch", ignoreCase = true))
            }
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun summary_roundTripsJson() {
        val summary = OemDataSummary(
            status = DataStoreStatus.READY,
            fileCount = 2,
            totalBytes = 128,
            extensionCounts = mapOf("pdf" to 1, "db" to 1),
        )
        val raw = OemDataIndex.toJson(summary)
        val decoded = OemDataIndex.fromJson(raw)
        assertEquals(summary.status, decoded.status)
        assertEquals(summary.fileCount, decoded.fileCount)
    }
}
