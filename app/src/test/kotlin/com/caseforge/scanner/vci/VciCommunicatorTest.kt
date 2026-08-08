package com.caseforge.scanner.vci

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock

class VciCommunicatorTest {

    private lateinit var communicator: VciCommunicator

    @Before
    fun setUp() {
        communicator = VciCommunicator(mock())
    }

    @Test
    fun `parseDtcPayload decodes P0300 with count prefix`() {
        val payload = byteArrayOf(
            0x01,                     // DTC count
            0x03, 0x00,               // P0300
        )

        val dtcs = communicator.parseDtcPayload(payload)

        assertEquals(1, dtcs.size)
        assertEquals("P0300", dtcs[0].code)
        assertEquals(DtcSource.OBD_MODE03, dtcs[0].source)
    }

    @Test
    fun `parseDtcPayload skips null padding pairs`() {
        val payload = byteArrayOf(
            0x02,
            0x03, 0x00,
            0x00, 0x00,
        )

        val dtcs = communicator.parseDtcPayload(payload)
        assertEquals(1, dtcs.size)
        assertEquals("P0300", dtcs[0].code)
    }

    @Test
    fun `parseDtcPayload returns empty for empty payload`() {
        assertTrue(communicator.parseDtcPayload(ByteArray(0)).isEmpty())
    }

    @Test
    fun `parsePidResponse decodes engine RPM`() {
        val payload = byteArrayOf(
            0x41,
            VciCommunicator.PID_ENGINE_RPM.toByte(),
            0x1F,
            0x40,
        )

        val sample = communicator.parsePidResponse(VciCommunicator.PID_ENGINE_RPM, payload)

        assertNotNull(sample)
        assertEquals(1984.0, sample!!.value, 0.001)
        assertEquals("rpm", sample.unit)
    }

    @Test
    fun `parsePidResponse decodes coolant temperature`() {
        val payload = byteArrayOf(
            0x41,
            VciCommunicator.PID_ENGINE_COOLANT_TEMP.toByte(),
            0x7B,
        )

        val sample = communicator.parsePidResponse(
            VciCommunicator.PID_ENGINE_COOLANT_TEMP,
            payload,
        )

        assertNotNull(sample)
        assertEquals(83.0, sample!!.value, 0.001)
        assertEquals("°C", sample.unit)
    }

    @Test
    fun `parseVinPayload extracts 17 character VIN from ASCII payload`() {
        val vin = "1HGCM82633A004352"
        val payload = byteArrayOf(
            0x49, 0x02, 0x01,
            *vin.toByteArray(Charsets.US_ASCII),
        )

        assertEquals(vin, communicator.parseVinPayload(payload))
    }

    @Test
    fun `parseVinPayload finds VIN embedded in mixed bytes`() {
        val vin = "1HGCM82633A004352"
        val payload = byteArrayOf(0x00, 0x49) + vin.toByteArray(Charsets.US_ASCII) + byteArrayOf(0x00)

        assertEquals(vin, communicator.parseVinPayload(payload))
    }

    @Test
    fun `parseVinPayload returns null when too short`() {
        assertNull(communicator.parseVinPayload(byteArrayOf(0x49, 0x02)))
    }
}
