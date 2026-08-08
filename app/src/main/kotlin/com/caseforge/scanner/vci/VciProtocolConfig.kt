package com.caseforge.scanner.vci

import com.caseforge.scanner.data.SettingsRepo

/**
 * Runtime wire-format settings (header magic + hex transport) shared by the socket client,
 * frame builder, and protocol probe UI. Persisted via [SettingsRepo].
 *
 * Phase A–B: only persist header + transport after [validateResponseFrame] passes on a live
 * Mode 03 response; gate OEM direct paths on [directVciAllowed] / [preferOemVciTransport].
 */
object VciProtocolConfig {

  /** Header bytes used by [VciFrame.build]; updated from settings or probe sweep. */
  @Volatile
  var header: ByteArray = VciFrame.DEFAULT_HEADER.copyOf()

  val HEADER_CANDIDATES: List<ByteArray> = listOf(
    byteArrayOf(0x55.toByte(), 0xAA.toByte()),
    byteArrayOf(0xAA.toByte(), 0x55.toByte()),
    byteArrayOf(0xFE.toByte(), 0x01.toByte()),
    byteArrayOf(0x40.toByte(), 0xC8.toByte()),
  )

  /** Outcome of evaluating a single probe attempt (header + transport sweep). */
  enum class ProbeOutcome {
    CONNECT_FAILED,
    TIMEOUT,
    MALFORMED_FRAME,
    WRONG_HEADER,
    CHECKSUM_FAIL,
    WRONG_OPCODE,
    VALID_EMPTY,
    VALID_DTC,
  }

  sealed class FrameValidation {
    data object Valid : FrameValidation()
    data class Invalid(val reason: String, val remediation: String? = null) : FrameValidation()
  }

  fun headerLabel(bytes: ByteArray): String =
    bytes.joinToString(" ") { "0x%02X".format(it.toInt() and 0xFF) }

  fun transportLabel(useHex: Boolean): String =
    if (useHex) "hex-ASCII" else "binary"

  fun applyFromSettings(settings: SettingsRepo) {
    header = byteArrayOf(
      settings.vciHeaderByte0.toByte(),
      settings.vciHeaderByte1.toByte(),
    )
  }

  /**
   * Persist only after field probe confirms a valid Mode 03 response frame.
   * Clears [SettingsRepo.vciProtocolConfirmed] when resetting wire defaults.
   */
  fun persistToSettings(settings: SettingsRepo, confirmedHeader: ByteArray, useHex: Boolean) {
    require(confirmedHeader.size == 2) { "VCI header must be exactly 2 bytes" }
    settings.vciHeaderByte0 = confirmedHeader[0].toInt() and 0xFF
    settings.vciHeaderByte1 = confirmedHeader[1].toInt() and 0xFF
    settings.vciUseHexEncoding = useHex
    settings.vciProtocolConfirmed = true
    applyFromSettings(settings)
  }

  fun clearConfirmation(settings: SettingsRepo) {
    settings.vciProtocolConfirmed = false
    settings.vciHeaderByte0 = VciFrame.DEFAULT_HEADER[0].toInt() and 0xFF
    settings.vciHeaderByte1 = VciFrame.DEFAULT_HEADER[1].toInt() and 0xFF
    settings.vciUseHexEncoding = false
    applyFromSettings(settings)
  }

  /** Direct OEM VCI connect/read requires the experimental toggle (Phase B gate). */
  fun directVciAllowed(settings: SettingsRepo): Boolean =
    settings.directVciExperimental || settings.nativeObdUseVci

  /** Auto-connect prefers OEM USB/BT when protocol is confirmed or native OBD routes via VCI. */
  fun preferOemVciTransport(settings: SettingsRepo): Boolean =
    settings.preferOemVciTransport()

  fun protocolStatusLine(settings: SettingsRepo): String {
    applyFromSettings(settings)
    val hdr = headerLabel(header)
    val mode = transportLabel(settings.vciUseHexEncoding)
    return when {
      settings.vciProtocolConfirmed ->
        "Confirmed: $hdr $mode"
      settings.directVciExperimental ->
        "Unconfirmed — run Direct VCI probe (header $hdr, $mode)"
      else ->
        "Direct VCI off — enable experimental toggle or use ELM327"
    }
  }

  fun oemVciGateMessage(): String =
    "Direct VCI is disabled. Enable Direct VCI (experimental) in Settings, " +
      "then run the protocol probe to lock header + transport before OEM USB/BT."

  fun probeFailureRemediation(outcome: ProbeOutcome): String = when (outcome) {
    ProbeOutcome.CONNECT_FAILED ->
      "Check Bluetooth pairing, USB OTG cable, and force-stop the OEM diagnostic app."
    ProbeOutcome.TIMEOUT ->
      "No valid Mode 03 frame — try another header candidate or toggle hex-ASCII transport."
    ProbeOutcome.MALFORMED_FRAME,
    ProbeOutcome.WRONG_HEADER,
    ProbeOutcome.CHECKSUM_FAIL,
    ProbeOutcome.WRONG_OPCODE,
    ->
      "Wrong wire format — run header sweep (binary first, then hex-ASCII)."
    ProbeOutcome.VALID_EMPTY ->
      "Protocol OK (empty DTC list). Re-test after a known fault or save as confirmed."
    ProbeOutcome.VALID_DTC ->
      "Protocol confirmed — settings can be persisted."
  }

  fun readDtcsFailureRemediation(settings: SettingsRepo, cause: Throwable?): String {
    val base = cause?.message ?: "readDtcs failed"
    return if (!settings.vciProtocolConfirmed) {
      "$base — run Direct VCI probe to confirm header (${headerLabel(header)}) " +
        "and ${transportLabel(settings.vciUseHexEncoding)} transport."
    } else {
      "$base — verify ignition ON, OEM app force-stopped, and VCI still bonded."
    }
  }

  fun matchesHeader(frame: VciFrame, expectedHeader: ByteArray = header): Boolean =
    frame.header.size == expectedHeader.size &&
      frame.header.contentEquals(expectedHeader)

  /**
   * Validates an inbound wire frame against confirmed framing rules.
   * @param expectedOpcode When set, frame opcode must match (e.g. Mode 03 response).
   */
  fun validateResponseFrame(
    frame: VciFrame,
    expectedOpcode: Int? = null,
    expectedHeader: ByteArray = header,
  ): FrameValidation {
    if (!matchesHeader(frame, expectedHeader)) {
      return FrameValidation.Invalid(
        reason = "Header mismatch: got ${headerLabel(frame.header)}, expected ${headerLabel(expectedHeader)}",
        remediation = probeFailureRemediation(ProbeOutcome.WRONG_HEADER),
      )
    }
    if (!frame.isChecksumValid) {
      return FrameValidation.Invalid(
        reason = "Checksum invalid for opcode 0x${frame.opcode.toString(16)}",
        remediation = probeFailureRemediation(ProbeOutcome.CHECKSUM_FAIL),
      )
    }
    if (expectedOpcode != null && frame.opcode != expectedOpcode) {
      return FrameValidation.Invalid(
        reason = "Unexpected opcode 0x${frame.opcode.toString(16).uppercase()} " +
          "(expected 0x${expectedOpcode.toString(16).uppercase()})",
        remediation = probeFailureRemediation(ProbeOutcome.WRONG_OPCODE),
      )
    }
    return FrameValidation.Valid
  }

  /** Classify a Mode 03 response for probe sweep winner selection. */
  fun classifyMode03ProbeFrame(
    frame: VciFrame?,
    expectedHeader: ByteArray,
  ): ProbeOutcome {
    if (frame == null) return ProbeOutcome.TIMEOUT
    return when (val v = validateResponseFrame(
      frame,
      expectedOpcode = KnownOpcode.OBD_MODE03_DTC_RESP.value,
      expectedHeader = expectedHeader,
    )) {
      is FrameValidation.Invalid -> when {
        v.reason.contains("Header mismatch") -> ProbeOutcome.WRONG_HEADER
        v.reason.contains("Checksum") -> ProbeOutcome.CHECKSUM_FAIL
        v.reason.contains("opcode") -> ProbeOutcome.WRONG_OPCODE
        else -> ProbeOutcome.MALFORMED_FRAME
      }
      is FrameValidation.Valid -> {
        val dtcs = VciCommunicator.parseDtcPayloadStatic(frame.payload)
        if (dtcs.isEmpty()) ProbeOutcome.VALID_EMPTY else ProbeOutcome.VALID_DTC
      }
    }
  }

  /** Probe winners must pass framing validation (empty DTC payload is acceptable). */
  fun isProbeWinner(outcome: ProbeOutcome): Boolean =
    outcome == ProbeOutcome.VALID_DTC || outcome == ProbeOutcome.VALID_EMPTY
}
