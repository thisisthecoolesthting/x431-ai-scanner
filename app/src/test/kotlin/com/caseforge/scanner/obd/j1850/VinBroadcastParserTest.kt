package com.caseforge.scanner.obd.j1850

import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull

/**
 * [VinBroadcastParser] tests. The five `F0...` hex strings below are copied
 * verbatim from the confirmed real 2004-2006 Jeep capture
 * (`src/test/resources/real/skreem_jeep_2006.log`) - not fabricated - see
 * [VinBroadcastParser]'s class doc for the full frame-by-frame breakdown.
 */
class VinBroadcastParserTest {

    private fun parse(hex: String): J1850Frame =
        requireNotNull(J1850Frame.parseElmHexText(hex).firstOrNull()) { "fixture hex did not parse: $hex" }

    private val realVinFrames = listOf(
        "F0013162",       // idx=1  "1"
        "F0024A34474B20", // idx=2  "J4GK"
        "F00634384B3898", // idx=6  "48K8"
        "F00A36573137F9", // idx=10 "6W17"
        "F00E313531394D"  // idx=14 "1519"
    ).map(::parse)

    @Test
    fun `reassembles the real 2006 Jeep VIN from the five confirmed F0 frames`() {
        assertEquals("1J4GK48K86W171519", VinBroadcastParser.reassemble(realVinFrames))
    }

    @Test
    fun `reassembly is order-independent`() {
        val shuffled = listOf(realVinFrames[3], realVinFrames[0], realVinFrames[4], realVinFrames[1], realVinFrames[2])
        assertEquals("1J4GK48K86W171519", VinBroadcastParser.reassemble(shuffled))
    }

    @Test
    fun `ignores frames with a different PCI ID mixed into the list`() {
        val withNoise = realVinFrames + parse("B1B100D2") + parse("100000000000E2")
        assertEquals("1J4GK48K86W171519", VinBroadcastParser.reassemble(withNoise))
    }

    @Test
    fun `missing a chunk (gap in VIN positions) returns null rather than a partial VIN`() {
        val missingOneChunk = realVinFrames.filterNot { it.toHexString() == "F00A36573137F9" }
        assertNull(VinBroadcastParser.reassemble(missingOneChunk))
    }

    @Test
    fun `empty input returns null`() {
        assertNull(VinBroadcastParser.reassemble(emptyList()))
    }

    @Test
    fun `a character outside the VIN charset invalidates the whole reassembly`() {
        // Same shape as the real idx=2 frame but with 'I' (0x49) substituted for 'J' (0x4A) -
        // 'I' is explicitly excluded from the VIN charset (SAE J853 / ISO 3779 excludes I/O/Q to
        // avoid confusion with 1/0). CRC recomputed for real so this is a well-formed frame that
        // only fails the charset gate, not CRC.
        val header = listOf(0xF0)
        val data = listOf(0x02, 'I'.code, '4'.code, 'G'.code, 'K'.code)
        val crc = J1850Crc.compute(header + data)
        val badCharFrame = parse((header + data + crc).joinToString("") { "%02X".format(it) })

        val frames = realVinFrames.filterNot { it.toHexString() == "F0024A34474B20" } + badCharFrame
        assertNull(VinBroadcastParser.reassemble(frames))
    }
}
