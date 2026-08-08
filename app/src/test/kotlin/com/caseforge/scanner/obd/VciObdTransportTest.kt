package com.caseforge.scanner.obd

import com.caseforge.scanner.vci.KnownOpcode
import com.caseforge.scanner.vci.VciFrame
import com.caseforge.scanner.vci.VciTransport
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VciObdTransportTest {

    @Test
    fun mapTesterObdRequest_mode01_maps_to_pid_opcode() {
        // ISO-TP SF, 2 byte TP: 01 0C -> RPM
        val pci = 0x02 // SF length 2
        val data = ByteArray(8) { IsoTp15765.PADDING_BYTE }
        data[0] = ((IsoTpPci.SINGLE_FRAME shl 4) or pci).toByte()
        data[1] = 0x01
        data[2] = 0x0C
        val mapped = mapTesterObdRequest(data)!!
        assertEquals(KnownOpcode.OBD_MODE01_PID_REQ, mapped.first)
        assertEquals(0x0C, mapped.second[0].toInt() and 0xFF)
    }

    @Test
    fun vciFrameToObdCanFrames_mode01_response_wraps_as_single_iso_tp_can() {
        val payload = hb("41 0C 1A F8") // standard Mode 01 RPM positive
        val vf = VciFrame.build(KnownOpcode.OBD_MODE01_PID_RESP.value, payload)
        val frames = vciFrameToObdCanFrames(vf)
        assertEquals(1, frames.size)
        assertEquals(0x7E8, frames[0].id)
        assertEquals(8, frames[0].data.size)
        val ing = IsoTpHandler(null)
        val r = ing.ingest(frames[0].data)
        assertTrue(r is IsoTpRxResult.Complete)
        assertTrue((r as IsoTpRxResult.Complete).payload.contentEquals(payload))
    }

    @Test
    fun sendFrame_routes_to_known_opcode_on_fake_vci() {
        val fake = FakeVciTransport()
        val transport = VciObdTransport { Result.success(fake) }
        transport.connect()
        val pci = 0x02
        val data = ByteArray(8) { IsoTp15765.PADDING_BYTE }
        data[0] = ((IsoTpPci.SINGLE_FRAME shl 4) or pci).toByte()
        data[1] = 0x01
        data[2] = 0x05 // coolant
        transport.sendFrame(0x7E0, data)
        assertEquals(1, fake.sentOpcodes.size)
        assertEquals(KnownOpcode.OBD_MODE01_PID_REQ.value, fake.sentOpcodes[0])
        transport.disconnect()
    }

    private fun hb(s: String): ByteArray =
        s.trim().split(Regex("\\s+")).map { it.toInt(16).toByte() }.toByteArray()

    private class FakeVciTransport : VciTransport {
        val sentOpcodes = mutableListOf<Int>()
        private val _state = MutableStateFlow(VciTransport.ConnectionState.CONNECTED)
        private val ch = Channel<VciFrame>(Channel.BUFFERED)

        override val connectionState: StateFlow<VciTransport.ConnectionState> = _state.asStateFlow()
        override val frames: Flow<VciFrame> = ch.receiveAsFlow()
        override val label: String = "fake"

        override fun disconnect() {
            _state.value = VciTransport.ConnectionState.DISCONNECTED
        }

        override fun close() = disconnect()

        override fun sendFrame(frame: VciFrame) {
            sentOpcodes += frame.opcode
        }

        override fun sendRaw(opcode: Int, payload: ByteArray) {
            sendFrame(VciFrame.build(opcode, payload))
        }

        override fun send(opcode: KnownOpcode, payload: ByteArray) = sendRaw(opcode.value, payload)
    }
}
