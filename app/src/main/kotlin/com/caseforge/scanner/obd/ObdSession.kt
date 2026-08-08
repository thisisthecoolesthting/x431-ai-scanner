package com.caseforge.scanner.obd

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Session shell: connects transport and routes ECU responses into ISO-TP reassembly;
 * FC responses are sent on [testerToEcuCanId] when receiving a First Frame.
 */
class ObdSession(
    val transport: ObdTransport = StubObdTransport(),
    private val ecuResponseCanId: Int = 0x7E8,
    private val testerToEcuCanId: Int = 0x7E0,
) {
    private val iso = IsoTpHandler(
        emitFlowControl = { fc ->
            transport.sendFrame(testerToEcuCanId, fc)
        },
    )

    val isoTp: IsoTpHandler get() = iso

    private val exchangeMutex = Mutex()

    @Volatile
    private var pendingRx: CompletableDeferred<ByteArray>? = null

    /**
     * One ISO-15765 segmented request and wait for ECU TP payload ([ecuResponseCanId]);
     * [pid] omitted for modes that use length-1 payloads (stored/pending DTC).
     *
     * Stub transport calls with no ECU RX time out quickly and yield an empty [ByteArray].
     */
    suspend fun exchange(mode: Int, pid: Int? = null, timeoutMs: Long = DEFAULT_EXCHANGE_TIMEOUT_MS): ByteArray =
        exchangeMutex.withLock {
            iso.reset()
            val obdPayload = if (pid == null) {
                byteArrayOf((mode and 0xFF).toByte())
            } else {
                byteArrayOf((mode and 0xFF).toByte(), (pid and 0xFF).toByte())
            }
            val frames = iso.buildTransmitSequence(obdPayload)
            val waiter = CompletableDeferred<ByteArray>()
            pendingRx = waiter
            try {
                for (segment in frames) {
                    transport.sendFrame(testerToEcuCanId, segment)
                }
                withTimeoutOrNull(timeoutMs) { waiter.await() } ?: ByteArray(0)
            } finally {
                if (!waiter.isCompleted) {
                    waiter.complete(ByteArray(0))
                }
                if (pendingRx === waiter) {
                    pendingRx = null
                }
            }
        }

    fun connect() {
        transport.connect()
        transport.setFrameListener(
            ObdFrameListener { frame ->
                if (frame.id != ecuResponseCanId) return@ObdFrameListener
                when (val rx = iso.ingest(frame.data)) {
                    IsoTpRxResult.NeedMore -> Unit
                    is IsoTpRxResult.Error ->
                        pendingRx?.takeUnless { it.isCompleted }?.complete(ByteArray(0))
                    is IsoTpRxResult.Complete ->
                        pendingRx?.takeUnless { it.isCompleted }?.complete(rx.payload.copyOf())
                }
            },
        )
    }

    fun disconnect() {
        transport.setFrameListener(null)
        transport.disconnect()
        iso.reset()
        pendingRx = null
    }

    companion object {
        const val DEFAULT_EXCHANGE_TIMEOUT_MS: Long = 500L
    }
}
