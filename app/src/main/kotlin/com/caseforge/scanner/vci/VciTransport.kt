package com.caseforge.scanner.vci

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Pluggable VCI link — Bluetooth SPP ([BluetoothVciClient]) or USB serial ([OemUsbVciClient]).
 */
interface VciTransport {
    enum class ConnectionState {
        DISCONNECTED,
        CONNECTING,
        CONNECTED,
        RECONNECTING,
        CLOSED,
    }

    val connectionState: StateFlow<ConnectionState>
    val frames: Flow<VciFrame>
    val label: String

    fun disconnect()
    fun close()

    @Throws(VciException::class)
    fun sendFrame(frame: VciFrame)

    @Throws(VciException::class)
    fun sendRaw(opcode: Int, payload: ByteArray = ByteArray(0))

    @Throws(VciException::class)
    fun send(opcode: KnownOpcode, payload: ByteArray = ByteArray(0))
}
