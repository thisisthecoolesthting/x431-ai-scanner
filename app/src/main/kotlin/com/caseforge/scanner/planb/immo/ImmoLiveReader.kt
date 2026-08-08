package com.caseforge.scanner.planb.immo

import com.caseforge.scanner.planb.PlanbMarque
import com.caseforge.scanner.planb.gateway.UdsNegativeResponse
import com.caseforge.scanner.vci.ImmoUdsReadResult
import com.caseforge.scanner.vci.VciCommunicator

/**
 * Plan B immobilizer live-read lane: marque JSON [ImmoLiveReadConfig] + read-only [VciCommunicator.readImmoStatus].
 */
object ImmoLiveReader {

    private const val UDS_READ_DATA_BY_ID: Int = 0x22
    private const val UDS_POSITIVE_READ_OFFSET: Int = 0x40

    fun parseDataIdentifier(hex: String): Int? {
        val cleaned = hex.trim().removePrefix("0x").removePrefix("0X")
        if (cleaned.length !in 2..4) return null
        return cleaned.toIntOrNull(16)
    }

    suspend fun tryLiveRead(
        communicator: VciCommunicator,
        marque: PlanbMarque,
        banner: ImmoInfoBanner?,
    ): ImmoLiveStatus? {
        val config = banner?.liveRead?.takeIf { it.enabled } ?: return null
        if (!communicator.isTransportConnected()) return null
        val did = parseDataIdentifier(config.dataIdentifierHex)
            ?: return ImmoLiveStatus(
                attempted = true,
                success = false,
                summaryLine = "Invalid live-read DID \"${config.dataIdentifierHex}\" in bundled config",
                rawHex = null,
            )

        val result = communicator.readImmoStatus(config.reqCanId, config.respCanId, did)
        return result.fold(
            onSuccess = { uds -> uds.toLiveStatus(marque, banner.bannerKind) },
            onFailure = { err ->
                ImmoLiveStatus(
                    attempted = true,
                    success = false,
                    summaryLine = err.message ?: "Live immo read failed",
                    rawHex = null,
                )
            },
        )
    }

    internal fun parseUdsReadResponse(
        marque: PlanbMarque,
        bannerKind: String,
        dataIdentifier: Int,
        tpPayload: ByteArray,
    ): ImmoLiveStatus {
        if (tpPayload.isEmpty()) {
            return ImmoLiveStatus(
                attempted = true,
                success = false,
                summaryLine = "Empty UDS response",
                rawHex = null,
            )
        }
        val sid = tpPayload[0].toInt() and 0xFF
        if (sid == 0x7F) {
            val nrc = if (tpPayload.size >= 3) tpPayload[2].toInt() and 0xFF else -1
            val label = UdsNegativeResponse.fromNrc(nrc.toByte())?.name
                ?: "NRC 0x${nrc.toString(16).uppercase()}"
            return ImmoLiveStatus(
                attempted = true,
                success = false,
                summaryLine = "UDS negative ($label) — gateway may block immo DID 0x${
                    dataIdentifier.toString(16).uppercase().padStart(4, '0')
                }",
                rawHex = tpPayload.toHex(),
            )
        }
        val expectedPositive = UDS_READ_DATA_BY_ID + UDS_POSITIVE_READ_OFFSET
        if (sid != expectedPositive) {
            return ImmoLiveStatus(
                attempted = true,
                success = false,
                summaryLine = "Unexpected SID 0x${sid.toString(16)} (expected 0x${expectedPositive.toString(16)})",
                rawHex = tpPayload.toHex(),
            )
        }
        val dataBytes = if (tpPayload.size > 3) tpPayload.copyOfRange(3, tpPayload.size) else byteArrayOf()
        val summary = summarizeImmoPayload(marque, bannerKind, dataBytes)
        return ImmoLiveStatus(
            attempted = true,
            success = true,
            summaryLine = summary,
            rawHex = dataBytes.toHex(),
        )
    }

    internal fun buildReadDataByIdentifierRequest(dataIdentifier: Int): ByteArray {
        val hi = (dataIdentifier shr 8) and 0xFF
        val lo = dataIdentifier and 0xFF
        return byteArrayOf(UDS_READ_DATA_BY_ID.toByte(), hi.toByte(), lo.toByte())
    }

    private fun ImmoUdsReadResult.toLiveStatus(marque: PlanbMarque, bannerKind: String): ImmoLiveStatus =
        parseUdsReadResponse(marque, bannerKind, dataIdentifier, tpPayload)

    private fun summarizeImmoPayload(marque: PlanbMarque, bannerKind: String, data: ByteArray): String {
        if (data.isEmpty()) return "Module responded (no payload bytes)"
        val hex = data.toHex()
        return when {
            marque == PlanbMarque.FORD || bannerKind == "pats_info_only" ->
                "PATS module responded (${data.size} byte(s): $hex)"
            SkreemModule.isStellantisMarque(marque) ->
                "SKREEM/SKIM responded (${data.size} byte(s): $hex)"
            else ->
                "Immobilizer ECU responded (${data.size} byte(s): $hex)"
        }
    }

    private fun ByteArray.toHex(): String =
        joinToString(" ") { b -> (b.toInt() and 0xFF).toString(16).uppercase().padStart(2, '0') }
}

data class ImmoLiveStatus(
    val attempted: Boolean,
    val success: Boolean,
    val summaryLine: String,
    val rawHex: String?,
)
