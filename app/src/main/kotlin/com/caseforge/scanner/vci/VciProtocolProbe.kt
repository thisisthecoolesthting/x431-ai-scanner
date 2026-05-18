package com.caseforge.scanner.vci

import android.content.Context

/**
 * Brute-force header + transport sweep for Day-1 protocol lock-in (see PHASE2-COMPLETE-PLAN).
 */
object VciProtocolProbe {

  data class ProbeAttempt(
    val header: ByteArray,
    val useHex: Boolean,
    val success: Boolean,
    val dtcCount: Int,
    val detail: String,
  )

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
        val client = VciSocketClient(context, useHexEncoding = useHex)
        val comm = VciCommunicator(client, requestTimeoutMs = timeoutMs)
        var detail = "no response"
        var dtcCount = 0
        var success = false
        try {
          val connect = client.connect(deviceAddress)
          if (connect.isFailure) {
            detail = connect.exceptionOrNull()?.message ?: "connect failed"
          } else {
            val dtcs = comm.readDtcs()
            dtcs.fold(
              onSuccess = { list ->
                dtcCount = list.size
                success = true
                detail = if (list.isEmpty()) "connected; empty DTC list (valid)" else "DTCs=${list.joinToString { it.code }}"
              },
              onFailure = { e -> detail = e.message ?: "readDtcs failed" },
            )
          }
        } catch (e: Exception) {
          detail = e.message ?: e.javaClass.simpleName
        } finally {
          client.disconnect()
        }

        val attempt = ProbeAttempt(header, useHex, success, dtcCount, detail)
        attempts += attempt
        if (success && winner == null) winner = attempt
      }
    }

    return SweepResult(winner, attempts)
  }
}
