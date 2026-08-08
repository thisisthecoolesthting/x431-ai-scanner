package com.caseforge.scanner.obd

/**
 * Test/desk stub: records activity, optional ELM327-style ATZ log line on [connect].
 */
class StubObdTransport(
    private val log: (String) -> Unit = { },
) : ObdTransport {

    private var connected: Boolean = false
    private var listener: ObdFrameListener? = null

    /** When true, [connect] emits a one-line ATZ-style banner to [log] (not on CAN bus). */
    var simulateAtzOnConnect: Boolean = false

    val sentFrames: MutableList<Pair<Int, ByteArray>> = mutableListOf()
    val logLines: MutableList<String> = mutableListOf()

    override fun connect() {
        connected = true
        logLines += "connect()"
        if (simulateAtzOnConnect) {
            val line = "ATZ -> ELM327 v1.5"
            log(line)
            logLines += line
        }
    }

    override fun disconnect() {
        connected = false
        listener = null
        logLines += "disconnect()"
    }

    override fun isConnected(): Boolean = connected

    override fun sendFrame(canId: Int, payload: ByteArray) {
        val copy = payload.copyOf()
        sentFrames += canId to copy
        logLines += "sendFrame id=0x${canId.toString(16)} len=${copy.size}"
        log("TX 0x${canId.toString(16)} ${copy.joinToString(" ") { b -> "%02X".format(b) }}")
    }

    override fun setFrameListener(listener: ObdFrameListener?) {
        this.listener = listener
    }

    /** Test helper: simulate an ECU RX frame. */
    fun deliverInbound(frame: ObdCanFrame) {
        listener?.onFrame(frame)
    }
}
