package com.caseforge.scanner.obd.j1850

/**
 * Parses the raw text an [ElmSerialTransport] returns for the SKIM status
 * request into a [SkreemReadResult]. Pure function, no I/O - all fixture
 * data lives in the test suite.
 */
object SkimResponseParser {

    /** ELM327 tokens that mean "nothing came back" rather than "here is data". */
    private val NEGATIVE_TOKENS = listOf(
        "NO DATA", "UNABLE TO CONNECT", "BUS INIT", "STOPPED", "CAN ERROR", "TIMEOUT", "SEARCHING"
    )

    /** Valid VIN character set: A-Z except I/O/Q, plus 0-9 (SAE J853 / ISO 3779). */
    private val VIN_CHARSET: Set<Char> = ('A'..'Z').filterNot { it in "IOQ" }.toSet() + ('0'..'9').toSet()

    fun parse(rawResponse: String): SkreemReadResult {
        val trimmed = rawResponse.trim()
        val normalized = trimmed.uppercase()

        if (trimmed.isEmpty() || normalized == "?" || NEGATIVE_TOKENS.any { normalized.contains(it) }) {
            return SkreemReadResult(
                modulePresent = false,
                immobilizerStatus = "NO_RESPONSE",
                keyCount = null,
                vinEcho = null,
                rawHex = trimmed,
                outcome = SkimReadOutcome.NO_RESPONSE,
                detail = "Adapter/module reported no data for the SKIM status request."
            )
        }

        val frames = J1850Frame.parseElmHexText(trimmed, headerLength = 1)

        if (frames.isEmpty()) {
            return SkreemReadResult(
                modulePresent = false,
                immobilizerStatus = "MALFORMED",
                keyCount = null,
                vinEcho = null,
                rawHex = trimmed,
                outcome = SkimReadOutcome.MALFORMED_RESPONSE,
                detail = "Response text did not parse as valid hex frame(s)."
            )
        }

        val primary = frames.firstOrNull { it.pciId == SkimVpwReader.SKIM_STATUS_PCI_ID }

        if (primary == null) {
            // We heard *something* on the bus, but not the SKIM status ID we
            // asked for - treat as no usable module response rather than
            // guessing at an unrelated module's frame.
            return SkreemReadResult(
                modulePresent = false,
                immobilizerStatus = "NO_RESPONSE",
                keyCount = null,
                vinEcho = extractVinEcho(frames),
                rawHex = trimmed,
                outcome = SkimReadOutcome.NO_RESPONSE,
                detail = "No frame with PCI ID 0x${"%02X".format(SkimVpwReader.SKIM_STATUS_PCI_ID)} (SKIM status) in response."
            )
        }

        if (primary.crc == null || !primary.isCrcValid || primary.data.isEmpty()) {
            return SkreemReadResult(
                modulePresent = false,
                immobilizerStatus = "MALFORMED",
                keyCount = null,
                vinEcho = extractVinEcho(frames),
                rawHex = trimmed,
                outcome = SkimReadOutcome.MALFORMED_RESPONSE,
                detail = when {
                    primary.crc == null -> "SKIM frame truncated (missing CRC byte)."
                    !primary.isCrcValid -> "SKIM frame CRC check failed."
                    else -> "SKIM frame had no status data byte."
                }
            )
        }

        val statusByte = primary.data[0]
        val keyCount = primary.data.getOrNull(1)

        return SkreemReadResult(
            modulePresent = true,
            immobilizerStatus = describeStatus(statusByte),
            keyCount = keyCount,
            vinEcho = extractVinEcho(frames),
            rawHex = trimmed,
            outcome = SkimReadOutcome.MODULE_PRESENT,
            detail = null
        )
    }

    /**
     * PROVISIONAL status-byte decoding. We only have confirmed evidence
     * (PCM ROM disassembly, see SkimVpwReader) that PCI ID 0xB1 = "SKIM
     * status" carries a single status byte - NOT what each value of that
     * byte means. This mapping is an educated placeholder pending a real
     * capture correlated against known dash states (immobilizer lamp off /
     * solid / flashing). Anything not in the table falls back to a
     * hex-labelled UNKNOWN so nothing is silently misreported as ARMED or
     * DISARMED.
     */
    private fun describeStatus(statusByte: Int): String = when (statusByte and 0xFF) {
        0x00 -> "DISARMED_KEY_VALID"
        0x01 -> "ARMED"
        0x02 -> "FAULT"
        else -> "UNKNOWN_CODE(0x${"%02X".format(statusByte and 0xFF)})"
    }

    /**
     * Reassembles a VIN echo from the data bytes of every frame in the
     * response OTHER than the SKIM status frame itself (whose data byte(s)
     * are a status/key-count code, not ASCII). This is a speculative
     * capability: we have no confirmed PCI ID for a VIN-bearing SKIM
     * response, only that some Chrysler modules of this era are known to
     * hold/compare the VIN internally as part of their anti-theft logic.
     * Concatenates in frame order; only treated as a VIN if the result is
     * exactly 17 characters from the valid VIN character set.
     */
    private fun extractVinEcho(frames: List<J1850Frame>): String? {
        val candidateBytes = frames
            .filterNot { it.pciId == SkimVpwReader.SKIM_STATUS_PCI_ID }
            .flatMap { it.data }
        if (candidateBytes.size != 17) return null
        val text = candidateBytes.joinToString("") { (it and 0x7F).toChar().toString() }
        return if (text.all { it in VIN_CHARSET }) text else null
    }

    // =====================================================================
    // PASSIVE MONITOR STREAM path (e.g. ELM327 `ATMA`) - CONFIRMED against
    // a real 2004-2006 Jeep capture, see below. Completely separate from
    // [parse] above, which stays exactly as it was for the single active
    // request/response shape the original 40-test suite covers. Real
    // vehicles broadcast SKIM status unsolicited; they do not answer an
    // active "B1 00" request (confirmed: that request returned "NO DATA"
    // every time in the real capture - see
    // src/test/resources/real/skreem_jeep_2006.log, STEP 2).
    // =====================================================================

    /**
     * Parses a PASSIVE MONITOR STREAM - a sequence of candidate frame
     * tokens as ELM327 prints them one-per-line under `ATMA`, e.g.
     * `"B1B100D2"` or `"B100FA<DATA ERROR"` - into a [SkreemReadResult].
     *
     * CONFIRMED against a real 2004-2006 Jeep capture
     * (`src/test/resources/real/skreem_jeep_2006.log`, ELM327 `ATMA`
     * monitor dump, VPW protocol 2 confirmed):
     *  - SKIM status is a **passive broadcast** on PCI ID `0xB1`, not
     *    something the active `"B1 00"` request elicits.
     *  - The real capture also contained 191x `"B100FA<DATA ERROR"` and
     *    11x `"B100<DATA ERROR"` - ELM-flagged collision/IFR garble that
     *    must never be mistaken for a real frame. The *only* frame in the
     *    whole ~1880-line capture that was both un-flagged by ELM AND
     *    CRC-valid with PCI ID `0xB1` was `"B1B100D2"` (heard twice).
     *  - Critically, `"B100FA"` is *arithmetically* CRC-valid as its own
     *    3-byte frame (CRC-8/SAE-J1850 of `[0xB1, 0x00]` really does
     *    equal `0xFA`) despite being bus garbage - proof that CRC math
     *    ALONE is not sufficient to filter this stream. The ELM inline
     *    error annotation must be an independent, equally authoritative
     *    discard condition, not a fallback only checked when CRC fails.
     *
     * Two-gate discard, applied to every token before it is trusted:
     *  1. Any token carrying an ELM inline error annotation (matched
     *     loosely: contains `"DATA ERROR"` or a stray `'<'`, which never
     *     appears in clean hex) is discarded outright, regardless of what
     *     its bytes would otherwise compute to.
     *  2. Whatever parses as a well-formed hex frame after that is still
     *     required to pass [J1850Frame.isCrcValid] - catches ordinary
     *     short/garbled frames the adapter didn't flag itself. (In the
     *     confirmed capture, every single ELM-clean frame also happened to
     *     be CRC-valid with zero exceptions - this second gate is a
     *     defensive belt-and-suspenders check, not one observed to matter
     *     yet.)
     *
     * Of the frames that survive both gates, the *first* with PCI ID
     * `0xB1` (see [SkimVpwReader.SKIM_STATUS_PCI_ID]) sets
     * [SkreemReadResult.modulePresent] = true. [VinBroadcastParser] runs
     * over the same survivors regardless of whether a SKIM frame was
     * found, mirroring [parse]'s existing "still try VIN echo" behavior
     * for its negative-result branch.
     *
     * Unlike [parse], this function never returns
     * [SkimReadOutcome.MALFORMED_RESPONSE] - "discard" means skip, not
     * escalate; a monitor stream is expected to contain plenty of noise
     * and unrelated traffic by nature, so only [SkimReadOutcome.MODULE_PRESENT]
     * (a valid SKIM frame was found) or [SkimReadOutcome.NO_RESPONSE] (it
     * wasn't) are produced.
     */
    fun parseMonitorStream(candidateFrames: List<String>): SkreemReadResult {
        val validFrames = mutableListOf<Pair<String, J1850Frame>>()
        for (token in candidateFrames) {
            val trimmed = token.trim()
            if (trimmed.isEmpty() || looksLikeElmInlineError(trimmed)) continue
            val hexOnly = trimmed.filterNot { it.isWhitespace() }
            if (hexOnly.isEmpty() || hexOnly.length % 2 != 0) continue
            if (!hexOnly.all { it.isDigit() || it.uppercaseChar() in 'A'..'F' }) continue
            val bytes = hexOnly.chunked(2).map { it.toInt(16) }
            val frame = J1850Frame.fromBytes(bytes, headerLength = 1) ?: continue
            if (!frame.isCrcValid) continue
            validFrames.add(trimmed to frame)
        }

        val vinEcho = VinBroadcastParser.reassemble(validFrames.map { it.second })
        val skimEntry = validFrames.firstOrNull { (_, frame) ->
            frame.pciId == SkimVpwReader.SKIM_STATUS_PCI_ID && frame.data.isNotEmpty()
        }

        if (skimEntry == null) {
            return SkreemReadResult(
                modulePresent = false,
                immobilizerStatus = "NO_RESPONSE",
                keyCount = null,
                vinEcho = vinEcho,
                rawHex = "",
                outcome = SkimReadOutcome.NO_RESPONSE,
                detail = "Scanned ${candidateFrames.size} candidate frame(s) in the monitor stream; " +
                    "none was a CRC-valid, non-DATA-ERROR 0x${"%02X".format(SkimVpwReader.SKIM_STATUS_PCI_ID)} " +
                    "(SKIM status) broadcast."
            )
        }

        val (rawHex, frame) = skimEntry
        return SkreemReadResult(
            modulePresent = true,
            immobilizerStatus = describeBroadcastStatus(frame.data.last()),
            keyCount = null,
            vinEcho = vinEcho,
            rawHex = rawHex,
            outcome = SkimReadOutcome.MODULE_PRESENT,
            detail = null
        )
    }

    /** True for tokens carrying an ELM327 inline error annotation (e.g. "B100FA<DATA ERROR") rather than clean frame hex. */
    private fun looksLikeElmInlineError(token: String): Boolean =
        token.contains("DATA ERROR", ignoreCase = true) || token.contains('<')

    /**
     * PROVISIONAL status-byte decoding for the PASSIVE BROADCAST `0xB1`
     * frame shape confirmed by the real 2006 Jeep capture (header=`0xB1`,
     * data=`[0xB1, statusByte]` - i.e. the PCI ID is echoed as the first
     * data byte, and the second (last) data byte is treated as the actual
     * status; see [parseMonitorStream]). This is a SEPARATE table from
     * [describeStatus] above, which decodes the older, still-unconfirmed
     * single active-response frame shape (`data=[statusByte]`) that
     * predates this capture and remains untouched for the existing 40-test
     * suite.
     *
     * We only have ONE confirmed real-vehicle sample so far - status byte
     * `0x00`, captured with the vehicle's ignition ON and no other known
     * state correlated against it - so this table is explicitly
     * PROVISIONAL with exactly one entry. `0x00` is mapped to a
     * "secured / key-learned" reading: on Chrysler SKIM modules, the
     * quiescent/no-fault broadcast state during normal running (key
     * recognized, no immobilizer fault) is the most plausible reading of
     * an all-zero status byte, but this is an inference, not a
     * dash-state-correlated confirmation. Anything else falls back to a
     * hex-labelled UNKNOWN so a future differently-valued capture is never
     * silently misreported as secured. See README.md "Open items" - more
     * captures in other key states (key out, wrong key, security lamp
     * flashing) are needed to firm this table up.
     */
    private fun describeBroadcastStatus(statusByte: Int): String = when (statusByte and 0xFF) {
        0x00 -> "SECURED_KEY_LEARNED_PROVISIONAL(0x00)"
        else -> "UNKNOWN_CODE(0x${"%02X".format(statusByte and 0xFF)})"
    }
}
