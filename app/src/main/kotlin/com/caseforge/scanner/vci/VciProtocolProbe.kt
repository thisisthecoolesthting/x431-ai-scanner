package com.caseforge.scanner.vci

import android.content.Context

/**
 * Brute-force header + transport sweep for Day-1 protocol lock-in (see PHASE2-COMPLETE-PLAN).
 * Phase A: winner requires a valid Mode 03 response frame (checksum + header + opcode).
 */
object VciProtocolProbe {

  data class ProbeAttempt(
    val header: ByteArray,
    val useHex: Boolean,
    val outcome: VciProtocolConfig.ProbeOutcome,
    val dtcCount: Int,
    val detail: String,
  ) {
    val success: Boolean get() = VciProtocolConfig.isProbeWinner(outcome)
  }

  data class SweepResult(
    val winner: ProbeAttempt?,
    val attempts: List<ProbeAttempt>,
  )

  suspend fun sweep(
    context: Context,
    deviceAddress: String,
    timeoutMs: Long = 12_000L,
  ): SweepResult {
    val attempts = mutableListOf<ProbeAttempt>()
    var winner: ProbeAttempt? = null

    for (useHex in listOf(false, true)) {
      for (header in VciProtocolConfig.HEADER_CANDIDATES) {
        VciProtocolConfig.header = header.copyOf()
        val client = BluetoothVciClient(context, useHexEncoding = useHex)
        val comm = VciCommunicator(
          transport = client,
          requestTimeoutMs = timeoutMs,
          expectedHeader = header.copyOf(),
          requireValidFraming = true,
        )
        var outcome = VciProtocolConfig.ProbeOutcome.TIMEOUT
        var dtcCount = 0
        var detail = "no response"
        try {
          val connect = client.connect(deviceAddress)
          if (connect.isFailure) {
            outcome = VciProtocolConfig.ProbeOutcome.CONNECT_FAILED
            detail = connect.exceptionOrNull()?.message ?: "connect failed"
          } else {
            val dtcs = comm.readDtcs()
            dtcs.fold(
              onSuccess = { list ->
                dtcCount = list.size
                outcome = if (list.isEmpty()) {
                  VciProtocolConfig.ProbeOutcome.VALID_EMPTY
                } else {
                  VciProtocolConfig.ProbeOutcome.VALID_DTC
                }
                detail = if (list.isEmpty()) {
                  "valid Mode 03 frame; empty DTC list"
                } else {
                  "DTCs=${list.joinToString { it.code }}"
                }
              },
              onFailure = { e ->
                outcome = classifyFailure(e)
                detail = e.message ?: "readDtcs failed"
              },
            )
          }
        } catch (e: Exception) {
          outcome = classifyFailure(e)
          detail = e.message ?: e.javaClass.simpleName
        } finally {
          client.disconnect()
        }

        val attempt = ProbeAttempt(header, useHex, outcome, dtcCount, detail)
        attempts += attempt
        if (attempt.success && winner == null) winner = attempt
      }
    }

    return SweepResult(winner, attempts)
  }

  /**
   * Single-shot Mode 03 probe using current [VciProtocolConfig.header] — for connect-flow feedback.
   */
  suspend fun probeMode03(
    transport: VciTransport,
    expectedHeader: ByteArray = VciProtocolConfig.header,
    timeoutMs: Long = 12_000L,
  ): ProbeAttempt {
    val comm = VciCommunicator(
      transport = transport,
      requestTimeoutMs = timeoutMs,
      expectedHeader = expectedHeader.copyOf(),
    )
    var outcome = VciProtocolConfig.ProbeOutcome.TIMEOUT
    var dtcCount = 0
    var detail = "no response"
    try {
      comm.readDtcs().fold(
        onSuccess = { list ->
          dtcCount = list.size
          outcome = if (list.isEmpty()) {
            VciProtocolConfig.ProbeOutcome.VALID_EMPTY
          } else {
            VciProtocolConfig.ProbeOutcome.VALID_DTC
          }
          detail = if (list.isEmpty()) "valid Mode 03 frame; empty DTC list" else "DTCs=${list.size}"
        },
        onFailure = { e ->
          outcome = classifyFailure(e)
          detail = e.message ?: "readDtcs failed"
        },
      )
    } catch (e: Exception) {
      outcome = classifyFailure(e)
      detail = e.message ?: e.javaClass.simpleName
    }
    return ProbeAttempt(
      header = expectedHeader.copyOf(),
      useHex = false,
      outcome = outcome,
      dtcCount = dtcCount,
      detail = detail,
    )
  }

  private fun classifyFailure(e: Throwable): VciProtocolConfig.ProbeOutcome {
    val msg = e.message.orEmpty().lowercase()
    return when {
      msg.contains("connect") || msg.contains("bluetooth") -> VciProtocolConfig.ProbeOutcome.CONNECT_FAILED
      msg.contains("checksum") -> VciProtocolConfig.ProbeOutcome.CHECKSUM_FAIL
      msg.contains("header mismatch") -> VciProtocolConfig.ProbeOutcome.WRONG_HEADER
      msg.contains("opcode") -> VciProtocolConfig.ProbeOutcome.WRONG_OPCODE
      msg.contains("timeout") -> VciProtocolConfig.ProbeOutcome.TIMEOUT
      else -> VciProtocolConfig.ProbeOutcome.MALFORMED_FRAME
    }
  }
}
