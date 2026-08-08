package com.caseforge.scanner.obd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ObdDtcParserTest {

    @Test
    fun parsesStoredP0133_followed_by_padding() {
        val raw = hb("43 01 33 00 00")
        val dtcs = ObdDtcReader.parseStored(raw)
        assertEquals(listOf(ObdDtc("P0133", pending = false)), dtcs)
    }

    @Test
    fun parsesPending_mirror_of_stored_prefix() {
        val raw = hb("47 01 33 00 00")
        val dtcs = ObdDtcReader.parsePending(raw)
        assertEquals(listOf(ObdDtc("P0133", pending = true)), dtcs)
    }

    @Test
    fun parsesMultipleStoredBefore_padding() {
        val raw = hb("43 01 33 04 71 01 74 00 00")
        val dtcs = ObdDtcReader.parseStored(raw)
        assertEquals(
            listOf(
                ObdDtc("P0133"),
                ObdDtc("P0471"),
                ObdDtc("P0174"),
            ),
            dtcs,
        )
    }

    @Test
    fun formatDtc_produces_four_hex_suffix() {
        assertEquals("P0133", ObdDtcReader.formatDtc(0x01, 0x33))
        assertEquals("C0123", ObdDtcReader.formatDtc(0x41, 0x23))
        assertEquals("B0ABC", ObdDtcReader.formatDtc(0x8A, 0xBC))
    }

    companion object {
        internal fun hb(hex: String): ByteArray =
            hex.trim().split(Regex("\\s+"))
                .filter { it.isNotEmpty() }
                .map { it.toInt(16).toByte() }
                .toByteArray()
    }
}
