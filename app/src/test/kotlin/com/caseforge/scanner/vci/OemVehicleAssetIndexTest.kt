package com.caseforge.scanner.vci

import org.junit.Assert.assertEquals
import org.junit.Test

class OemVehicleAssetIndexTest {

    @Test
    fun parseDtcFile_mapsCodes() {
        val raw = """{"OBD-II":{"P0300":"Random misfire"}}"""
        val map = OemVehicleAssetIndex.parseDtcFile(raw)
        assertEquals("Random misfire", map["OBD-II"]?.get("P0300"))
    }

    @Test
    fun parseMenuFile_mapsPaths() {
        val raw = """{"abs_scan":["ABS","Read DTCs"]}"""
        val map = OemVehicleAssetIndex.parseMenuFile(raw)
        assertEquals(listOf("ABS", "Read DTCs"), map["abs_scan"])
    }
}
