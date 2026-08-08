package com.caseforge.scanner.obd.j1850

import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse

class J1850FrameTest {

    @Test
    fun `CRC-8 SAE-J1850 known-answer test`() {
        // Standard check value for ASCII "123456789" under CRC-8/SAE-J1850
        // (poly 0x1D, init 0xFF, xorout 0xFF, no reflection).
        val bytes = "123456789".map { it.code }
        assertEquals(0x4B, J1850Crc.compute(bytes))
    }

    @Test
    fun `fromBytes splits header data and trailing crc`() {
        val frame = J1850Frame.fromBytes(listOf(0xB1, 0x01, 0x4B), headerLength = 1)
        assertEquals(listOf(0xB1), frame?.header)
        assertEquals(listOf(0x01), frame?.data)
        assertEquals(0x4B, frame?.crc)
    }

    @Test
    fun `fromBytes with only a header byte has null crc and empty data`() {
        val frame = J1850Frame.fromBytes(listOf(0xB1), headerLength = 1)
        assertEquals(emptyList<Int>(), frame?.data)
        assertNull(frame?.crc)
    }

    @Test
    fun `fromBytes returns null when shorter than the header itself`() {
        val frame = J1850Frame.fromBytes(listOf(0x48, 0x6B), headerLength = 3)
        assertNull(frame)
    }

    @Test
    fun `pciId is only exposed for one-byte headers, target-source for three-byte headers`() {
        val oneByte = J1850Frame(header = listOf(0xB1), data = listOf(0x01), crc = 0x4B)
        assertEquals(0xB1, oneByte.pciId)
        assertNull(oneByte.targetAddress)

        val threeByte = J1850Frame(header = listOf(0x48, 0x6B, 0x10), data = listOf(0x41, 0x00), crc = null)
        assertNull(threeByte.pciId)
        assertEquals(0x6B, threeByte.targetAddress)
        assertEquals(0x10, threeByte.sourceAddress)
    }

    @Test
    fun `isCrcValid true for a correctly computed crc`() {
        val header = listOf(0xB1)
        val data = listOf(0x01)
        val crc = J1850Crc.compute(header + data)
        val frame = J1850Frame(header = header, data = data, crc = crc)
        assertTrue(frame.isCrcValid)
    }

    @Test
    fun `isCrcValid false for a wrong crc`() {
        val header = listOf(0xB1)
        val data = listOf(0x01)
        val correct = J1850Crc.compute(header + data)
        val frame = J1850Frame(header = header, data = data, crc = correct xor 0xFF)
        assertFalse(frame.isCrcValid)
    }

    @Test
    fun `isCrcValid false when crc is absent`() {
        val frame = J1850Frame(header = listOf(0xB1), data = listOf(0x01), crc = null)
        assertFalse(frame.isCrcValid)
    }

    @Test
    fun `parseElmHexText handles multiple CR separated frames without spaces`() {
        val f1 = listOf(0xB1, 0x01, J1850Crc.compute(listOf(0xB1, 0x01)))
        val f2 = listOf(0x70, 0x41, 0x42, J1850Crc.compute(listOf(0x70, 0x41, 0x42)))
        val text = f1.joinToString("") { "%02X".format(it) } + "\r" + f2.joinToString("") { "%02X".format(it) }

        val frames = J1850Frame.parseElmHexText(text)

        assertEquals(2, frames.size)
        assertEquals(0xB1, frames[0].pciId)
        assertEquals(0x70, frames[1].pciId)
    }

    @Test
    fun `parseElmHexText tolerates spaced hex too`() {
        val frames = J1850Frame.parseElmHexText("B1 01 4B")
        assertEquals(1, frames.size)
        assertEquals(0xB1, frames[0].pciId)
        assertEquals(listOf(0x01), frames[0].data)
    }

    @Test
    fun `parseElmHexText ignores non-hex status lines`() {
        val frames = J1850Frame.parseElmHexText("SEARCHING...")
        assertTrue(frames.isEmpty())
    }

    @Test
    fun `parseElmHexText skips odd-length garbage`() {
        val frames = J1850Frame.parseElmHexText("B10")
        assertTrue(frames.isEmpty())
    }
}
