package com.caseforge.scanner.obd

import android.content.Context
import android.util.Log
import com.caseforge.scanner.data.SettingsRepo
import com.caseforge.scanner.vci.DiagnosticConnector
import com.caseforge.scanner.vci.KnownOpcode
import com.caseforge.scanner.vci.VciConnector
import com.caseforge.scanner.vci.VciFrame
import com.caseforge.scanner.vci.VciTransport
import android.os.Looper
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.coroutineContext

/**
 * Bridges ISO-TP / OBD-style CAN frames ([ObdTransport]) to the OEM VCI [VciTransport] stack
 * (same path as [com.caseforge.scanner.vci.VciConnector] / [DirectVciSession]).
 *
 * **TX:** Maps common OBD-II single-frame tester requests on **0x7E0** to inferred VCI opcodes;
 * multi-segment ISO-TP tester TX and flow-control frames fall back to a raw shim (`sendRaw`).
 * **RX:** Consumes the VCI [VciTransport.frames] pump and re-wraps OBD response payloads as
 * synthetic **0x7E8** classic CAN data fields so [ObdSession] / [IsoTpHandler] can ingest them.
 */
class VciObdTransport(
    private val connector: suspend () -> Result<VciTransport>,
) : ObdTransport {

    constructor(context: Context, settings: SettingsRepo) : this(
        connector = {
            DiagnosticConnector.connect(context.applicationContext, settings).fold(
                onSuccess = { link ->
                    when (link.kind) {
                        DiagnosticConnector.LinkKind.ELM327_USB,
                        DiagnosticConnector.LinkKind.ELM327_BT,
                        -> {
                            link.disconnect()
                            Result.failure(
                                IllegalStateException(
                                    "ELM327 link detected — native OBD routes ELM327 via DiagnosticConnector/Elm327ObdTransport",
                                ),
                            )
                        }
                        else -> {
                            link.disconnect()
                            VciConnector.connect(context.applicationContext, settings).map { it.transport }
                        }
                    }
                },
                onFailure = { err ->
                    VciConnector.connect(context.applicationContext, settings)
                        .map { it.transport }
                        .onFailure { Log.w(TAG, "DiagnosticConnector failed (${err.message}); VciConnector fallback failed too") }
                },
            ).onFailure { err ->
                val msg = err.message.orEmpty().lowercase()
                if (
                    msg.contains("no usb") ||
                    msg.contains("first attached") ||
                    msg.contains("no adapter") ||
                    msg.contains("not found")
                ) {
                    Log.w(
                        TAG,
                        "No VCI USB device detected. DB15 may need OEM USB enumeration; try Direct VCI diagnostics.",
                    )
                }
            }
        },
    )

    companion object {
        private const val TAG = "VciObdTransport"
        private const val CONNECT_TIMEOUT_MS = 25_000L

        /**
         * Fallback host→VCI opcode when no structured mapping exists yet (wire capture TBD).
         * Payload layout: 4-byte big-endian CAN ID + 8-byte CAN data (ISO-TP / raw).
         */
        internal const val SHIM_CAN_RAW_OPCODE: Int = 0x0200

        /** Default ECU response CAN id (11-bit) used when synthesising RX from VCI OBD payloads. */
        const val DEFAULT_ECU_RESPONSE_CAN_ID: Int = 0x7E8
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var vci: VciTransport? = null

    @Volatile
    private var rxJob: Job? = null

    @Volatile
    private var frameListener: ObdFrameListener? = null

    @Volatile
    private var connectInFlight: CompletableDeferred<Result<Unit>>? = null

    private val connectMutex = Mutex()

    override fun describe(): String {
        val t = vci
        return if (t != null) "VciObdTransport(${t.label})" else "VciObdTransport(disconnected)"
    }

    /**
     * Suspend connect — preferred from [kotlinx.coroutines.Dispatchers.IO] (never blocks Main).
     */
    suspend fun connectSuspend(): Result<Unit> = connectMutex.withLock {
        if (vci != null) return Result.success(Unit)
        connectInFlight?.let { return it.await() }

        val deferred = CompletableDeferred<Result<Unit>>()
        connectInFlight = deferred
        try {
            val result = withContext(Dispatchers.IO) {
                withTimeout(CONNECT_TIMEOUT_MS) {
                    connector().map { transport ->
                        vci = transport
                        startFramesPump(transport)
                    }
                }
            }
            deferred.complete(result)
            result.onFailure { e -> Log.e(TAG, "connect failed: ${e.message}", e) }
            result
        } catch (e: Exception) {
            Log.e(TAG, "connect failed: ${e.message}", e)
            val failure = Result.failure<Unit>(e)
            deferred.complete(failure)
            failure
        } finally {
            if (connectInFlight === deferred) connectInFlight = null
        }
    }

    override fun connect() {
        if (vci != null) return
        if (Looper.myLooper() == Looper.getMainLooper()) {
            Log.i(TAG, "connect() on main — scheduling async IO connect")
            scope.launch { connectSuspend() }
            return
        }
        runBlocking { connectSuspend() }
    }

    override fun disconnect() {
        rxJob?.cancel()
        rxJob = null
        frameListener = null
        runCatching {
            val t = vci
            t?.disconnect()
            t?.close()
        }
        vci = null
    }

    override fun isConnected(): Boolean {
        val t = vci ?: return false
        return t.connectionState.value == VciTransport.ConnectionState.CONNECTED
    }

    override fun sendFrame(canId: Int, payload: ByteArray) {
        val transport = vci ?: return
        if (payload.size > 8) {
            Log.w(TAG, "sendFrame: DLC > 8, truncating")
        }
        val data = if (payload.size >= 8) payload.copyOf(8) else payload.copyOf(8).also { pad ->
            for (i in payload.size until 8) pad[i] = IsoTp15765.PADDING_BYTE
        }
        runCatching {
            if (canId == 0x7E0) {
                mapTesterObdRequest(data)?.let { (op, pl) ->
                    transport.send(op, pl)
                    return
                }
            }
            transport.sendRaw(SHIM_CAN_RAW_OPCODE, packRawShim(canId, data))
        }.onFailure { Log.e(TAG, "sendFrame failed: ${it.message}", it) }
    }

    override fun setFrameListener(listener: ObdFrameListener?) {
        frameListener = listener
    }

    private fun startFramesPump(transport: VciTransport) {
        rxJob?.cancel()
        rxJob = scope.launch {
            transport.frames
                .catch { e -> Log.e(TAG, "frames pump error: ${e.message}", e) }
                .collect { frame ->
                    if (!coroutineContext.isActive) return@collect
                    val l = frameListener ?: return@collect
                    for (obd in vciFrameToObdCanFrames(frame)) {
                        l.onFrame(obd)
                    }
                }
        }
    }
}

/** Visible for unit tests — maps a VCI wire frame into zero or more ECU-side CAN frames. */
internal fun vciFrameToObdCanFrames(
    frame: VciFrame,
    ecuCanId: Int = VciObdTransport.DEFAULT_ECU_RESPONSE_CAN_ID,
): List<ObdCanFrame> {
    val opcode = KnownOpcode.fromValue(frame.opcode) ?: return emptyList()
    val tp = frame.payload
    if (tp.isEmpty()) return emptyList()

    return when (opcode) {
        KnownOpcode.OBD_MODE01_PID_RESP,
        KnownOpcode.OBD_MODE03_DTC_RESP,
        KnownOpcode.OBD_MODE04_CLEAR_RESP,
        KnownOpcode.OBD_MODE09_VEH_INFO_RESP,
        -> obdPayloadToSyntheticEcuCan(tp, ecuCanId)
        else -> emptyList()
    }
}

/**
 * Wraps a logical OBD response byte string (what the ECU would place after ISO-TP PCI on CAN)
 * into one or more 8-byte CAN data fields for [ObdSession].
 */
internal fun obdPayloadToSyntheticEcuCan(
    obdPayload: ByteArray,
    ecuCanId: Int = VciObdTransport.DEFAULT_ECU_RESPONSE_CAN_ID,
): List<ObdCanFrame> {
    if (obdPayload.isEmpty()) return emptyList()
    val iso = IsoTpHandler(emitFlowControl = null)
    val segments = iso.buildTransmitSequence(obdPayload)
    return segments.map { ObdCanFrame(ecuCanId, it) }
}

internal fun packRawShim(canId: Int, data8: ByteArray): ByteArray {
    require(data8.size == 8)
    val out = ByteArray(4 + 8)
    out[0] = (canId shr 24).toByte()
    out[1] = (canId shr 16).toByte()
    out[2] = (canId shr 8).toByte()
    out[3] = canId.toByte()
    data8.copyInto(out, 4)
    return out
}

/** Maps ISO-TP single-frame tester data on 0x7E0 to VCI OBD opcodes when possible. */
internal fun mapTesterObdRequest(isoTpData: ByteArray): Pair<KnownOpcode, ByteArray>? {
    if (isoTpData.size < 8) return null
    val pci = isoTpData[0].toInt() and 0xFF
    val pciType = pci shr 4
    if (pciType != IsoTpPci.SINGLE_FRAME) return null
    val sfDl = pci and 0x0F
    if (sfDl == 0 || sfDl > 7) return null
    if (1 + sfDl > isoTpData.size) return null
    val tp = isoTpData.copyOfRange(1, 1 + sfDl)
    if (tp.isEmpty()) return null
    return when (val mode = tp[0].toInt() and 0xFF) {
        0x01 -> {
            if (tp.size < 2) return null
            KnownOpcode.OBD_MODE01_PID_REQ to byteArrayOf(tp[1])
        }
        0x03 -> KnownOpcode.OBD_MODE03_DTC_REQ to ByteArray(0)
        0x04 -> KnownOpcode.OBD_MODE04_CLEAR_DTC to ByteArray(0)
        0x07 ->
            // Same opcode word as stored DTCs; payload distinguishes pending (VciCommunicator spike).
            KnownOpcode.OBD_MODE03_DTC_REQ to byteArrayOf(0x07)
        0x09 -> {
            if (tp.size < 2) return null
            KnownOpcode.OBD_MODE09_VEH_INFO_REQ to byteArrayOf(tp[1])
        }
        else -> null
    }
}
