package com.caseforge.scanner.transfer

import android.util.Log
import androidx.annotation.VisibleForTesting
import com.caseforge.scanner.BuildConfig
import com.caseforge.scanner.data.SettingsRepo
import com.caseforge.scanner.ui.setup.SetupLiveStep
import com.caseforge.scanner.ui.setup.SetupStepStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.URI
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/**
 * Best-effort Shop Desk setup telemetry:
 * - LAN `http://` → `POST {deskBase}/api/ingest/setup-step` when [SettingsRepo.shouldReportSetupStepsToLan]
 * - Production `https://desk.rickyscontrolcenter.com` → same path with Bearer token when
 *   [shopDeskBearerToken] is configured (BuildConfig `SHOP_DESK_TOKEN` from `.env` at assemble time).
 */
object SetupProgressReporter {

    private const val TAG = "SetupProgressReporter"

    internal const val MAX_ATTEMPTS = ShopDeskIngestClient.MAX_ATTEMPTS

    @VisibleForTesting
    @Volatile
    var skipDelaysForTests: Boolean = false

    @VisibleForTesting
    @Volatile
    var tokenOverrideForTests: String? = null

    @VisibleForTesting
    @Volatile
    private var httpClientOverride: OkHttpClient? = null

    private val defaultHttp = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .writeTimeout(8, TimeUnit.SECONDS)
        .build()

    @VisibleForTesting
    fun setHttpClientForTests(client: OkHttpClient?) {
        httpClientOverride = client
    }

    private fun http(): OkHttpClient = httpClientOverride ?: defaultHttp

    private val isoUtc = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    /** Desk origin from ingest URL (e.g. `http://192.168.1.50:8791`). */
    fun deriveDeskBaseUrl(ingestUrl: String): String {
        val trimmed = ingestUrl.trim().ifBlank { SettingsRepo.DEFAULT_SHOP_DESK_INGEST_URL }
        val withoutTrailing = trimmed.trimEnd('/')
        return when {
            withoutTrailing.endsWith("/api/ingest/session", ignoreCase = true) ->
                withoutTrailing.removeSuffix("/api/ingest/session")
            withoutTrailing.contains("/api/", ignoreCase = true) -> {
                val idx = withoutTrailing.indexOf("/api/", ignoreCase = true)
                withoutTrailing.substring(0, idx)
            }
            else ->
                runCatching {
                    val uri = URI(withoutTrailing)
                    val scheme = uri.scheme ?: "http"
                    val host = uri.host ?: "localhost"
                    val portPart = if (uri.port > 0) ":${uri.port}" else ""
                    "$scheme://$host$portPart"
                }.getOrDefault("http://localhost:8791")
        }
    }

    fun setupStepEndpoint(deskBase: String): String =
        "${deskBase.trimEnd('/')}/api/ingest/setup-step"

    fun statusWireValue(status: SetupStepStatus): String? = when (status) {
        SetupStepStatus.RUNNING -> "start"
        SetupStepStatus.PASSED -> "pass"
        SetupStepStatus.FAILED -> "fail"
        SetupStepStatus.SKIPPED -> "skip"
        SetupStepStatus.PENDING -> null
    }

    /**
     * Bearer token for hosted Shop Desk (`SHOP_DESK_TOKEN` in repo `.env` → BuildConfig at build time).
     * Uses reflection so this file compiles before a sibling lane adds the BuildConfig field.
     */
    internal fun shopDeskBearerToken(): String {
        tokenOverrideForTests?.let { return it }
        return runCatching {
            val field = BuildConfig::class.java.getField("SHOP_DESK_TOKEN")
            (field.get(null) as? String).orEmpty().trim()
        }.getOrDefault("")
    }

    fun shouldReportSetupStepsToHttps(ingestUrl: String, bearerToken: String): Boolean =
        ShopDeskIngestClient.isSupportedEndpoint(ingestUrl) &&
            ShopDeskIngestClient.isProductionHttpsEndpoint(ingestUrl) &&
            bearerToken.isNotBlank()

    /**
     * Fire-and-forget on [Dispatchers.IO]; never throws to callers.
     */
    suspend fun report(
        settings: SettingsRepo,
        step: SetupLiveStep,
        status: SetupStepStatus,
        detail: String = "",
        latencyMs: Long? = null,
    ) = withContext(Dispatchers.IO) {
        val wireStatus = statusWireValue(status) ?: return@withContext
        val ingestUrl = settings.shopDeskIngestUrl
        if (!ShopDeskIngestClient.isSupportedEndpoint(ingestUrl)) return@withContext

        val isProduction = ShopDeskIngestClient.isProductionHttpsEndpoint(ingestUrl)
        val bearerToken = if (isProduction) shopDeskBearerToken() else ""

        val shouldPost = when {
            isProduction -> shouldReportSetupStepsToHttps(ingestUrl, bearerToken)
            else -> settings.shouldReportSetupStepsToLan()
        }
        if (!shouldPost) return@withContext

        val deskBase = deriveDeskBaseUrl(ingestUrl)
        val endpoint = setupStepEndpoint(deskBase)
        val started = System.nanoTime()
        val bodyJson = JSONObject()
            .put("device_id", settings.setupDeviceId)
            .put("step_id", step.id)
            .put("step_name", step.title)
            .put("status", wireStatus)
            .put("detail", detail.take(2000))
            .put("latency_ms", latencyMs ?: TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started))
            .put("ts", isoUtc.format(Date()))
            .put("app_version", BuildConfig.VERSION_NAME)
            .toString()

        postSetupStepWithRetry(
            endpoint = endpoint,
            bodyJson = bodyJson,
            bearerToken = bearerToken.takeIf { it.isNotBlank() },
            wireStatus = wireStatus,
            stepId = step.id,
        )
    }

    @VisibleForTesting
    internal suspend fun postSetupStepWithRetry(
        endpoint: String,
        bodyJson: String,
        bearerToken: String?,
        wireStatus: String,
        stepId: String,
    ) {
        var lastError: Throwable? = null
        repeat(MAX_ATTEMPTS) { attempt ->
            val attemptNo = attempt + 1
            val result = runCatching {
                val body = bodyJson.toRequestBody("application/json; charset=utf-8".toMediaType())
                val reqBuilder = Request.Builder()
                    .url(endpoint)
                    .post(body)
                if (!bearerToken.isNullOrBlank()) {
                    reqBuilder.header("Authorization", "Bearer $bearerToken")
                }
                http().newCall(reqBuilder.build()).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        val snippet = resp.body?.string()?.take(200).orEmpty()
                        error("HTTP ${resp.code}${if (snippet.isNotBlank()) ": $snippet" else ""}")
                    }
                }
            }
            if (result.isSuccess) {
                Log.d(TAG, "setup-step $wireStatus $stepId → $endpoint (attempt $attemptNo)")
                return
            }
            lastError = result.exceptionOrNull()
            Log.w(
                TAG,
                "setup-step attempt $attemptNo/$MAX_ATTEMPTS failed ${stepId}: ${lastError?.message}",
            )
            if (attempt < MAX_ATTEMPTS - 1) {
                val backoffMs = ShopDeskIngestClient.retryDelayMs(attempt)
                if (!skipDelaysForTests) delay(backoffMs)
            }
        }
        Log.w(
            TAG,
            "setup-step failed ${stepId} after $MAX_ATTEMPTS attempts: ${lastError?.message}",
        )
    }
}
