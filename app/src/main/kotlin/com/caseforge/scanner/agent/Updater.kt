package com.caseforge.scanner.agent

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.caseforge.scanner.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Pulls the latest APK from the public GitHub release and triggers an install Intent.
 * The user taps "Check for Update" on the dashboard; if there's a newer build, the install
 * UI opens and replaces the current install.
 */
object Updater {
    private const val LATEST_RELEASE_URL =
        "https://api.github.com/repos/thisisthecoolesthting/x431-ai-scanner/releases/tags/latest"
    private const val APK_URL =
        "https://github.com/thisisthecoolesthting/x431-ai-scanner/releases/download/latest/app-debug.apk"

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    data class Info(val sha: String, val body: String, val publishedAt: String)

    /** Returns the latest release's commit SHA and body, or throws. */
    fun checkLatest(): Info {
        val req = Request.Builder().url(LATEST_RELEASE_URL)
            .header("Accept", "application/vnd.github+json").get().build()
        http.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) error("HTTP ${resp.code}: ${text.take(200)}")
            val o = JSONObject(text)
            val body = o.optString("body", "")
            val publishedAt = o.optString("published_at", "")
            // Extract the SHA from the body — we put it there in the workflow.
            val sha = Regex("commit ([0-9a-f]{7,40})").find(body)?.groupValues?.get(1)
                ?: o.optString("target_commitish", "?")
            return Info(sha = sha.take(7), body = body, publishedAt = publishedAt)
        }
    }

    fun isNewer(info: Info): Boolean {
        // BUILD_INFO has form "vX.Y.Z #N sha (...)" — compare the sha segment
        val current = BuildConfig.BUILD_INFO
        return !current.contains(info.sha)
    }

    /** Download the APK to app's external files dir and launch the install Intent. */
    fun downloadAndInstall(context: Context, onProgress: (String) -> Unit) {
        onProgress("Downloading APK…")
        val out = File(context.getExternalFilesDir(null), "launch-ai-latest.apk")
        val req = Request.Builder().url(APK_URL).get().build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) error("Download HTTP ${resp.code}")
            val body = resp.body ?: error("empty body")
            out.outputStream().use { sink -> body.byteStream().copyTo(sink) }
        }
        onProgress("Opening installer…")
        val uri: Uri = FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", out
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        context.startActivity(intent)
    }
}
