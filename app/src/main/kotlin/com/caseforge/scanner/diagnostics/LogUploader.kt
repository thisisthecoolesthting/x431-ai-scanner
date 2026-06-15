package com.caseforge.scanner.diagnostics

import android.content.Context
import android.os.Build
import com.caseforge.scanner.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Bundles the app's recent logs + last crash and uploads them to the TCW debug endpoint
 * so support can read them. Triggered by the "Send Logs" button.
 */
object LogUploader {

    private const val ENDPOINT = "https://tcw.aiaffiliate.builders/api/debug-log"
    private const val TOKEN = "tcwlogs2026"

    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    /** Returns the upload id on success, or null on failure. */
    suspend fun send(context: Context, extraNote: String? = null): String? = withContext(Dispatchers.IO) {
        val text = gather(context, extraNote)
        val payload = JSONObject().apply {
            put("source", "apk")
            put("label", "send-logs")
            put("text", text)
        }.toString()
        val req = Request.Builder()
            .url(ENDPOINT)
            .header("Content-Type", "application/json")
            .header("x-log-token", TOKEN)
            .post(payload.toRequestBody("application/json".toMediaType()))
            .build()
        runCatching {
            http.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) return@withContext null
                JSONObject(body).optString("id").takeIf { it.isNotBlank() }
            }
        }.getOrNull()
    }

    private fun gather(context: Context, extraNote: String?): String = buildString {
        appendLine("=== TCW APK logs ===")
        appendLine("build: ${BuildConfig.BUILD_INFO}")
        appendLine("device: ${Build.MANUFACTURER} ${Build.MODEL} / Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        extraNote?.takeIf { it.isNotBlank() }?.let { appendLine("note: $it") }
        appendLine()

        // last crash, if any
        val crash = readFile(File(context.getExternalFilesDir(null) ?: context.filesDir, "TCW-last-crash.txt"))
            ?: readFile(File(context.cacheDir, "last_crash.txt"))
        if (crash != null) {
            appendLine("---- LAST CRASH ----")
            appendLine(crash.take(8000))
            appendLine()
        }

        // action log tail
        val actionLog = readFile(File(context.filesDir, "agent_actions.log"))
        if (actionLog != null) {
            appendLine("---- ACTION LOG (tail) ----")
            appendLine(actionLog.lines().takeLast(400).joinToString("\n"))
            appendLine()
        }

        // connect-lab last result, if present
        val lab = readFile(File(context.cacheDir, "connect_lab_last.txt"))
        if (lab != null) {
            appendLine("---- CONNECT LAB ----")
            appendLine(lab.take(8000))
        }
    }

    private fun readFile(f: File): String? =
        runCatching { if (f.exists() && f.length() > 0) f.readText() else null }.getOrNull()
}
