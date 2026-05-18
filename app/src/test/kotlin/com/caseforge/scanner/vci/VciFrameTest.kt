package com.caseforge.scanner.vci

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VciFrameTest {

    @Test
    fun `computeChecksum XORs opcode length and payload`() {
        val opcode = 0x0103
        val payload = byteArrayOf(0x03)
        val checksum = VciFrame.computeChecksum(opcode, payload)

        val frame = VciFrame.build(opcode, payload)
        assertEquals(checksum, frame.checksum)
        assertTrue(frame.isChecksumValid)
    }

    @Test
    fun `encode decode roundtrip preserves opcode and payload`() {
        val opcode = 0x0101
        val payload = byteArrayOf(0x0C.toByte())
        val built = VciFrame.build(opcode, payload, header = VciFrame.DEFAULT_HEADER)

        val raw = built.encode()
        val decoded = VciFrame.decode(raw)

        assertTrue(decoded is VciFrame.DecodeResult.Ok)
        val frame = (decoded as VciFrame.DecodeResult.Ok).frame
        assertArrayEquals(VciFrame.DEFAULT_HEADER, frame.header)
        assertEquals(opcode, frame.opcode)
        assertArrayEquals(payload, frame.payload)
        assertTrue(frame.isChecksumValid)
    }

    @Test
    fun `decodeHex roundtrips through hex line encoding`() {
        val built = VciFrame.build(opcode = 0x0104, payload = ByteArray(0))
        val hexLine = built.encodeHex()

        val decoded = VciFrame.decodeHex(hexLine)
        assertTrue(decoded is VciFrame.DecodeResult.Ok)
        val frame = (decoded as VciFrame.DecodeResult.Ok).frame
        assertEquals(0x0104, frame.opcode)
        assertTrue(frame.payload.isEmpty())
    }

    @Test
    fun `HEADER_CANDIDATES has four entries`() {
        assertEquals(4, VciFrame.HEADER_CANDIDATES.size)
        assertEquals(4, VciProtocolConfig.HEADER_CANDIDATES.size)
    }

    @Test
    fun `DEFAULT_HEADER is 0x55 0xAA`() {
        assertArrayEquals(
            byteArrayOf(0x55.toByte(), 0xAA.toByte()),
            VciFrame.DEFAULT_HEADER,
        )
    }
}
