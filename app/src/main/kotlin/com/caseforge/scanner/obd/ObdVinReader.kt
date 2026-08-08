package com.caseforge.scanner.obd

import java.io.ByteArrayOutputStream

/**
 * Mode 09 PID 02 (VIN). Accepts either a reassembled OBD payload (`49 02 01` + 17 ASCII bytes)
 * or raw ISO-15765 first / consecutive frames for unit tests and future transport.
 */
object ObdVinReader {

    private const val VIN_LENGTH = 17

    /**
     * Parses VIN from a completed OBD response: `49 02 [messageCount]` + 17× ASCII.
     */
    fun parseFromReassembled(obdPayload: ByteArray): String? {
        if (obdPayload.size < 3 + VIN_LENGTH) return null
        val svc = obdPayload[0].toInt() and 0xFF
        if (svc != ObdModes.positiveResponse(ObdModes.MODE_VEHICLE_INFO)) return null
        val pid = obdPayload[1].toInt() and 0xFF
        if (pid != ObdModes.Pid09.VIN) return null
        // Byte 2 is message index / count per SAE J1979 for VIN; data follows.
        val vinBytes = obdPayload.copyOfRange(3, 3 + VIN_LENGTH)
        if (!vinBytes.all { it in 0x20..0x7E }) return null
        return vinBytes.toString(Charsets.US_ASCII)
    }

    /**
     * Reassembles ISO-TP (multi-frame) **ECU response** CAN payloads, then parses VIN layer.
     * Frames are typical 8-byte CAN data fields; Flow Control frames from tester are omitted.
     */
    fun parseFromIsoTpFrames(frames: List<ByteArray>): String? {
        val obd = reassembleIsoTp(frames) ?: return null
        return parseFromReassembled(obd)
    }

    /** Accumulates Rx ISO-TP frames; yields full OBD payload when multi-frame completes. */
    fun newRxAssembler(): IsoTpAssembler {
        return object : IsoTpAssembler {
            private val buffer = mutableListOf<ByteArray>()
            override fun appendFrame(frame: ByteArray): ByteArray? {
                buffer.add(frame.copyOf(frame.size))
                val obd = reassembleIsoTp(buffer) ?: return null
                buffer.clear()
                return obd
            }
        }
    }

    /** Visible for tests — ISO 15765-2 classic CAN 8-byte framing. */
    internal fun reassembleIsoTp(frames: List<ByteArray>): ByteArray? {
        if (frames.isEmpty()) return null
        val first = frames[0]
        if (first.isEmpty()) return null
        val pci = first[0].toInt() and 0xFF
        return when (pci and 0xF0) {
            0x00 -> parseSingleFrame(first)
            0x10 -> parseMultiFrame(frames)
            else -> null
        }
    }

    private fun parseSingleFrame(first: ByteArray): ByteArray? {
        val pci = first[0].toInt() and 0xFF
        val sfDl = pci and 0x0F
        if (sfDl != 0) {
            if (first.size <= sfDl) return null
            return first.copyOfRange(1, 1 + sfDl)
        }
        // ESC / longer SF (CAN FD-style length in byte 1); handle minimal Tier-0 path
        if (first.size < 2) return null
        val len = first[1].toInt() and 0xFF
        if (first.size < 2 + len) return null
        return first.copyOfRange(2, 2 + len)
    }

    private fun parseMultiFrame(frames: List<ByteArray>): ByteArray? {
        val first = frames[0]
        if (first.size < 2) return null
        val total = (((first[0].toInt() and 0x0F) shl 8) or (first[1].toInt() and 0xFF))
        if (total <= 0) return null
        val out = ByteArrayOutputStream(total)
        // First frame carries 6 data bytes starting at index 2
        var idx = 2
        var remaining = total
        while (idx < first.size && remaining > 0) {
            out.write(first[idx].toInt() and 0xFF)
            idx++
            remaining--
        }
        var expectedSn = 1
        var frameIndex = 1
        while (remaining > 0 && frameIndex < frames.size) {
            val cf = frames[frameIndex++]
            if (cf.isEmpty()) return null
            val cfPci = cf[0].toInt() and 0xFF
            if ((cfPci and 0xF0) != 0x20) return null
            val sn = cfPci and 0x0F
            if (sn != (expectedSn and 0x0F)) return null
            expectedSn = if (expectedSn == 15) 0 else expectedSn + 1
            var j = 1
            while (j < cf.size && remaining > 0) {
                out.write(cf[j].toInt() and 0xFF)
                j++
                remaining--
            }
        }
        return if (remaining == 0) out.toByteArray() else null
    }
}
