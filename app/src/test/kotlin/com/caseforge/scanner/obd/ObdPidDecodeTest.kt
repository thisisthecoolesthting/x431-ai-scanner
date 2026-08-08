package com.caseforge.scanner.obd

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ObdPidDecodeTest {

    @Test
    fun rpm_0x1A0xF8_decodes_SA_formula() {
        val raw = hb("41 0C 1A F8")
        assertEquals(1726, ObdLivePidReader.decodeRpmResponse(raw))
    }

    @Test
    fun coolant_0x64_is_60C() {
        val raw = hb("41 05 64")
        assertEquals(60, ObdLivePidReader.decodeCoolantResponse(raw))
    }

    @Test
    fun speed_0x3C_is_60kmh() {
        val raw = hb("41 0D 3C")
        assertEquals(60, ObdLivePidReader.decodeSpeedResponse(raw))
    }

    @Test
    fun rejects_wrong_service_or_pid() {
        assertNull(ObdLivePidReader.decodeRpmResponse(hb("42 0C 1A F8")))
        assertNull(ObdLivePidReader.decodeRpmResponse(hb("41 0D 3C")))
    }

    @Test
    fun vin_reassembled_three_byte_header_then_ascii17() {
        val ascii = "1HGBH41JXMN109186".toByteArray(Charsets.US_ASCII)
        assertEquals(17, ascii.size)
        val payload = hb("49 02 01").plus(ascii)
        assertEquals(
            "1HGBH41JXMN109186",
            ObdVinReader.parseFromReassembled(payload),
        )
    }

    @Test
    fun vin_isoTp_three_frames_FF_and_two_CF() {
        // OBD ISO-TP payload: 49 02 01 + 17× VIN ASCII = 20 bytes.
        val ff = pad8(byteArrayOf(0x10, 0x14, 0x49, 0x02, 0x01, 0x31, 0x48, 0x47))
        val cf1 = pad8(byteArrayOf(0x21, 0x42, 0x48, 0x34, 0x31, 0x4A, 0x58, 0x4D))
        val cf2 = pad8(byteArrayOf(0x22, 0x4E, 0x31, 0x30, 0x39, 0x31, 0x38, 0x36))

        val expectedObd =
            hb("49 02 01 31 48 47 42 48 34 31 4A 58 4D 4E 31 30 39 31 38 36")

        assertArrayEquals(expectedObd, ObdVinReader.reassembleIsoTp(listOf(ff, cf1, cf2)))
        assertEquals(
            "1HGBH41JXMN109186",
            ObdVinReader.parseFromIsoTpFrames(listOf(ff, cf1, cf2)),
        )
    }

    @Test
    fun isoTpAssembler_returns_obd_when_buffer_complete() {
        val ff = pad8(byteArrayOf(0x10, 0x14, 0x49, 0x02, 0x01, 0x31, 0x48, 0x47))
        val cf1 = pad8(byteArrayOf(0x21, 0x42, 0x48, 0x34, 0x31, 0x4A, 0x58, 0x4D))
        val cf2 = pad8(byteArrayOf(0x22, 0x4E, 0x31, 0x30, 0x39, 0x31, 0x38, 0x36))

        val asm = ObdVinReader.newRxAssembler()
        assertEquals(null, asm.appendFrame(ff))
        assertEquals(null, asm.appendFrame(cf1))
        val done = asm.appendFrame(cf2)!!
        assertEquals(
            "1HGBH41JXMN109186",
            ObdVinReader.parseFromReassembled(done),
        )
    }

    companion object {
        internal fun hb(hex: String): ByteArray =
            hex.trim().replace("\n", " ")
                .split(Regex("\\s+"))
                .filter { it.isNotEmpty() }
                .map { it.toInt(16).toByte() }
                .toByteArray()

        internal fun pad8(frame: ByteArray): ByteArray {
            require(frame.size <= 8)
            if (frame.size == 8) return frame.copyOf(frame.size)
            val out = ByteArray(8)
            frame.copyInto(out)
            return out
        }
    }
}
