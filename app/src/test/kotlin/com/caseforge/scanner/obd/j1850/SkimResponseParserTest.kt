package com.caseforge.scanner.obd.j1850

import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse

/**
 * Synthetic J1850 VPW / Chrysler PCI-bus fixtures for SkimResponseParser.
 *
 * Every fixture is built with [frameHex] so the trailing CRC byte is
 * always a real CRC-8/SAE-J1850 of header+data (computed by the same
 * J1850Crc object the production parser uses), not a hand-typed guess -
 * this keeps the fixtures internally consistent with real ELM327 ATH1
 * output (see J1850Frame.kt) even though the PCI-ID/byte-layout choices
 * themselves are the PROVISIONAL ones documented in SkimVpwReader.kt.
 *
 * The VIN used below ("1J4GR48K05C123456") is a fabricated, plausible-
 * looking string for a 2005 Jeep Grand Cherokee - it is NOT from a real
 * vehicle capture, purely a fixture to exercise multi-frame reassembly.
 */
class SkimResponseParserTest {

    /** Builds one on-the-wire frame's hex text: header+data+CRC, no spaces (matches ATS0). */
    private fun frameHex(header: List<Int>, data: List<Int>): String {
        val bytes = header + data
        val crc = J1850Crc.compute(bytes)
        return (bytes + crc).joinToString("") { "%02X".format(it) }
    }

    // ---- (1) module-present successful read ---------------------------------

    @Test
    fun `module present successful read reports armed status`() {
        val raw = frameHex(header = listOf(0xB1), data = listOf(0x01)) // 0x01 -> ARMED, see SkimResponseParser.describeStatus

        val result = SkimResponseParser.parse(raw)

        assertEquals(SkimReadOutcome.MODULE_PRESENT, result.outcome)
        assertTrue(result.modulePresent)
        assertEquals("ARMED", result.immobilizerStatus)
        assertNull(result.keyCount)
        assertNull(result.vinEcho)
        assertEquals(raw, result.rawHex)
    }

    @Test
    fun `module present read with a second data byte is exposed as keyCount`() {
        val raw = frameHex(header = listOf(0xB1), data = listOf(0x00, 0x02)) // status=DISARMED, provisional keyCount=2

        val result = SkimResponseParser.parse(raw)

        assertEquals(SkimReadOutcome.MODULE_PRESENT, result.outcome)
        assertEquals("DISARMED_KEY_VALID", result.immobilizerStatus)
        assertEquals(2, result.keyCount)
    }

    @Test
    fun `unrecognized status byte falls back to a labelled unknown code, not a false positive`() {
        val raw = frameHex(header = listOf(0xB1), data = listOf(0x7E))

        val result = SkimResponseParser.parse(raw)

        assertEquals(SkimReadOutcome.MODULE_PRESENT, result.outcome)
        assertEquals("UNKNOWN_CODE(0x7E)", result.immobilizerStatus)
    }

    // ---- (2) no-response / negative case -------------------------------------

    @Test
    fun `NO DATA token is a negative no-response result`() {
        val result = SkimResponseParser.parse("NO DATA")

        assertEquals(SkimReadOutcome.NO_RESPONSE, result.outcome)
        assertFalse(result.modulePresent)
        assertEquals("NO_RESPONSE", result.immobilizerStatus)
        assertNull(result.keyCount)
        assertNull(result.vinEcho)
    }

    @Test
    fun `UNABLE TO CONNECT token is a negative no-response result`() {
        val result = SkimResponseParser.parse("UNABLE TO CONNECT")

        assertEquals(SkimReadOutcome.NO_RESPONSE, result.outcome)
        assertFalse(result.modulePresent)
    }

    @Test
    fun `blank response is treated as no-response`() {
        val result = SkimResponseParser.parse("")

        assertEquals(SkimReadOutcome.NO_RESPONSE, result.outcome)
        assertFalse(result.modulePresent)
    }

    @Test
    fun `response from a different module is treated as no usable response`() {
        // Some other module's frame went by, but not SKIM's (0xB1) - e.g.
        // PCI ID 0xCC ("outside air temperature" per the same PCM ROM table).
        val raw = frameHex(header = listOf(0xCC), data = listOf(0x2A, 0x01, 0x00))

        val result = SkimResponseParser.parse(raw)

        assertEquals(SkimReadOutcome.NO_RESPONSE, result.outcome)
        assertFalse(result.modulePresent)
    }

    // ---- (3) VIN echo parse ---------------------------------------------------

    @Test
    fun `VIN echo is reassembled across multiple frames`() {
        val vin = "1J4GR48K05C123456"
        check(vin.length == 17)
        val chunk1 = vin.substring(0, 6)
        val chunk2 = vin.substring(6, 12)
        val chunk3 = vin.substring(12, 17)

        val statusFrame = frameHex(header = listOf(0xB1), data = listOf(0x00))
        // SYNTHETIC/TEST-ONLY PCI IDs (0x70/0x71/0x72) - not a researched
        // real assignment, chosen only so this fixture has distinct frame
        // headers to iterate over; see class doc for VIN fixture provenance.
        val vinFrame1 = frameHex(header = listOf(0x70), data = chunk1.map { it.code })
        val vinFrame2 = frameHex(header = listOf(0x71), data = chunk2.map { it.code })
        val vinFrame3 = frameHex(header = listOf(0x72), data = chunk3.map { it.code })

        val raw = listOf(statusFrame, vinFrame1, vinFrame2, vinFrame3).joinToString("\r")

        val result = SkimResponseParser.parse(raw)

        assertEquals(SkimReadOutcome.MODULE_PRESENT, result.outcome)
        assertEquals(vin, result.vinEcho)
        assertEquals("DISARMED_KEY_VALID", result.immobilizerStatus)
    }

    @Test
    fun `non-VIN-shaped extra bytes do not get reported as a VIN`() {
        val statusFrame = frameHex(header = listOf(0xB1), data = listOf(0x01))
        val junkFrame = frameHex(header = listOf(0x70), data = listOf(0x01, 0x02, 0x03)) // only 3 bytes, not 17

        val raw = listOf(statusFrame, junkFrame).joinToString("\r")

        val result = SkimResponseParser.parse(raw)

        assertNull(result.vinEcho)
    }

    // ---- (4) malformed / short frame -------------------------------------------

    @Test
    fun `truncated frame missing CRC is malformed`() {
        val raw = "B1" // header byte only - no data, no CRC

        val result = SkimResponseParser.parse(raw)

        assertEquals(SkimReadOutcome.MALFORMED_RESPONSE, result.outcome)
        assertFalse(result.modulePresent)
        assertEquals("MALFORMED", result.immobilizerStatus)
    }

    @Test
    fun `unparseable odd-length hex text is malformed`() {
        val raw = "B10" // odd number of hex digits, cannot split into whole bytes

        val result = SkimResponseParser.parse(raw)

        assertEquals(SkimReadOutcome.MALFORMED_RESPONSE, result.outcome)
        assertFalse(result.modulePresent)
    }

    @Test
    fun `frame with corrupted CRC is malformed`() {
        val header = listOf(0xB1)
        val data = listOf(0x01)
        val correctCrc = J1850Crc.compute(header + data)
        val corruptedCrc = correctCrc xor 0xFF
        val raw = (header + data + corruptedCrc).joinToString("") { "%02X".format(it) }

        val result = SkimResponseParser.parse(raw)

        assertEquals(SkimReadOutcome.MALFORMED_RESPONSE, result.outcome)
        assertFalse(result.modulePresent)
    }

    // ---- (5) parseMonitorStream - PASSIVE MONITOR STREAM, real 2006 Jeep bytes ----------------
    //
    // Every literal hex token below (B1B100D2, B100FA<DATA ERROR, B100<DATA ERROR, the five F0...
    // VIN frames) is copied verbatim from the confirmed real capture,
    // src/test/resources/real/skreem_jeep_2006.log (ELM327 ATMA monitor dump, VPW protocol 2
    // confirmed on a real 2004-2006 Jeep) - not synthetic/fabricated like the frameHex()-built
    // fixtures above. See also TranscriptRunnerTest's end-to-end real-fixture tests.

    @Test
    fun `parseMonitorStream finds the real CRC-valid 0xB1 broadcast among DATA-ERROR noise`() {
        val stream = listOf(
            "B100FA<DATA ERROR", "B100FA<DATA ERROR", "B100<DATA ERROR",
            "B1B100D2",
            "B100FA<DATA ERROR", "100000000000E2", "1A00000052B9"
        )

        val result = SkimResponseParser.parseMonitorStream(stream)

        assertEquals(SkimReadOutcome.MODULE_PRESENT, result.outcome)
        assertTrue(result.modulePresent)
        assertEquals("B1B100D2", result.rawHex)
        assertEquals("SECURED_KEY_LEARNED_PROVISIONAL(0x00)", result.immobilizerStatus)
        assertNull(result.keyCount)
    }

    @Test
    fun `parseMonitorStream discards a DATA-ERROR-flagged token even though it is arithmetically CRC-valid`() {
        // B100FA is a real trap: CRC-8-SAE-J1850 of [0xB1, 0x00] genuinely equals 0xFA, so this
        // token would pass a CRC-only check - but ELM flagged it as a collision/IFR garble
        // (<DATA ERROR) 191 times in the real capture, and it never once co-occurred with a clean
        // B1 frame of that shape. Feeding the parser ONLY this token (no B1B100D2 present) must
        // NOT produce a false-positive MODULE_PRESENT.
        val result = SkimResponseParser.parseMonitorStream(listOf("B100FA<DATA ERROR", "B100<DATA ERROR"))

        assertEquals(SkimReadOutcome.NO_RESPONSE, result.outcome)
        assertFalse(result.modulePresent)
        assertNull(result.vinEcho)
    }

    @Test
    fun `parseMonitorStream reassembles the real VIN from scattered F0 frames`() {
        val stream = listOf(
            "100000000000E2",
            "F00634384B3898",
            "B100FA<DATA ERROR",
            "F0013162",
            "F00A36573137F9",
            "B1B100D2",
            "F0024A34474B20",
            "1A00000052B9",
            "F00E313531394D"
        )

        val result = SkimResponseParser.parseMonitorStream(stream)

        assertEquals("1J4GK48K86W171519", result.vinEcho)
        assertTrue(result.modulePresent)
    }

    @Test
    fun `parseMonitorStream with no valid B1 frame is NO_RESPONSE but still empty candidate list handled gracefully`() {
        val result = SkimResponseParser.parseMonitorStream(emptyList())

        assertEquals(SkimReadOutcome.NO_RESPONSE, result.outcome)
        assertFalse(result.modulePresent)
        assertEquals("", result.rawHex)
        assertNull(result.vinEcho)
    }

    @Test
    fun `parseMonitorStream reports an unmapped broadcast status byte as UNKNOWN_CODE`() {
        // Synthetic (not a real capture): same confirmed shape (header=0xB1, data=[0xB1, statusByte])
        // but with a status byte other than the one real sample (0x00), to prove the PROVISIONAL
        // table doesn't silently misreport an unseen value as secured/key-learned.
        val raw = frameHex(header = listOf(0xB1), data = listOf(0xB1, 0x05))

        val result = SkimResponseParser.parseMonitorStream(listOf(raw))

        assertEquals(SkimReadOutcome.MODULE_PRESENT, result.outcome)
        assertEquals("UNKNOWN_CODE(0x05)", result.immobilizerStatus)
    }
}
