package com.caseforge.scanner.obd

import kotlin.math.min

/**
 * ISO 15765-2 style ISO-TP on classic CAN (8-byte DLC): single-frame, multi-frame RX/TX,
 * basic flow-control (CTS) when receiving a First Frame.
 *
 * OBD on CAN (15765-4) uses the same PCI layout for segmented diagnostic payloads.
 */
object IsoTp15765 {
    /** Maximum logical TP payload (12-bit length field in First Frame). */
    const val MAX_PAYLOAD_LENGTH = 4095

    /** N_Bs — time until next Flow Control or Consecutive Frame (informative; host should enforce). */
    const val N_BS_TIMEOUT_MS = 1_000L

    /** N_Cr — time between consecutive frames (informative). */
    const val N_CR_TIMEOUT_MS = 1_000L

    /** Default STmin placeholder when not specified (0 = as fast as possible). */
    const val ST_MIN_DEFAULT_MS = 0L

    /** CAN frame data padding for unused bytes (ISO 15765-2). */
    const val PADDING_BYTE: Byte = 0xCC.toByte()
}

/**
 * ISO-TP PCI high nibble values.
 */
object IsoTpPci {
    const val SINGLE_FRAME: Int = 0x0
    const val FIRST_FRAME: Int = 0x1
    const val CONSECUTIVE_FRAME: Int = 0x2
    const val FLOW_CONTROL: Int = 0x3

    const val FC_CTS: Int = 0x0
    const val FC_WAIT: Int = 0x1
    const val FC_OVERFLOW: Int = 0x2
}

sealed class IsoTpRxResult {
    data class Complete(val payload: ByteArray) : IsoTpRxResult()

    /** More CFs or FC handling required. */
    data object NeedMore : IsoTpRxResult()

    data class Error(val message: String) : IsoTpRxResult()
}

/**
 * @param emitFlowControl when non-null, a default CTS frame (`30 00 00` + padding) is sent after a valid First Frame.
 */
class IsoTpHandler(
    private val emitFlowControl: ((ByteArray) -> Unit)? = null,
) {
    private enum class RxPhase { Idle, Multi }

    private var phase: RxPhase = RxPhase.Idle
    private var expectedTotal: Int = 0
    private var received: Int = 0
    private var buffer: ByteArray? = null
    private var nextCfSeq: Int = 1

    fun reset() {
        phase = RxPhase.Idle
        expectedTotal = 0
        received = 0
        buffer = null
        nextCfSeq = 1
    }

    /**
     * Feed one ISO-TP CAN data field (up to 8 bytes). For single-frame input returns [IsoTpRxResult.Complete].
     */
    fun ingest(data: ByteArray): IsoTpRxResult {
        if (data.isEmpty()) return IsoTpRxResult.Error("empty CAN data")
        val pci = data[0].toInt() and 0xFF
        val type = pci shr 4

        return when (type) {
            IsoTpPci.SINGLE_FRAME -> handleSingleFrame(pci, data)
            IsoTpPci.FIRST_FRAME -> handleFirstFrame(pci, data)
            IsoTpPci.CONSECUTIVE_FRAME -> handleConsecutiveFrame(pci, data)
            IsoTpPci.FLOW_CONTROL -> IsoTpRxResult.Error("unexpected flow control on RX path")
            else -> IsoTpRxResult.Error("unknown PCI 0x${type.toString(16)}")
        }
    }

    private fun handleSingleFrame(pci: Int, data: ByteArray): IsoTpRxResult {
        if (phase != RxPhase.Idle) {
            reset()
        }
        val sfDl = pci and 0x0F
        if (sfDl == 0 || sfDl > 7) {
            return IsoTpRxResult.Error("invalid SF_DL $sfDl")
        }
        if (data.size < 1 + sfDl) {
            return IsoTpRxResult.Error("SF truncated")
        }
        val payload = data.copyOfRange(1, 1 + sfDl)
        return IsoTpRxResult.Complete(payload)
    }

    private fun handleFirstFrame(pci: Int, data: ByteArray): IsoTpRxResult {
        reset()
        if (data.size < 8) {
            return IsoTpRxResult.Error("FF too short")
        }
        val total =
            ((pci and 0x0F) shl 8) or (data[1].toInt() and 0xFF)
        if (total == 0 || total > IsoTp15765.MAX_PAYLOAD_LENGTH) {
            return IsoTpRxResult.Error("invalid FF length $total")
        }
        expectedTotal = total
        buffer = ByteArray(total)
        val firstChunk = min(6, total)
        data.copyInto(buffer!!, 0, 2, 2 + firstChunk)
        received = firstChunk
        nextCfSeq = 1
        phase = RxPhase.Multi

        val fc = flowControlFrame(
            status = IsoTpPci.FC_CTS,
            blockSize = 0x00,
            stMin = 0x00,
        )
        emitFlowControl?.invoke(fc)

        return if (received >= expectedTotal) {
            val done = buffer!!
            reset()
            IsoTpRxResult.Complete(done)
        } else {
            IsoTpRxResult.NeedMore
        }
    }

    private fun handleConsecutiveFrame(pci: Int, data: ByteArray): IsoTpRxResult {
        if (phase != RxPhase.Multi || buffer == null) {
            return IsoTpRxResult.Error("unexpected CF")
        }
        val seq = pci and 0x0F
        if (seq != nextCfSeq) {
            reset()
            return IsoTpRxResult.Error("CF sequence got $seq expected $nextCfSeq")
        }
        val room = expectedTotal - received
        if (room <= 0) {
            reset()
            return IsoTpRxResult.Error("overflow")
        }
        val take = min(7, room)
        if (data.size < 1 + take) {
            return IsoTpRxResult.Error("CF truncated")
        }
        data.copyInto(buffer!!, received, 1, 1 + take)
        received += take
        nextCfSeq = if (nextCfSeq == 0x0F) 1 else nextCfSeq + 1

        return if (received >= expectedTotal) {
            val done = buffer!!
            reset()
            IsoTpRxResult.Complete(done)
        } else {
            IsoTpRxResult.NeedMore
        }
    }

    /**
     * Build one classic-CAN 8-byte data field for a single-frame transmission (payload 1..7 bytes).
     */
    fun buildSingleFrame(payload: ByteArray): ByteArray {
        require(payload.isNotEmpty()) { "SF payload empty" }
        require(payload.size <= 7) { "SF payload max 7 bytes, got ${payload.size}" }
        val out = ByteArray(8) { IsoTp15765.PADDING_BYTE }
        out[0] = (IsoTpPci.SINGLE_FRAME shl 4 or payload.size).toByte()
        payload.copyInto(out, 1, 0, payload.size)
        return out
    }

    /**
     * Segment a full diagnostic payload into ISO-TP CAN data fields (8 bytes each, padded).
     * Does not wait for peer Flow Control before CFs — caller should pace using bus rules.
     */
    fun buildTransmitSequence(payload: ByteArray): List<ByteArray> {
        require(payload.isNotEmpty()) { "empty payload" }
        require(payload.size <= IsoTp15765.MAX_PAYLOAD_LENGTH) {
            "payload ${payload.size} > ${IsoTp15765.MAX_PAYLOAD_LENGTH}"
        }
        if (payload.size <= 7) {
            return listOf(buildSingleFrame(payload))
        }
        val frames = ArrayList<ByteArray>()
        val total = payload.size
        val ff = ByteArray(8) { IsoTp15765.PADDING_BYTE }
        ff[0] = (IsoTpPci.FIRST_FRAME shl 4 or ((total shr 8) and 0x0F)).toByte()
        ff[1] = (total and 0xFF).toByte()
        payload.copyInto(ff, 2, 0, 6)
        frames.add(ff)

        var offset = 6
        var seq = 1
        while (offset < total) {
            val cf = ByteArray(8) { IsoTp15765.PADDING_BYTE }
            cf[0] = (IsoTpPci.CONSECUTIVE_FRAME shl 4 or (seq and 0x0F)).toByte()
            val chunk = min(7, total - offset)
            payload.copyInto(cf, 1, offset, offset + chunk)
            frames.add(cf)
            offset += chunk
            seq = if (seq == 0x0F) 1 else seq + 1
        }
        return frames
    }

    /**
     * Parse a Flow Control frame data field (first three bytes meaningful).
     */
    fun parseFlowControl(data: ByteArray): Triple<Int, Int, Int>? {
        if (data.isEmpty()) return null
        val pci = data[0].toInt() and 0xFF
        if (pci shr 4 != IsoTpPci.FLOW_CONTROL) return null
        val fs = pci and 0x0F
        val bs = if (data.size > 1) data[1].toInt() and 0xFF else 0
        val st = if (data.size > 2) data[2].toInt() and 0xFF else 0
        return Triple(fs, bs, st)
    }

    private fun flowControlFrame(status: Int, blockSize: Int, stMin: Int): ByteArray {
        val out = ByteArray(8) { IsoTp15765.PADDING_BYTE }
        out[0] = ((IsoTpPci.FLOW_CONTROL shl 4) or (status and 0x0F)).toByte()
        out[1] = (blockSize and 0xFF).toByte()
        out[2] = (stMin and 0xFF).toByte()
        return out
    }
}
