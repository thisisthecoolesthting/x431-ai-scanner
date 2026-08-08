package com.caseforge.scanner.obd.j1850

/**
 * Reassembles a VIN from Chrysler PCI-bus `0xF0` "VIN broadcast" indexed
 * multi-frames.
 *
 * CONFIRMED against a real 2004-2006 Jeep capture
 * (`src/test/resources/real/skreem_jeep_2006.log`, ELM327 `ATMA` passive
 * monitor dump, VPW protocol 2 confirmed). Frame shape: one-byte header
 * `0xF0`, then a data payload of `[indexByte, asciiChar, asciiChar, ...]`,
 * then the trailing CRC byte. `indexByte` is the **1-based** position of
 * the *first* ASCII character in this frame within the reassembled VIN
 * string - i.e. frame N contributes characters `indexByte .. indexByte +
 * asciiChars.size - 1`. This parser does not hardcode a fixed chunk size:
 * every real frame observed carried 4 ASCII bytes except the `idx=0x01`
 * frame, which carried only 1 (see the confirmed frames below) - both are
 * handled the same way, by placing each ASCII byte at `indexByte + offset`.
 *
 * Frames actually observed in the confirmed capture (2006 Jeep, VIN
 * `1J4GK48K86W171519`, 17 characters, position 10 = '6' -> model year
 * 2006 per SAE J853/ISO 3779 position-10 decoding):
 *
 * ```
 * F0013162        -> idx=1  "1"     (VIN chars  1)
 * F0024A34474B20  -> idx=2  "J4GK"  (VIN chars  2-5)
 * F00634384B3898  -> idx=6  "48K8"  (VIN chars  6-9)
 * F00A36573137F9  -> idx=10 "6W17"  (VIN chars 10-13)
 * F00E313531394D  -> idx=14 "1519"  (VIN chars 14-17)
 * ```
 *
 * Each of the 5 frames above was seen 9-10 times, byte-for-byte identical
 * every time, across ~35s of passive `ATMA` monitoring - i.e. these are a
 * genuinely periodic broadcast, not a one-off fluke. All 5 were CRC-valid
 * every occurrence; no DATA-ERROR/garbled variant of any `0xF0` frame was
 * observed in the capture (unlike the `0xB1` SKIM frame - see
 * [SkimResponseParser.parseMonitorStream]), but [reassemble] still only
 * trusts frames its caller has already confirmed are CRC-valid, exactly
 * like that function, so a future noisier capture is still handled safely.
 */
object VinBroadcastParser {

    /** Chrysler PCI-bus message ID for the SKIM/PCM VIN broadcast (confirmed real capture, see class doc). */
    const val VIN_BROADCAST_PCI_ID: Int = 0xF0

    /** VIN character count, per SAE J853 / ISO 3779 - used as the completeness gate below. */
    private const val VIN_LENGTH = 17

    /**
     * Valid VIN character set: A-Z except I/O/Q, plus 0-9 (SAE J853 / ISO
     * 3779). Deliberately a separate copy from [SkimResponseParser]'s
     * private one rather than a shared reference - both are one-line,
     * standard-defined constants, and keeping this parser self-contained
     * avoids coupling two otherwise-independent files together for a
     * single `val`.
     */
    private val VIN_CHARSET: Set<Char> = ('A'..'Z').filterNot { it in "IOQ" }.toSet() + ('0'..'9').toSet()

    /**
     * Reassembles a VIN from a list of already-parsed, already-CRC-validated
     * frames (callers - see [SkimResponseParser.parseMonitorStream] - are
     * responsible for discarding non-CRC-valid / ELM DATA-ERROR frames
     * *before* calling this; every frame passed in here is trusted as-is).
     * Frames with a PCI ID other than [VIN_BROADCAST_PCI_ID] are ignored,
     * so it is safe to pass a whole mixed monitor-stream frame list.
     *
     * Returns null unless the reassembled result is exactly [VIN_LENGTH]
     * characters with every position `1..VIN_LENGTH` covered by some frame
     * (no gaps) and every character in the valid VIN charset - the same
     * "all or nothing" discipline [SkimResponseParser]'s legacy
     * `extractVinEcho` uses for the single-response path, so a partial/
     * torn capture never reports a bogus partial VIN.
     */
    fun reassemble(frames: List<J1850Frame>): String? {
        val positions = mutableMapOf<Int, Char>()
        for (frame in frames) {
            if (frame.pciId != VIN_BROADCAST_PCI_ID) continue
            if (frame.data.size < 2) continue
            val startIndex = frame.data[0]
            for ((offset, byte) in frame.data.drop(1).withIndex()) {
                positions[startIndex + offset] = (byte and 0x7F).toChar()
            }
        }
        if (positions.isEmpty()) return null
        val chars = (1..VIN_LENGTH).map { positions[it] ?: return null }
        return if (chars.all { it in VIN_CHARSET }) chars.joinToString("") else null
    }
}
