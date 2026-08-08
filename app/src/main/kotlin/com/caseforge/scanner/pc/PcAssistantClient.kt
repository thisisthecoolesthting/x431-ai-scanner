package com.caseforge.scanner.pc

import com.caseforge.scanner.data.SettingsRepo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Lightweight LAN client for the office-PC receiver's processing protocol.
 *
 * Host and port default from [SettingsRepo.receiverPcHost] / [receiverPcPort]
 * (same endpoint as [com.caseforge.scanner.transfer.LanPushUploader] uploads).
 *
 * Endpoints:
 * - `GET /health`
 * - `GET /process/status`
 * - `POST /process/start`
 */
class PcAssistantClient(
    private val settings: SettingsRepo,
    private val http: OkHttpClient = defaultHttp,
) {

    private val host: String get() = settings.receiverPcHost
    private val port: Int get() = settings.receiverPcPort

    private fun baseUrl(): String = "http://${host.trim()}:$port"

    suspend fun health(): Result<PcHealthInfo> = withContext(Dispatchers.IO) {
        getJson("/health") { body, latencyMs ->
            PcHealthInfo.fromJson(body, latencyMs)
                ?: error("Unrecognized /health response")
        }
    }

    suspend fun processStatus(): Result<PcProcessStatus> = withContext(Dispatchers.IO) {
        getJson("/process/status") { body, _ ->
            PcProcessStatus.fromJson(body)
                ?: error("Unrecognized /process/status response")
        }
    }

    suspend fun processStart(request: PcProcessStartRequest = PcProcessStartRequest()): Result<PcProcessStartResponse> =
        withContext(Dispatchers.IO) {
            postJson("/process/start", request.toJsonBody()) { body ->
                PcProcessStartResponse.fromJson(body)
                    ?: error("Unrecognized /process/start response")
            }
        }

    private inline fun <T> getJson(
        path: String,
        crossinline parse: (body: String, latencyMs: Long) -> T,
    ): Result<T> = runCatching {
        val t0 = System.currentTimeMillis()
        val req = Request.Builder()
            .url("${baseUrl()}$path")
            .get()
            .build()
        http.newCall(req).execute().use { resp ->
            val latencyMs = System.currentTimeMillis() - t0
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw IOException("HTTP ${resp.code}: ${body.take(200)}")
            }
            parse(body, latencyMs)
        }
    }

    private inline fun <T> postJson(
        path: String,
        jsonBody: String,
        crossinline parse: (body: String) -> T,
    ): Result<T> = runCatching {
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val req = Request.Builder()
            .url("${baseUrl()}$path")
            .post(jsonBody.toRequestBody(mediaType))
            .build()
        http.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw IOException("HTTP ${resp.code}: ${body.take(200)}")
            }
            parse(body)
        }
    }

    companion object {
        private val defaultHttp: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build()
    }
}
