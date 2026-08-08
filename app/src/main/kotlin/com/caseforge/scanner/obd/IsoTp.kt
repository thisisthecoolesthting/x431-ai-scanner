package com.caseforge.scanner.obd

/**
 * ISO 15765-2 (ISO-TP) hooks — real CAN framing lives in the VCI layer; Tier 0 keeps a small
 * contract so [ObdVinReader] tests and future wiring share the same seam.
 */
fun interface IsoTpAssembler {
    /**
     * @param frame raw CAN payload (8 bytes typical; shorter allowed in tests)
     * @return completed reassembled **OBD** payload (starts with `0x40 + mode`) once all frames received, else null
     */
    fun appendFrame(frame: ByteArray): ByteArray?
}
