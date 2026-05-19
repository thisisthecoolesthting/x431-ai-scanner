package com.caseforge.scanner.report

import com.caseforge.scanner.data.DtcEntity
import com.caseforge.scanner.data.SessionEntity
import com.caseforge.scanner.engine.EngineState
import com.caseforge.scanner.engine.ScrapedDtc
import com.caseforge.scanner.engine.ScreenKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShopExportFormatterTest {

    private val sampleExport = ShopExport(
        vin = "1HGCM82633A004352",
        vehicleSummary = "2003 Honda Accord",
        timestampMs = 1_704_067_200_000L,
        transport = "ELM327 USB",
        dtcs = listOf(
            ShopExportDtcRow("P0301", "ECM", "Current", "Cylinder 1 misfire"),
            ShopExportDtcRow("P0171", "ECM", "Pending", "System too lean"),
        ),
        liveData = mapOf(
            "RPM" to "750",
            "Coolant" to "88",
        ),
        technicianNotes = "Customer reports rough idle at stoplights.",
        repairStoryText = "Replaced spark plug on cylinder 1; cleared codes and verified live data.",
        symptom = "Rough idle",
        rootCause = "Worn spark plug",
        recommendedRepair = "Replace spark plug cylinder 1",
        technicianName = "Ricky",
    )

    @Test
    fun plainText_includesAllSections() {
        val text = ShopExportFormatter.toPlainText(sampleExport)
        assertTrue(text.contains("1HGCM82633A004352"))
        assertTrue(text.contains("ELM327 USB"))
        assertTrue(text.contains("P0301"))
        assertTrue(text.contains("RPM: 750"))
        assertTrue(text.contains("TECHNICIAN NOTES"))
        assertTrue(text.contains("Customer reports rough idle"))
        assertTrue(text.contains("REPAIR STORY"))
        assertTrue(text.contains("Replaced spark plug"))
    }

    @Test
    fun plainText_omitsRepairStoryWhenBlank() {
        val text = ShopExportFormatter.toPlainText(sampleExport.copy(repairStoryText = null))
        assertFalse(text.contains("REPAIR STORY"))
    }

    @Test
    fun plainText_showsNotesPlaceholderWhenEmpty() {
        val text = ShopExportFormatter.toPlainText(sampleExport.copy(technicianNotes = ""))
        assertTrue(text.contains("(none)"))
    }

    @Test
    fun csv_containsMetaDtcAndLiveSections() {
        val csv = ShopExportFormatter.toCsv(sampleExport)
        assertTrue(csv.contains("section,meta"))
        assertTrue(csv.contains("vin,1HGCM82633A004352"))
        assertTrue(csv.contains("section,dtcs"))
        assertTrue(csv.contains("P0301,ECM,Current,Cylinder 1 misfire"))
        assertTrue(csv.contains("section,live_data"))
        assertTrue(csv.contains("RPM,750"))
        assertTrue(csv.contains("repair_story,"))
    }

    @Test
    fun csv_escapesCommasAndQuotes() {
        val row = ShopExportFormatter.csvRow("note", "said \"hi\", then left")
        assertEquals("note,\"said \"\"hi\"\", then left\"", row)
    }

    @Test
    fun fromEngineState_mapsDtcsAndLiveData() {
        val state = EngineState(
            screen = ScreenKind.FullScanResults,
            vehicleVin = "VIN123",
            vehicleSummary = "Test vehicle",
            dtcs = listOf(
                ScrapedDtc("P0420", "Catalyst efficiency", "ECM", "current"),
            ),
            liveData = mapOf("RPM" to 820.0),
            updatedAtMs = 1_000L,
        )
        val export = ShopExport.fromEngineState(state, transport = "OEM USB")
        assertEquals("VIN123", export.vin)
        assertEquals("OEM USB", export.transport)
        assertEquals(1, export.dtcs.size)
        assertEquals("P0420", export.dtcs.first().code)
        assertEquals("820", export.liveData["RPM"])
    }

    @Test
    fun fromSession_buildsRepairStoryFromFields() {
        val session = SessionEntity(
            id = 1L,
            vin = "VIN999",
            startedAt = 100L,
            endedAt = 200L,
            symptom = "No start",
            rootCause = "Dead battery",
            recommendedRepair = "Replace battery",
            transcriptJson = "[]",
        )
        val export = ShopExport.fromSession(
            session = session,
            dtcs = listOf(
                DtcEntity(sessionId = 1L, code = "P0562", module = "ECM", description = "Low voltage", status = "Current"),
            ),
            transport = ShopExport.transportLabel("elm327_bt"),
        )
        assertTrue(export.repairStoryText!!.contains("No start"))
        assertTrue(export.repairStoryText!!.contains("Dead battery"))
        assertEquals("ELM327 Bluetooth", export.transport)
    }

    @Test
    fun format_dispatchesByEnum() {
        val csv = ShopExportFormatter.format(sampleExport, ShopExportFormatter.Format.CSV)
        val text = ShopExportFormatter.format(sampleExport, ShopExportFormatter.Format.PLAIN_TEXT)
        assertTrue(csv.startsWith("section,meta"))
        assertTrue(text.contains("Diagnostic Export"))
    }
}
