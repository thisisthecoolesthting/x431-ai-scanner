package com.caseforge.scanner.obd

/**
 * Parses stored (Mode 03) and pending (Mode 07) DTC lists from positive OBD responses.
 *
 * Expected frames start with [ObdModes.positiveResponse] for the respective mode (`0x43`, `0x47`).
 * Each DTC is two bytes; `0x00 0x00` is padding / end.
 */
object ObdDtcReader {

    fun parseStored(response: ByteArray): List<ObdDtc> =
        parsePairs(response, expectedService = ObdModes.positiveResponse(ObdModes.MODE_SHOW_STORED_DTCS), pending = false)

    fun parsePending(response: ByteArray): List<ObdDtc> =
        parsePairs(response, expectedService = ObdModes.positiveResponse(ObdModes.MODE_SHOW_PENDING_DTCS), pending = true)

    private fun parsePairs(response: ByteArray, expectedService: Int, pending: Boolean): List<ObdDtc> {
        if (response.isEmpty()) return emptyList()
        require(response[0].toInt() and 0xFF == expectedService) {
            "Unexpected service 0x${response[0].toInt() and 0xFF}, expected 0x$expectedService"
        }
        val out = ArrayList<ObdDtc>()
        var i = 1
        while (i + 1 < response.size) {
            val hi = response[i].toInt() and 0xFF
            val lo = response[i + 1].toInt() and 0xFF
            i += 2
            if (hi == 0 && lo == 0) break
            out.add(ObdDtc(code = formatDtc(hi, lo), description = null, pending = pending))
        }
        return out
    }

    /** SAE J2012 two-byte DTC encoding → `P0xxx` / `C0xxx` / `B0xxx` / `U0xxx` style. */
    fun formatDtc(b1: Int, b2: Int): String {
        val typeChar = when ((b1 shr 6) and 0x03) {
            0 -> 'P'
            1 -> 'C'
            2 -> 'B'
            else -> 'U'
        }
        val digit1 = (b1 shr 4) and 0x03
        val digit2 = b1 and 0x0F
        val digit3 = (b2 shr 4) and 0x0F
        val digit4 = b2 and 0x0F
        return "%c%d%X%X%X".format(typeChar, digit1, digit2, digit3, digit4)
    }
}
