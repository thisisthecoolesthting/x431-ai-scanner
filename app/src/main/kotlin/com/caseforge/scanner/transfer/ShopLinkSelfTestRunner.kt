package com.caseforge.scanner.transfer

import android.content.Context
import com.caseforge.scanner.data.SettingsRepo
import java.io.File
import java.net.URI
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Non-invasive Shop Desk + vehicle-link self-tests for Settings → Debug lane.
 * Writes [RESULTS_FILENAME] under [Context.cacheDir].
 */
object ShopLinkSelfTestRunner {

    const val RESULTS_FILENAME = "self_test_results.json"
    private const val HEALTH_TIMEOUT_MS = 8_000L

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    private val http = OkHttpClient.Builder()
        .connectTimeout(HEALTH_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .readTimeout(HEALTH_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .callTimeout(HEALTH_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .build()

    @Serializable
    data class SelfTestResult(
        val id: String,
        val name: String,
        val passed: Boolean,
        val latencyMs: Long? = null,
        val detail: String,
    )

    @Serializable
    data class SelfTestReport(
        val timestamp: String,
        val results: List<SelfTestResult>,
    )

    /** Derive Shop Desk health URL from an ingest endpoint (e.g. …/api/ingest/session → …/api/health). */
    fun deriveShopDeskHealthUrl(ingestUrl: String): String {
        val trimmed = ingestUrl.trim().ifBlank { SettingsRepo.DEFAULT_SHOP_DESK_INGEST_URL }
        val withoutTrailing = trimmed.trimEnd('/')
        return when {
            withoutTrailing.endsWith("/api/ingest/session", ignoreCase = true) ->
                withoutTrailing.removeSuffix("/api/ingest/session") + "/api/health"
            withoutTrailing.contains("/api/", ignoreCase = true) -> {
                val idx = withoutTrailing.indexOf("/api/", ignoreCase = true)
                withoutTrailing.substring(0, idx) + "/api/health"
            }
            else -> {
                runCatching {
                    val uri = URI(withoutTrailing)
                    val scheme = uri.scheme ?: "http"
                    val host = uri.host ?: "localhost"
                    val portPart = if (uri.port > 0) ":${uri.port}" else ""
                    "$scheme://$host$portPart/api/health"
                }.getOrDefault("http://localhost:8791/api/health")
            }
        }
    }

    suspend fun pingShopDesk(settings: SettingsRepo): SelfTestResult = withContext(Dispatchers.IO) {
        val ingestUrl = settings.shopDeskIngestUrl
        val healthUrl = deriveShopDeskHealthUrl(ingestUrl)
        val started = System.nanoTime()
        runCatching {
            val req = Request.Builder().url(healthUrl).get().build()
            http.newCall(req).execute().use { resp ->
                val latencyMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)
                val bodySnippet = resp.body?.string()?.trim()?.take(120).orEmpty()
                val passed = resp.isSuccessful
                val detail = buildString {
                    append("GET $healthUrl → HTTP ${resp.code}")
                    if (bodySnippet.isNotBlank()) append(" body=$bodySnippet")
                    if (!passed) append(" (ingest URL: $ingestUrl)")
                }
                SelfTestResult(
                    id = "shop_desk_ping",
                    name = "Shop Desk ping",
                    passed = passed,
                    latencyMs = latencyMs,
                    detail = detail,
                )
            }
        }.getOrElse { err ->
            val latencyMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)
            SelfTestResult(
                id = "shop_desk_ping",
                name = "Shop Desk ping",
                passed = false,
                latencyMs = latencyMs,
                detail = "GET $healthUrl failed: ${err.message ?: err.javaClass.simpleName}",
            )
        }
    }

    fun linkStackCheck(settings: SettingsRepo): SelfTestResult {
        val dateFmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        val connectSummary = settings.lastConnectAttemptSummary.takeIf { it.isNotBlank() }
        val connectAt = settings.lastConnectAttemptAtMs
        val connectLine = when {
            connectSummary != null -> {
                val status = if (settings.lastConnectAttemptSuccess) "ok" else "fail"
                val whenStr = if (connectAt > 0L) " @ ${dateFmt.format(Date(connectAt))}" else ""
                "[$status]$whenStr $connectSummary"
            }
            else -> "(none logged yet)"
        }
        val detail = buildString {
            appendLine("linkTransport=${settings.linkTransport}")
            appendLine("bluetoothTransportEnabled=${settings.bluetoothTransportEnabled}")
            appendLine("vciProtocolConfirmed=${settings.vciProtocolConfirmed}")
            append("lastConnectAttempt=$connectLine")
        }.trimEnd()
        return SelfTestResult(
            id = "link_stack",
            name = "Link stack check",
            passed = true,
            latencyMs = null,
            detail = detail,
        )
    }

    suspend fun runAll(settings: SettingsRepo): SelfTestReport {
        val ping = pingShopDesk(settings)
        val link = linkStackCheck(settings)
        return SelfTestReport(
            timestamp = isoTimestampNow(),
            results = listOf(ping, link),
        )
    }

    suspend fun runAllAndPersist(context: Context, settings: SettingsRepo): SelfTestReport {
        val report = runAll(settings)
        writeReport(context.cacheDir, report)
        return report
    }

    fun writeReport(cacheDir: File, report: SelfTestReport) {
        val file = File(cacheDir, RESULTS_FILENAME)
        file.writeText(json.encodeToString(report))
    }

    fun loadReport(cacheDir: File): SelfTestReport? {
        val file = File(cacheDir, RESULTS_FILENAME)
        if (!file.isFile) return null
        return runCatching { json.decodeFromString<SelfTestReport>(file.readText()) }.getOrNull()
    }

    fun formatReportText(report: SelfTestReport): String = json.encodeToString(report)

    private fun isoTimestampNow(): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        fmt.timeZone = TimeZone.getTimeZone("UTC")
        return fmt.format(Date())
    }
}
