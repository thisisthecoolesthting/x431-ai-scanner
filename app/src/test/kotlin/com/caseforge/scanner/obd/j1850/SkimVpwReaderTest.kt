package com.caseforge.scanner.obd.j1850

import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

class SkimVpwReaderTest {

    private fun elmSetupFixtures(): Map<String, String> = mapOf(
        "ATZ" to "ELM327 v2.1",
        "ATE0" to "OK",
        "ATL0" to "OK",
        "ATS0" to "OK",
        "ATH1" to "OK",
        "ATSP2" to "OK",
        "ATDPN" to "2"
    )

    @Test
    fun `readSkimStatus runs init then sends the provisional SKIM request and parses the reply`() {
        val bytes = listOf(0xB1, 0x01)
        val statusFrameHex = (bytes + J1850Crc.compute(bytes)).joinToString("") { "%02X".format(it) }
        val fixtures = elmSetupFixtures() + (SkimVpwReader.PROVISIONAL_SKIM_STATUS_REQUEST_HEX to statusFrameHex)
        val transport = FakeElmTransport(fixtures)

        val result = SkimVpwReader(transport).readSkimStatus()

        assertEquals(SkimReadOutcome.MODULE_PRESENT, result.outcome)
        assertEquals("ARMED", result.immobilizerStatus)
        assertTrue(transport.sentCommands.contains(SkimVpwReader.PROVISIONAL_SKIM_STATUS_REQUEST_HEX))
        assertEquals(1, transport.closeCallCount)
    }

    @Test
    fun `readSkimStatus reports NO_RESPONSE and still closes when init fails`() {
        val transport = FakeElmTransport(mapOf("ATZ" to ""))

        val result = SkimVpwReader(transport).readSkimStatus()

        assertEquals(SkimReadOutcome.NO_RESPONSE, result.outcome)
        assertEquals(1, transport.closeCallCount)
    }

    @Test
    fun `readSkimStatus reports NO_RESPONSE when adapter answers NO DATA`() {
        val fixtures = elmSetupFixtures() + (SkimVpwReader.PROVISIONAL_SKIM_STATUS_REQUEST_HEX to "NO DATA")
        val transport = FakeElmTransport(fixtures)

        val result = SkimVpwReader(transport).readSkimStatus()

        assertEquals(SkimReadOutcome.NO_RESPONSE, result.outcome)
        assertEquals(1, transport.closeCallCount)
    }
}
