package com.caseforge.scanner.planb.gateway

import com.caseforge.scanner.obd.IsoTpHandler
import com.caseforge.scanner.obd.IsoTpRxResult
import com.caseforge.scanner.obd.ObdDtcReader
import com.caseforge.scanner.obd.ObdModes
import com.caseforge.scanner.planb.body.BodyDtc
import com.caseforge.scanner.vci.Dtc
import com.caseforge.scanner.vci.VciCommunicator

/**
 * Live Plan B gateway reads over routed ISO-TP CAN identifiers via [VciCommunicator].
 *
 * Standard OBD wedge **0x7E0 → 0x7E8** uses the existing VCI Mode 03 opcode path; other ID pairs
 * use the raw CAN shim ([VciCommunicator.sendRoutedCanFrame] / [VciCommunicator.awaitRoutedCanData]).
 */
object GatewayLiveReader {

    /** Nominal aftermarket PCM request / response IDs used by [GatewayMap] defaults. */
    const val STANDARD_REQ_CAN_ID: Int = 0x7E0
    const val STANDARD_RESP_CAN_ID: Int = 0x7E8

    suspend fun readStoredDtcs(communicator: VciCommunicator, entry: EcuEntry): Result<List<BodyDtc>> {
        if (!communicator.isTransportConnected()) {
            return Result.failure(IllegalStateException("Gateway live read — VCI not connected"))
        }
        return if (entry.reqId == STANDARD_REQ_CAN_ID && entry.respId == STANDARD_RESP_CAN_ID) {
            communicator.readDtcs().map { dtcs -> dtcs.map(::toBodyDtc) }
        } else {
            readRoutedStoredDtcs(communicator, entry.reqId, entry.respId)
        }
    }

    internal suspend fun readRoutedStoredDtcs(
        communicator: VciCommunicator,
        reqCanId: Int,
        respCanId: Int,
    ): Result<List<BodyDtc>> {
        val iso = IsoTpHandler(
            emitFlowControl = { fc ->
                communicator.sendRoutedCanFrame(reqCanId, fc).getOrThrow()
            },
        )
        val request = byteArrayOf(ObdModes.MODE_SHOW_STORED_DTCS.toByte())
        for (segment in iso.buildTransmitSequence(request)) {
            communicator.sendRoutedCanFrame(reqCanId, segment).getOrThrow()
        }
        while (true) {
            val data = communicator.awaitRoutedCanData(respCanId).getOrNull() ?: break
            when (val rx = iso.ingest(data)) {
                IsoTpRxResult.NeedMore -> continue
                is IsoTpRxResult.Error ->
                    return Result.failure(IllegalStateException("ISO-TP error: ${rx.message}"))
                is IsoTpRxResult.Complete -> {
                    val dtcs = parseStoredObdPayload(rx.payload)
                    return Result.success(dtcs)
                }
            }
        }
        return Result.success(emptyList())
    }

    internal fun parseStoredObdPayload(tpPayload: ByteArray): List<BodyDtc> {
        if (tpPayload.isEmpty()) return emptyList()
        val obd = if ((tpPayload[0].toInt() and 0xFF) == ObdModes.positiveResponse(ObdModes.MODE_SHOW_STORED_DTCS)) {
            tpPayload
        } else {
            byteArrayOf(ObdModes.positiveResponse(ObdModes.MODE_SHOW_STORED_DTCS).toByte()) + tpPayload
        }
        return runCatching { ObdDtcReader.parseStored(obd) }
            .getOrDefault(emptyList())
            .map { d -> BodyDtc(code = d.code, description = d.description.orEmpty()) }
    }

    private fun toBodyDtc(dtc: Dtc): BodyDtc =
        BodyDtc(code = dtc.code, description = dtc.description.orEmpty())
}
