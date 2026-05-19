package com.caseforge.scanner.agent

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import com.caseforge.scanner.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.util.concurrent.TimeUnit

/**
 * Pulls the latest APK from the public GitHub release and installs it.
 *
 * **Signing:** CI must use a stable debug keystore (see `.github/workflows/build.yml` cache).
 * If the tablet shows "App not installed" after download, the APK signature does not match
 * the installed app — uninstall once, sideload the latest CI build, then in-app updates work.
 */
object Updater {

    private const val TAG = "Updater"
    private const val LATEST_RELEASE_URL =
        "https://api.github.com/repos/thisisthecoolesthting/x431-ai-scanner/releases/tags/latest"
    private const val APK_URL =
        "https://github.com/thisisthecoolesthting/x431-ai-scanner/releases/download/latest/app-debug.apk"

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    data class Info(val sha: String, val body: String, val publishedAt: String)

    class UpdateException(message: String, cause: Throwable? = null) : Exception(message, cause)

    fun checkLatest(): Info {
        val req = Request.Builder().url(LATEST_RELEASE_URL)
            .header("Accept", "application/vnd.github+json")
            .get()
            .build()
        http.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) error("HTTP ${resp.code}: ${text.take(200)}")
            val o = JSONObject(text)
            val body = o.optString("body", "")
            val publishedAt = o.optString("published_at", "")
            val sha = Regex("commit ([0-9a-f]{7,40})").find(body)?.groupValues?.get(1)
                ?: o.optString("target_commitish", "?")
            return Info(sha = sha.take(7), body = body, publishedAt = publishedAt)
        }
    }

    fun isNewer(info: Info): Boolean {
        val current = BuildConfig.BUILD_INFO
        return !current.contains(info.sha)
    }

    fun needsInstallPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()

    fun openInstallPermissionSettings(context: Context) {
        val intent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    fun downloadAndInstall(context: Context, onProgress: (String) -> Unit) {
        if (needsInstallPermission(context)) {
            throw UpdateException(
                "Allow \"Install unknown apps\" for Together, then tap Check for update again.",
            )
        }

        onProgress("Downloading APK…")
        val out = File(context.getExternalFilesDir(null), "launch-ai-latest.apk")
        val req = Request.Builder().url(APK_URL).get().build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw UpdateException("Download HTTP ${resp.code}")
            }
            val body = resp.body ?: throw UpdateException("empty download body")
            val contentType = resp.header("Content-Type").orEmpty()
            if (contentType.contains("text/html", ignoreCase = true)) {
                throw UpdateException(
                    "Download returned HTML, not an APK — check the latest GitHub release asset.",
                )
            }
            out.outputStream().use { sink -> body.byteStream().copyTo(sink) }
        }

        validateApkFile(out)

        onProgress("Installing…")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            installWithPackageInstaller(context, out, onProgress)
        } else {
            launchViewInstallIntent(context, out)
        }
    }

    private fun validateApkFile(file: File) {
        if (!file.exists() || file.length() < 100_000) {
            throw UpdateException(
                "Downloaded file too small (${file.length()} bytes) — not a valid APK.",
            )
        }
        FileInputStream(file).use { input ->
            val magic = ByteArray(4)
            if (input.read(magic) != 4 || magic[0] != 0x50.toByte() || magic[1] != 0x4B.toByte()) {
                throw UpdateException("Downloaded file is not a ZIP/APK (wrong signature bytes).")
            }
        }
    }

    private fun installWithPackageInstaller(context: Context, apk: File, onProgress: (String) -> Unit) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
        }
        val sessionId = installer.createSession(params)
        val session = installer.openSession(sessionId)
        try {
            apk.inputStream().use { input ->
                session.openWrite("app", 0, apk.length()).use { out ->
                    input.copyTo(out)
                    session.fsync(out)
                }
            }
            val callbackIntent = Intent(context, InstallResultReceiver::class.java)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    PendingIntent.FLAG_MUTABLE
                } else {
                    0
                }
            val pending = PendingIntent.getBroadcast(context, sessionId, callbackIntent, flags)
            session.commit(pending.intentSender)
            onProgress("Install prompt should appear — tap Install")
        } catch (e: Exception) {
            session.abandon()
            Log.w(TAG, "PackageInstaller failed, falling back to VIEW intent", e)
            launchViewInstallIntent(context, apk)
        } finally {
            session.close()
        }
    }

    private fun launchViewInstallIntent(context: Context, apk: File) {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apk,
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    }
}

/**
 * Receives PackageInstaller result; logs status for debugging.
 */
class InstallResultReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        val status = intent?.getIntExtra(PackageInstaller.EXTRA_STATUS, -1) ?: return
        val msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
        when (status) {
            PackageInstaller.STATUS_SUCCESS ->
                Log.i("Updater", "Install succeeded")
            PackageInstaller.STATUS_FAILURE,
            PackageInstaller.STATUS_FAILURE_ABORTED,
            PackageInstaller.STATUS_FAILURE_BLOCKED,
            PackageInstaller.STATUS_FAILURE_CONFLICT,
            PackageInstaller.STATUS_FAILURE_INCOMPATIBLE,
            PackageInstaller.STATUS_FAILURE_INVALID,
            PackageInstaller.STATUS_FAILURE_STORAGE,
            -> {
                Log.e("Updater", "Install failed status=$status msg=$msg")
                AgentStatus.setActivity(
                    installFailureHint(status, msg),
                )
            }
        }
    }

    private fun installFailureHint(status: Int, msg: String?): String {
        val detail = msg?.take(120).orEmpty()
        return when (status) {
            PackageInstaller.STATUS_FAILURE_CONFLICT,
            PackageInstaller.STATUS_FAILURE_INVALID,
            ->
                "Update failed: signature mismatch. Uninstall this app once, install the latest APK from GitHub, then in-app updates will work. $detail"
            else -> "Update failed ($status): $detail"
        }
    }
}
