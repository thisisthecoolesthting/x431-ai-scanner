package com.caseforge.scanner.vci

/**
 * Wire-level frame model for the OEM VCI binary protocol.
 *
 * Verified frame layout (from CommunicationCOM.comReceiveData + ByteHexHelper decompile):
 *
 *   Offset  Size   Field
 *   ------  -----  -----
 *   0       2      Header magic  (0x?? 0x?? — exact bytes still obfuscated in NDK)
 *   2       2      Opcode / command category word
 *   4       2      Payload length, big-endian  (bArr[4]*256 + bArr[5])
 *   6       N      Payload bytes
 *   6+N     1      XOR checksum over bytes[2 .. 6+N-1] inclusive
 *
 * Total minimum frame size = 7 bytes (header + opcode + length + empty payload + checksum).
 *
 * Transport encoding: the LocalSocketClient sends/receives frames as hex-encoded ASCII
 * lines (one frame per line).  ByteHexHelper.hexStringToBytes() and bytesToHexString()
 * are the codec — every byte is two uppercase hex chars.
 *
 * Checksum algorithm (CommunicationCOM.getCrcByDataLength, verified):
 *   xor = 0
 *   for i in [startIdx, startIdx+length):   xor ^= byte[i] & 0xFF
 *   checksum stored at frame[totalLen-1]
 *   startIdx = 2  (from opcode word onward)
 *   length   = totalBytes - 3  (excludes the 2-byte header and the checksum itself)
 *
 * Voltage suffix (optional, firmware-version dependent):
 *   Some firmware appends an extra 2-byte voltage word AFTER the checksum.  When
 *   DiagnoseConstants.isVoltageShow is true, the app strips it before parsing.
 *   VciFrame.decode() handles this transparently via the [hasVoltageSuffix] flag.
 */
data class VciFrame(
    val header: ByteArray,      // 2 bytes — magic, partially unknown
    val opcode: Int,            // 2-byte unsigned value (bytes[2..3])
    val payload: ByteArray,     // N bytes
    val checksum: Byte,         // trailing XOR byte
) {

    // ------------------------------------------------------------------
    // Derived helpers
    // ------------------------------------------------------------------

    /** True if the checksum field matches a re-computation from opcode + payload. */
    val isChecksumValid: Boolean
        get() {
            val expected = computeChecksum(opcode, payload)
            return checksum == expected
        }

    /** Total wire bytes including header, opcode, length word, payload, checksum. */
    val wireSize: Int get() = HEADER_SIZE + OPCODE_SIZE + LENGTH_SIZE + payload.size + CHECKSUM_SIZE

    // ------------------------------------------------------------------
    // encode — produce raw bytes for transmission
    // ------------------------------------------------------------------

    fun encode(): ByteArray {
        val cs = computeChecksum(opcode, payload)
        val out = ByteArray(wireSize)
        // Header
        out[0] = header[0]
        out[1] = header[1]
        // Opcode (big-endian)
        out[2] = ((opcode ushr 8) and 0xFF).toByte()
        out[3] = (opcode and 0xFF).toByte()
        // Length (big-endian, payload byte count)
        val payloadLen = payload.size
        out[4] = ((payloadLen ushr 8) and 0xFF).toByte()
        out[5] = (payloadLen and 0xFF).toByte()
        // Payload
        payload.copyInto(out, destinationOffset = 6)
        // Checksum
        out[6 + payloadLen] = cs
        return out
    }

    /** Encode to hex-ASCII string suitable for writing to the LocalSocket line reader. */
    fun encodeHex(): String = encode().toHexString()

    // ------------------------------------------------------------------
    // equals / hashCode — ByteArray fields require manual impl
    // ------------------------------------------------------------------

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is VciFrame) return false
        return header.contentEquals(other.header) &&
            opcode == other.opcode &&
            payload.contentEquals(other.payload) &&
            checksum == other.checksum
    }

    override fun hashCode(): Int {
        var result = header.contentHashCode()
        result = 31 * result + opcode
        result = 31 * result + payload.contentHashCode()
        result = 31 * result + checksum
        return result
    }

    override fun toString(): String =
        "VciFrame(opcode=0x${opcode.toString(16).uppercase().padStart(4, '0')}" +
            " payloadLen=${payload.size}" +
            " checksum=0x${checksum.toInt().and(0xFF).toString(16).uppercase().padStart(2, '0')}" +
            " valid=$isChecksumValid)"

    // ------------------------------------------------------------------
    companion object {

        // Fixed field sizes
        const val HEADER_SIZE   = 2
        const val OPCODE_SIZE   = 2
        const val LENGTH_SIZE   = 2
        const val CHECKSUM_SIZE = 1
        const val MIN_FRAME_SIZE = HEADER_SIZE + OPCODE_SIZE + LENGTH_SIZE + CHECKSUM_SIZE // = 7

        /**
         * Best-known header magic bytes.
         *
         * STATUS: PARTIALLY CONFIRMED.
         * The Java layer does not expose them directly — they are assembled in native
         * code (CommunicationCOM.receiveData JNI).  The values below (0x55 0xAA) are
         * the most common framing magic in OEM VCI OBD protocols observed in
         * older open-source implementations and are a reasonable spike placeholder.
         *
         * ACTION REQUIRED: confirm via Frida hook on LocalSocketClient.send() or
         * packet capture before treating these as authoritative.
         */
        val DEFAULT_HEADER = byteArrayOf(0x55.toByte(), 0xAA.toByte())

        /** Common OEM VCI header candidates — swept on tablet via Direct VCI probe. */
        val HEADER_CANDIDATES: List<ByteArray> = VciProtocolConfig.HEADER_CANDIDATES

        // ------------------------------------------------------------------
        // decode
        // ------------------------------------------------------------------

        /**
         * Decode a single frame from raw bytes.
         *
         * @param raw             The raw byte array (full frame).
         * @param offset          Start offset within [raw].
         * @param hasVoltageSuffix When true, strips the trailing 2-byte voltage word
         *                        before parsing (matches firmware where isVoltageShow=true).
         * @return [DecodeResult.Ok] with the frame, or [DecodeResult.Error] with a reason.
         */
        fun decode(
            raw: ByteArray,
            offset: Int = 0,
            hasVoltageSuffix: Boolean = false,
        ): DecodeResult {
            val effectiveEnd = if (hasVoltageSuffix) raw.size - 2 else raw.size
            val available = effectiveEnd - offset

            if (available < MIN_FRAME_SIZE) {
                return DecodeResult.Error(
                    "Frame too short: need $MIN_FRAME_SIZE bytes, got $available"
                )
            }

            val header = byteArrayOf(raw[offset], raw[offset + 1])

            val opcode = ((raw[offset + 2].toInt() and 0xFF) shl 8) or
                         (raw[offset + 3].toInt() and 0xFF)

            val payloadLen = ((raw[offset + 4].toInt() and 0xFF) shl 8) or
                             (raw[offset + 5].toInt() and 0xFF)

            val expectedTotal = HEADER_SIZE + OPCODE_SIZE + LENGTH_SIZE + payloadLen + CHECKSUM_SIZE
            if (available < expectedTotal) {
                return DecodeResult.Error(
                    "Truncated payload: length field says $payloadLen bytes" +
                        " but only ${available - MIN_FRAME_SIZE + 1} bytes available"
                )
            }

            val payload = raw.copyOfRange(offset + 6, offset + 6 + payloadLen)
            val checksum = raw[offset + 6 + payloadLen]

            val frame = VciFrame(
                header = header,
                opcode = opcode,
                payload = payload,
                checksum = checksum,
            )

            return if (frame.isChecksumValid) {
                DecodeResult.Ok(frame, bytesConsumed = expectedTotal)
            } else {
                // Return the frame anyway so the caller can decide — some firmware
                // uses a voltage-patched checksum that we recompute after stripping.
                DecodeResult.ChecksumMismatch(
                    frame = frame,
                    bytesConsumed = expectedTotal,
                    expected = computeChecksum(opcode, payload),
                )
            }
        }

        /**
         * Decode a hex-ASCII line as received from the LocalSocket BufferedReader.
         * Empty / null lines return [DecodeResult.Error].
         */
        fun decodeHex(hexLine: String?, hasVoltageSuffix: Boolean = false): DecodeResult {
            if (hexLine.isNullOrBlank()) return DecodeResult.Error("Empty hex line")
            val raw = hexLine.trim().hexToByteArray()
                ?: return DecodeResult.Error("Invalid hex encoding: '$hexLine'")
            return decode(raw, hasVoltageSuffix = hasVoltageSuffix)
        }

        // ------------------------------------------------------------------
        // builder convenience
        // ------------------------------------------------------------------

        /**
         * Build a frame with the default header magic.
         * Checksum is computed automatically.
         */
        fun build(
            opcode: Int,
            payload: ByteArray = ByteArray(0),
            header: ByteArray = VciProtocolConfig.header,
        ): VciFrame {
            return VciFrame(
                header   = header.copyOf(),
                opcode   = opcode,
                payload  = payload,
                checksum = computeChecksum(opcode, payload),
            )
        }

        // ------------------------------------------------------------------
        // XOR checksum (mirrors CommunicationCOM.getCrcByDataLength exactly)
        // ------------------------------------------------------------------

        /**
         * Compute the XOR checksum over the opcode word + payload.
         *
         * Mirrors CommunicationCOM.getCrcByDataLength(bArr, startIdx=2, length=totalLen-3):
         *   - startIdx=2 means we start at the opcode bytes, skipping the 2-byte header
         *   - length = (total - 3) = opcode(2) + payloadLen(2) + payload(N) - 1
         *     Wait — let's be precise:
         *
         *   total frame bytes = 2 (hdr) + 2 (opc) + 2 (len) + N (payload) + 1 (cs) = 7+N
         *   getCrcByDataLength(arr, 2, total-3) = getCrcByDataLength(arr, 2, 4+N)
         *   That covers: opc[0], opc[1], len[0], len[1], payload[0..N-1]
         *
         * We reconstruct this from (opcode, payload) without needing the full frame array.
         */
        fun computeChecksum(opcode: Int, payload: ByteArray): Byte {
            var xor = 0
            // Opcode bytes (2)
            xor = xor xor ((opcode ushr 8) and 0xFF)
            xor = xor xor (opcode and 0xFF)
            // Length bytes (2) — these are included in the checksum scope
            val payloadLen = payload.size
            xor = xor xor ((payloadLen ushr 8) and 0xFF)
            xor = xor xor (payloadLen and 0xFF)
            // Payload bytes
            for (b in payload) {
                xor = xor xor (b.toInt() and 0xFF)
            }
            return xor.toByte()
        }
    }

    // ------------------------------------------------------------------
    // Decode result sum type
    // ------------------------------------------------------------------

    sealed class DecodeResult {
        data class Ok(val frame: VciFrame, val bytesConsumed: Int) : DecodeResult()
        data class ChecksumMismatch(
            val frame: VciFrame,
            val bytesConsumed: Int,
            val expected: Byte,
        ) : DecodeResult()
        data class Error(val reason: String) : DecodeResult()
    }
}

// ------------------------------------------------------------------
// Extension functions (ByteArray <-> hex string)
// Mirrors ByteHexHelper without the Android dependency.
// ------------------------------------------------------------------

fun ByteArray.toHexString(): String {
    val sb = StringBuilder(size * 2)
    for (b in this) {
        val i = b.toInt() and 0xFF
        if (i < 16) sb.append('0')
        sb.append(i.toString(16).uppercase())
    }
    return sb.toString()
}

fun String.hexToByteArray(): ByteArray? {
    val s = this.uppercase().trim()
    if (s.length % 2 != 0) return null
    return try {
        ByteArray(s.length / 2) { i ->
            ((s[i * 2].digitToInt(16) shl 4) or s[i * 2 + 1].digitToInt(16)).toByte()
        }
    } catch (_: IllegalArgumentException) {
        null
    }
}
