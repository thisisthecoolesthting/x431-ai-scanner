package com.caseforge.scanner.transfer

import com.caseforge.scanner.data.SettingsRepo
import java.net.URI

/** Parsed HTTP(S) target for [LanPushUploader] / health checks. */
data class TransferEndpoint(
    val scheme: String,
    val host: String,
    val port: Int,
) {
    fun baseUrl(): String = "$scheme://$host:$port"

    fun healthUrl(): String = "${baseUrl()}/health"

    fun uploadUrl(
        fileName: String,
        size: Long,
        sha256: String,
    ): String = "${baseUrl()}/upload?name=$fileName&size=$size&sha256=$sha256"

    fun uploadHeadUrl(fileName: String): String = "${baseUrl()}/upload?name=$fileName"

    fun uploadPatchUrl(fileName: String, offset: Long): String =
        "${baseUrl()}/upload?name=$fileName&offset=$offset"

    fun uploadMultipartUrl(): String = "${baseUrl()}/upload-multipart"

    companion object {
        fun fromHostPort(host: String, port: Int, useTls: Boolean = false): TransferEndpoint {
            val scheme = if (useTls) "https" else "http"
            return TransferEndpoint(scheme, host.trim(), port)
        }

        /** Parses `http://host:8765` or `https://vps.example.com/tcw` (path ignored; port required or defaulted). */
        fun parseUrl(raw: String): Result<TransferEndpoint> = runCatching {
            val trimmed = raw.trim()
            require(trimmed.isNotBlank()) { "URL is empty" }
            val withScheme = if (!trimmed.contains("://")) "http://$trimmed" else trimmed
            val uri = URI(withScheme)
            val scheme = uri.scheme?.lowercase() ?: "http"
            require(scheme == "http" || scheme == "https") { "Use http or https only" }
            val host = uri.host ?: error("Missing host in URL")
            val port = when {
                uri.port > 0 -> uri.port
                scheme == "https" -> 443
                else -> 80
            }
            TransferEndpoint(scheme, host, port)
        }
    }
}

fun SettingsRepo.resolveTransferEndpoint(): TransferEndpoint? = when (transferDeliveryMode) {
    TransferDeliveryMode.LAN_PC -> TransferEndpoint.fromHostPort(receiverPcHost, receiverPcPort)
    TransferDeliveryMode.SELF_HOSTED -> {
        val url = transferDropUrl.trim()
        if (url.isBlank()) null else TransferEndpoint.parseUrl(url).getOrNull()
    }
    else -> null
}
