package com.caseforge.scanner.obd.j1850

import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse

class ElmSerialTransportTest {

    private fun happyPathFixtures(): Map<String, String> = mapOf(
        "ATZ" to "ELM327 v2.1",
        "ATE0" to "OK",
        "ATL0" to "OK",
        "ATS0" to "OK",
        "ATH1" to "OK",
        "ATSP2" to "OK",
        "ATDPN" to "2"
    )

    @Test
    fun `elmInitVpw runs the documented sequence in order and confirms VPW`() {
        val transport = FakeElmTransport(happyPathFixtures())
        transport.open()

        val result = transport.elmInitVpw()

        assertTrue(result.success)
        assertTrue(result.protocolConfirmed)
        assertEquals(
            listOf("ATZ", "ATE0", "ATL0", "ATS0", "ATH1", "ATSP2", "ATDPN"),
            transport.sentCommands
        )
    }

    @Test
    fun `elmInitVpw fails cleanly when adapter does not answer reset`() {
        val transport = FakeElmTransport(mapOf("ATZ" to ""))
        transport.open()

        val result = transport.elmInitVpw()

        assertFalse(result.success)
        assertEquals(listOf("ATZ"), transport.sentCommands) // stops immediately, doesn't keep going
    }

    @Test
    fun `elmInitVpw fails cleanly when protocol is not confirmed`() {
        val fixtures = happyPathFixtures() + ("ATDPN" to "1") // adapter stuck on J1850 PWM, not VPW
        val transport = FakeElmTransport(fixtures)
        transport.open()

        val result = transport.elmInitVpw()

        assertFalse(result.success)
        assertFalse(result.protocolConfirmed)
    }

    @Test
    fun `elmInitVpw fails cleanly when a setup command is not OK'd`() {
        val fixtures = happyPathFixtures() + ("ATH1" to "ERROR")
        val transport = FakeElmTransport(fixtures)
        transport.open()

        val result = transport.elmInitVpw()

        assertFalse(result.success)
        assertEquals(listOf("ATZ", "ATE0", "ATL0", "ATS0", "ATH1"), transport.sentCommands)
    }
}
