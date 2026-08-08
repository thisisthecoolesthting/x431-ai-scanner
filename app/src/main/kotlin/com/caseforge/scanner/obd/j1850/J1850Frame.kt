package com.caseforge.scanner.obd.j1850

/**
 * Parsed SAE J1850 VPW frame.
 *
 * General J1850 wire format: Start-Of-Frame, a 1-byte *or* 3-byte header,
 * 0-8 data bytes, a 1-byte CRC, End-Of-Frame (SOF/EOD are electrical, not
 * part of the byte stream ELM327 hands back).
 * Reference: SAE J1850 protocol overview,
 * https://www.rfwireless-world.com/terminology/j1850-protocol-benefits-and-limitations
 * and the Motorola/NXP J1850 application note AN1212,
 * https://www.nxp.com/docs/en/application-note/AN1212.pdf
 *
 * Chrysler's PCI-bus (the physical/data-link layer SKIM rides on) uses the
 * *one-byte consolidated header* form almost exclusively for inter-module
 * broadcasts: the single header byte doubles as the message's "PCI ID"
 * (confirmed against a Chrysler PCM ROM disassembly - see SkimVpwReader.kt
 * for the full citation) rather than the 3-byte Priority/Target/Source
 * header used by standard SAE OBD-II Mode $01 PID traffic (e.g. the
 * well-documented 68/6A/F1 request -> 48/6B/10 response pair). Both header
 * shapes are modelled here since a general-purpose frame parser should be
 * able to represent either.
 *
 * With the adapter configured ATH1 (headers on), ELM327 shows the header,
 * data AND trailing CRC byte for legacy protocols (unlike CAN, where the
 * CRC is hidden) - per the ELM327 datasheet, H1 shows the complete message
 * "including the check-digits", and only the CAN CRC / J1850 IFR
 * (in-frame response) bytes are withheld. IFR bytes are stripped by the
 * adapter and are not represented here.
 */
data class J1850Frame(
    /** Header byte(s) as sent on the bus - length 1 or 3. */
    val header: List<Int>,
    /** Payload bytes between the header and the CRC. */
    val data: List<Int>,
    /** Trailing CRC byte, if the source text included one. */
    val crc: Int?
) {
    /** True if this is a Chrysler-PCI-style single-byte consolidated header frame. */
    val isOneByteHeader: Boolean get() = header.size == 1

    /** For one-byte-header frames, the header byte doubles as the PCI/message ID. */
    val pciId: Int? get() = if (isOneByteHeader) header[0] else null

    /** Three-byte-header target address (SAE OBD-II style). Null on one-byte headers. */
    val targetAddress: Int? get() = if (header.size == 3) header[1] else null

    /** Three-byte-header source address (SAE OBD-II style). Null on one-byte headers. */
    val sourceAddress: Int? get() = if (header.size == 3) header[2] else null

    /** True if [crc] is present and matches the CRC-8/SAE-J1850 of header+data. */
    val isCrcValid: Boolean get() = crc != null && crc == J1850Crc.compute(header + data)

    /** Full byte sequence: header + data + crc (if present) - for logging/rawHex. */
    fun toByteList(): List<Int> = header + data + listOfNotNull(crc)

    fun toHexString(): String = toByteList().joinToString("") { "%02X".format(it) }

    companion object {
        /**
         * Parses one frame from a flat list of bytes assumed to already be
         * in on-the-wire order. [headerLength] defaults to 1 (Chrysler
         * PCI-bus convention); pass 3 to parse standard SAE OBD-II style
         * frames. Returns null if there aren't even enough bytes for a
         * header (frame is malformed/truncated below the header itself).
         */
        fun fromBytes(bytes: List<Int>, headerLength: Int = 1): J1850Frame? {
            if (bytes.size < headerLength) return null
            val header = bytes.subList(0, headerLength)
            val rest = bytes.subList(headerLength, bytes.size)
            return if (rest.isEmpty()) {
                J1850Frame(header = header, data = emptyList(), crc = null)
            } else {
                J1850Frame(header = header, data = rest.dropLast(1), crc = rest.last())
            }
        }

        /**
         * Parses raw ELM327 output text - potentially multiple CR/LF
         * separated lines, each a run of hex digit pairs (with or without
         * spaces, regardless of the adapter's ATS0/ATS1 setting) - into
         * zero or more frames. Non-hex / status lines (e.g. "NO DATA",
         * "OK", "SEARCHING...") are skipped rather than throwing; callers
         * that care about negative responses should check for those
         * tokens themselves (see SkimResponseParser).
         */
        fun parseElmHexText(text: String, headerLength: Int = 1): List<J1850Frame> {
            return text.lineSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .mapNotNull { line -> parseHexLine(line, headerLength) }
                .toList()
        }

        /** Parses a single line of hex (spaces optional) into a frame, or null if not valid/even-length hex. */
        private fun parseHexLine(line: String, headerLength: Int): J1850Frame? {
            val compact = line.filterNot { it.isWhitespace() }
            if (compact.isEmpty() || compact.length % 2 != 0) return null
            if (!compact.all { it.isDigit() || it.uppercaseChar() in 'A'..'F' }) return null
            val bytes = compact.chunked(2).map { it.toInt(16) }
            return fromBytes(bytes, headerLength)
        }
    }
}

/**
 * CRC-8/SAE-J1850: poly 0x1D, init 0xFF, no input/output reflection,
 * XOR-out 0xFF. Standard check value for ASCII "123456789" is 0x4B -
 * verified independently and asserted as a known-answer test in
 * J1850FrameTest. Used to validate/compute the trailing CRC byte on a
 * J1850 VPW frame.
 */
object J1850Crc {
    private const val POLY = 0x1D

    fun compute(bytes: List<Int>): Int {
        var crc = 0xFF
        for (raw in bytes) {
            crc = crc xor (raw and 0xFF)
            repeat(8) {
                crc = if (crc and 0x80 != 0) {
                    ((crc shl 1) xor POLY) and 0xFF
                } else {
                    (crc shl 1) and 0xFF
                }
            }
        }
        return crc xor 0xFF
    }
}
