package com.caseforge.scanner.obd

fun interface ObdFrameListener {
    fun onFrame(frame: ObdCanFrame)
}

/**
 * Physical/data-link transport for OBD-relevant CAN frames (Plan B Tier 0 — no VCI coupling).
 */
interface ObdTransport {
    fun connect()

    fun disconnect()

    fun isConnected(): Boolean

    /** Send a CAN frame; [payload] is typically 1–8 bytes (classic CAN). */
    fun sendFrame(canId: Int, payload: ByteArray)

    fun setFrameListener(listener: ObdFrameListener?)

    /** Short label for OEM / status surfaces (defaults to runtime class name). */
    fun describe(): String = this::class.java.simpleName
}
