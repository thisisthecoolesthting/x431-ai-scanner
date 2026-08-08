package com.caseforge.scanner.obd

import android.util.Log
import com.caseforge.scanner.agent.ObdElmEngine
import com.caseforge.scanner.vci.DiagnosticConnector
import com.caseforge.scanner.vci.KnownOpcode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * [ObdTransport] backed by an ELM327 link from [DiagnosticConnector] (USB or Bluetooth).
 * Routes native OBD wedge through the same AUTO path as the standalone scanner UI.
 */
class Elm327ObdTransport(
    private val link: DiagnosticConnector.ActiveLink,
    private val elm: ObdElmEngine,
) : ObdTransport {

    companion object {
        private const val TAG = "Elm327ObdTransport"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val ioMutex = Mutex()

    @Volatile
    private var frameListener: ObdFrameListener? = null

    @Volatile
    private var connected = false

    @Volatile
    private var rxJob: Job? = null

    override fun describe(): String =
        if (connected) "Elm327ObdTransport(${link.detail})" else "Elm327ObdTransport(disconnected)"

    override fun connect() {
        if (connected) return
        connected = true
        Log.i(TAG, "connected via ${link.kind.name} ${link.detail}")
    }

    override fun disconnect() {
        rxJob?.cancel()
        rxJob = null
        frameListener = null
        connected = false
    }

    override fun isConnected(): Boolean = connected

    override fun sendFrame(canId: Int, payload: ByteArray) {
        if (!connected || canId != 0x7E0) return
        rxJob?.cancel()
        rxJob = scope.launch {
            runCatching {
                val obdPayload = ioMutex.withLock { queryObdPayload(payload) } ?: return@launch
                val listener = frameListener ?: return@launch
                for (frame in obdPayloadToSyntheticEcuCan(obdPayload)) {
                    listener.onFrame(frame)
                }
            }.onFailure { e ->
                Log.w(TAG, "sendFrame ELM327 query failed: ${e.message}")
            }
        }
    }

    override fun setFrameListener(listener: ObdFrameListener?) {
        frameListener = listener
    }

    fun close() {
        disconnect()
        runCatching { link.disconnect() }
    }

    private suspend fun queryObdPayload(isoTpData: ByteArray): ByteArray? {
        val mapped = mapTesterObdRequest(isoTpData) ?: return null
        val opcode = mapped.first
        val tp = mapped.second
        return when (opcode) {
            KnownOpcode.OBD_MODE01_PID_REQ -> {
                val pid = tp[0].toInt() and 0xFF
                val pidHex = "%02X".format(pid)
                val bytes = elm.readPidBytes(pidHex) ?: return null
                byteArrayOf(0x41, pid.toByte()) + bytes.map { it.toByte() }.toByteArray()
            }
            KnownOpcode.OBD_MODE03_DTC_REQ -> {
                val (stored, pending) = elm.readDtcCodes()
                val mode: Int
                val codes: List<String>
                if (tp.isNotEmpty() && (tp[0].toInt() and 0xFF) == 0x07) {
                    mode = 0x47
                    codes = pending
                } else {
                    mode = 0x43
                    codes = stored
                }
                buildDtcPayload(mode, codes)
            }
            KnownOpcode.OBD_MODE04_CLEAR_DTC -> {
                elm.clearCodes()
                byteArrayOf(0x44)
            }
            KnownOpcode.OBD_MODE09_VEH_INFO_REQ -> {
                val infoId = tp[0].toInt() and 0xFF
                if (infoId == 0x02) {
                    val vin = elm.readVin()?.trim()?.uppercase().orEmpty()
                    if (vin.length < 17) return null
                    byteArrayOf(0x49, 0x02, 0x01) +
                        vin.encodeToByteArray().copyOf(17)
                } else {
                    null
                }
            }
            else -> null
        }
    }

    private fun buildDtcPayload(mode: Int, codes: List<String>): ByteArray {
        val out = mutableListOf(mode.toByte())
        codes.forEach { code ->
            val c = code.trim().uppercase().removePrefix("P")
            if (c.length == 4) {
                val b0 = nibble(c[0]) shl 4 or nibble(c[1])
                val b1 = nibble(c[2]) shl 4 or nibble(c[3])
                out.add(b0.toByte())
                out.add(b1.toByte())
            }
        }
        return out.toByteArray()
    }

    private fun nibble(c: Char): Int = when {
        c in '0'..'9' -> c - '0'
        c in 'A'..'F' -> c - 'A' + 10
        else -> 0
    }
}

