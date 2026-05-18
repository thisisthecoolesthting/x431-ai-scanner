package com.caseforge.scanner.vci

import org.junit.Assert.assertEquals
import org.junit.Test

class CnlaunchAssetIndexTest {

    @Test
    fun parseDtcFile_mapsOemAndCode() {
        val raw = """{"OBD-II":{"P0301":"Cylinder 1 Misfire"}}"""
        val map = CnlaunchAssetIndex.parseDtcFile(raw)
        assertEquals("Cylinder 1 Misfire", map["OBD-II"]?.get("P0301"))
    }

    @Test
    fun parseMenuFile_mapsCapabilityPaths() {
        val raw = """{"read_dtcs":["A","B"]}"""
        val map = CnlaunchAssetIndex.parseMenuFile(raw)
        assertEquals(listOf("A", "B"), map["read_dtcs"])
    }

}
