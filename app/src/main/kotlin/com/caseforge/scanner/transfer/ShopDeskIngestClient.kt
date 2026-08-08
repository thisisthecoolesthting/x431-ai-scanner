package com.caseforge.scanner.transfer



import android.util.Log

import androidx.annotation.VisibleForTesting

import com.caseforge.scanner.data.SettingsRepo

import kotlinx.coroutines.delay

import okhttp3.MediaType.Companion.toMediaType

import okhttp3.MultipartBody

import okhttp3.OkHttpClient

import okhttp3.Request

import okhttp3.RequestBody.Companion.toRequestBody

import org.json.JSONObject

import java.util.concurrent.TimeUnit



/**

 * Best-effort Shop Desk ingest with exponential-backoff retries and actionable errors for UI toasts.

 */

object ShopDeskIngestClient {



    private const val TAG = "ShopDeskIngest"

    internal const val MAX_ATTEMPTS = 3

    internal const val BASE_RETRY_DELAY_MS = 400L



    @VisibleForTesting

    @Volatile

    var skipDelaysForTests: Boolean = false



    @VisibleForTesting

    @Volatile

    private var httpClientOverride: OkHttpClient? = null



    private val defaultHttp = OkHttpClient.Builder()

        .connectTimeout(5, TimeUnit.SECONDS)

        .readTimeout(20, TimeUnit.SECONDS)

        .writeTimeout(20, TimeUnit.SECONDS)

        .build()



    @VisibleForTesting

    fun setHttpClientForTests(client: OkHttpClient?) {

        httpClientOverride = client

    }



    private fun http(): OkHttpClient = httpClientOverride ?: defaultHttp



    /** Exponential backoff: base × 2^attempt (400 ms, 800 ms, 1600 ms, …). */

    internal fun retryDelayMs(failedAttemptIndex: Int): Long =

        BASE_RETRY_DELAY_MS * (1L shl failedAttemptIndex.coerceAtMost(10))



    /** Accepts LAN `http://` and production `https://` Shop Desk ingest URLs. */
    fun isSupportedEndpoint(url: String): Boolean {
        val trimmed = url.trim()
        return trimmed.startsWith("http://") || trimmed.startsWith("https://")
    }

    fun isProductionHttpsEndpoint(url: String): Boolean =
        SettingsRepo.isProductionDeskUrl(url)



    /**

     * Posts a harvest batch to the configured Shop Desk URL.

     * Session URLs (`/api/ingest/session`) receive a JSON body; harvest URLs keep multipart manifest.

     */

    suspend fun postHarvest(

        url: String,

        batch: HarvestBatch,

        sessionId: String? = null,

    ): Result<Unit> {

        val endpoint = url.trim()

        if (!isSupportedEndpoint(endpoint)) {

            return Result.failure(

                IllegalArgumentException("Shop Desk URL must start with http:// or https://"),

            )

        }

        return if (endpoint.contains("/ingest/session", ignoreCase = true)) {

            postSessionWithRetry(endpoint, sessionId, batch)

        } else {

            postManifestMultipartWithRetry(endpoint, batch)

        }

    }



    private suspend fun postSessionWithRetry(

        endpoint: String,

        sessionId: String?,

        batch: HarvestBatch,

    ): Result<Unit> {

        val bodyJson = JSONObject()

            .put("session_id", sessionId?.takeIf { it.isNotBlank() } ?: batch.manifest.vehicleProfileId)

            .put("vehicle_profile_id", batch.manifest.vehicleProfileId)

            .put("schema_version", batch.manifest.schemaVersion)

            .toString()

        val body = bodyJson.toRequestBody("application/json; charset=utf-8".toMediaType())

        return executeWithRetry(endpoint, body, "session ingest")

    }



    private suspend fun postManifestMultipartWithRetry(

        endpoint: String,

        batch: HarvestBatch,

    ): Result<Unit> {

        val json = HarvestBatchManifest.toJsonBytes(batch.manifest)

        val formBody = MultipartBody.Builder()

            .setType(MultipartBody.FORM)

            .addFormDataPart(

                "manifest",

                "manifest.json",

                json.toRequestBody("application/json; charset=utf-8".toMediaType()),

            )

            .build()

        return executeWithRetry(endpoint, formBody, "harvest manifest")

    }



    private suspend fun executeWithRetry(

        endpoint: String,

        body: okhttp3.RequestBody,

        label: String,

    ): Result<Unit> {

        var lastError: Throwable? = null

        repeat(MAX_ATTEMPTS) { attempt ->

            val attemptNo = attempt + 1

            val result = runCatching {

                val req = Request.Builder().url(endpoint).post(body).build()

                http().newCall(req).execute().use { resp ->

                    if (!resp.isSuccessful) {

                        val snippet = resp.body?.string()?.take(200).orEmpty()

                        error("HTTP ${resp.code}${if (snippet.isNotBlank()) ": $snippet" else ""}")

                    }

                }

            }

            if (result.isSuccess) {

                Log.i(TAG, "$label ok → $endpoint (attempt $attemptNo)")

                return Result.success(Unit)

            }

            lastError = result.exceptionOrNull()

            Log.w(TAG, "$label attempt $attemptNo/$MAX_ATTEMPTS failed: ${lastError?.message}")

            if (attempt < MAX_ATTEMPTS - 1) {

                val backoffMs = retryDelayMs(attempt)

                if (!skipDelaysForTests) delay(backoffMs)

            }

        }

        val msg = lastError?.message ?: "Shop Desk $label failed after $MAX_ATTEMPTS attempts"

        return Result.failure(IllegalStateException(msg, lastError))

    }

}


