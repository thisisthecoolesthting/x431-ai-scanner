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
import com.caseforge.scanner.ui.updates.UpdaterPhase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    private const val APK_FILENAME = "tcw-latest.apk"
    private const val PROGRESS_EMIT_BYTES = 65_536L
    private const val LATEST_RELEASE_URL =
        "https://api.github.com/repos/thisisthecoolesthting/x431-ai-scanner/releases/tags/latest"
    private const val APK_URL =
        "https://github.com/thisisthecoolesthting/x431-ai-scanner/releases/download/latest/app-debug.apk"

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val _phase = MutableStateFlow<UpdaterPhase>(UpdaterPhase.Idle)
    val phase: StateFlow<UpdaterPhase> = _phase.asStateFlow()

    @Volatile
    internal var pendingInstallVersion: String = "—"

    @Volatile
    internal var pendingInstallSha: String = "—"

    @Volatile
    internal var pendingDownloadBytes: Long = 0L

    data class Info(val sha: String, val body: String, val publishedAt: String)

    class UpdateException(message: String, cause: Throwable? = null) : Exception(message, cause)

    private fun userAgent(): String = "Together-Car-Works/${BuildConfig.VERSION_NAME}"

    private fun apkFile(context: Context): File {
        val dir = context.getExternalFilesDir(null) ?: context.filesDir
        return File(dir, APK_FILENAME)
    }

    private fun buildRequest(url: String): Request.Builder =
        Request.Builder()
            .url(url)
            .header("User-Agent", userAgent())

    fun checkLatest(): Info {
        val req = buildRequest(LATEST_RELEASE_URL)
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

    fun checkForUpdate(context: Context): Info {
        _phase.value = UpdaterPhase.Checking
        return try {
            val info = checkLatest()
            _phase.value = if (isNewer(info)) {
                UpdaterPhase.UpdateAvailable(
                    versionName = info.sha,
                    downloadUrl = APK_URL,
                    notes = info.body,
                )
            } else {
                UpdaterPhase.NoUpdate
            }
            info
        } catch (e: Exception) {
            val msg = e.message ?: e.javaClass.simpleName
            _phase.value = UpdaterPhase.Failed(
                message = "Update check failed — $msg",
                hint = "Check your network connection and try again.",
            )
            throw if (e is UpdateException) e else UpdateException(msg, e)
        }
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

    fun validateApkFile(file: File) {
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

    fun downloadAndInstall(context: Context, onProgress: (String) -> Unit) {
        val currentPhase = _phase.value
        val version = when (currentPhase) {
            is UpdaterPhase.UpdateAvailable -> currentPhase.versionName
            else -> "—"
        }
        downloadAndInstall(context, APK_URL, version, version, onProgress)
    }

    fun downloadAndInstall(
        context: Context,
        downloadUrl: String,
        versionName: String,
        sha: String,
        onProgress: (String) -> Unit,
    ) {
        if (needsInstallPermission(context)) {
            _phase.value = UpdaterPhase.PermissionRequired
            return
        }

        pendingInstallVersion = versionName.ifBlank { "—" }
        pendingInstallSha = sha.ifBlank { "—" }

        try {
            onProgress("Downloading APK…")
            val out = apkFile(context)
            downloadApk(context, downloadUrl, out)

            validateApkFile(out)
            pendingDownloadBytes = out.length()

            onProgress("Installing…")
            _phase.value = UpdaterPhase.Installing
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                installWithPackageInstaller(context, out, onProgress)
            } else {
                launchViewInstallIntent(context, out)
            }
        } catch (e: Exception) {
            val msg = e.message ?: e.javaClass.simpleName
            _phase.value = UpdaterPhase.Failed(
                message = "Update failed — $msg",
                hint = "Tap Retry or copy the log for support.",
            )
            throw if (e is UpdateException) e else UpdateException(msg, e)
        }
    }

    private fun downloadApk(context: Context, url: String, out: File) {
        val req = buildRequest(url).get().build()
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
            val total = body.contentLength().coerceAtLeast(0L)
            _phase.value = UpdaterPhase.Downloading(0L, total, APK_FILENAME)
            var bytesRead = 0L
            var lastEmit = 0L
            val buffer = ByteArray(8192)
            out.outputStream().use { sink ->
                body.byteStream().use { input ->
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        sink.write(buffer, 0, read)
                        bytesRead += read
                        if (bytesRead - lastEmit >= PROGRESS_EMIT_BYTES || bytesRead == total) {
                            _phase.value = UpdaterPhase.Downloading(bytesRead, total, APK_FILENAME)
                            lastEmit = bytesRead
                        }
                    }
                }
            }
            pendingDownloadBytes = bytesRead
            _phase.value = UpdaterPhase.Downloading(bytesRead, total.coerceAtLeast(bytesRead), APK_FILENAME)
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

    internal fun onInstallResult(context: Context?, status: Int, msg: String?) {
        when (status) {
            PackageInstaller.STATUS_SUCCESS -> {
                val version = pendingInstallVersion
                val sha = pendingInstallSha
                _phase.value = UpdaterPhase.Installed(version, sha)
                AgentStatus.setActivity("Together Car Works update installed — restart to apply")
            }
            PackageInstaller.STATUS_FAILURE,
            PackageInstaller.STATUS_FAILURE_ABORTED,
            PackageInstaller.STATUS_FAILURE_BLOCKED,
            PackageInstaller.STATUS_FAILURE_CONFLICT,
            PackageInstaller.STATUS_FAILURE_INCOMPATIBLE,
            PackageInstaller.STATUS_FAILURE_INVALID,
            PackageInstaller.STATUS_FAILURE_STORAGE,
            -> {
                val (message, hint) = installFailurePhase(status, msg)
                _phase.value = UpdaterPhase.Failed(message, hint)
                AgentStatus.setActivity("$message $hint")
                Log.e(TAG, "Install failed status=$status msg=$msg")
            }
        }
    }

    private fun installFailurePhase(status: Int, msg: String?): Pair<String, String> {
        val detail = msg?.take(200).orEmpty()
        return when (status) {
            PackageInstaller.STATUS_FAILURE_CONFLICT,
            PackageInstaller.STATUS_FAILURE_INVALID,
            ->
                "App not installed — signature mismatch" to
                    "Uninstall the current Together Car Works build, then try again."
            PackageInstaller.STATUS_FAILURE_STORAGE ->
                "App not installed — not enough storage" to
                    "Free up space then retry."
            else ->
                "App not installed — error $status" to
                    (if (detail.isNotBlank()) {
                        "$detail Copy this message for support."
                    } else {
                        "Copy the update log for support."
                    })
        }
    }
}

/**
 * Receives PackageInstaller result; updates [Updater.phase] and logs status.
 */
class InstallResultReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        val status = intent?.getIntExtra(PackageInstaller.EXTRA_STATUS, -1) ?: return
        val msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
        Updater.onInstallResult(context, status, msg)
    }
}
