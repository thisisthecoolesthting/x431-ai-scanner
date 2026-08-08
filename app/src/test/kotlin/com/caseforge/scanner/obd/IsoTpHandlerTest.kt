package com.caseforge.scanner.obd

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IsoTpHandlerTest {

    @Test
    fun singleFrame_assemble_payload() {
        val h = IsoTpHandler()
        // SF, DL=3, payload 01 02 03; rest padding ignored by parser
        val canData = byteArrayOf(
            0x03,
            0x01,
            0x02,
            0x03,
            IsoTp15765.PADDING_BYTE,
            IsoTp15765.PADDING_BYTE,
            IsoTp15765.PADDING_BYTE,
            IsoTp15765.PADDING_BYTE,
        )
        when (val r = h.ingest(canData)) {
            is IsoTpRxResult.Complete -> assertArrayEquals(byteArrayOf(0x01, 0x02, 0x03), r.payload)
            else -> error("expected Complete got $r")
        }
    }

    @Test
    fun singleFrame_transmit_buildsPciAndPadding() {
        val h = IsoTpHandler()
        val payload = byteArrayOf(0x41.toByte(), 0x00)
        val frame = h.buildSingleFrame(payload)
        assertEquals(8, frame.size)
        assertEquals(0x02.toByte(), frame[0])
        assertEquals(0x41.toByte(), frame[1])
        assertEquals(0x00.toByte(), frame[2])
        for (i in 3 until 8) {
            assertEquals(IsoTp15765.PADDING_BYTE, frame[i])
        }
    }
}
