package com.caseforge.scanner.update

import android.content.Context
import android.util.Log
import com.caseforge.scanner.BuildConfig
import com.caseforge.scanner.agent.Updater
import com.caseforge.scanner.agent.discovery.VehicleProfileLoader
import com.caseforge.scanner.oem.OemDecompileBundleLoader
import com.caseforge.scanner.planb.MarqueWedgeConfig
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Two-tier live updates:
 * - **Tier A** [syncPlanBAssets]: HTTPS JSON overlay — Windstar profile, wedge matrix, OEM bundle, checklists.
 * - **Tier B** [checkApkUpdate]: newer APK via GitHub release — user must tap install (no silent install).
 */
object LiveUpdateCoordinator {

    private const val TAG = "LiveUpdate"
    private const val PREFS = "live_update_coordinator"
    private const val KEY_LAST_SYNC_MS = "last_bundle_sync_ms"
    private const val KEY_LAST_REVISION = "last_bundle_revision"

    private val json = Json { ignoreUnknownKeys = true }

    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    class LiveUpdateException(message: String, cause: Throwable? = null) : Exception(message, cause)

    data class BundleSyncResult(
        val revision: String,
        val filesUpdated: Int,
        val filesSkipped: Int,
        val channelLabel: String,
    )

    data class ApkUpdateResult(
        val updateAvailable: Boolean,
        val remoteVersionCode: Int,
        val localVersionCode: Int,
        val downloadUrl: String,
        val buildSha: String,
        val notes: String,
    )

    fun loadChannel(context: Context): UpdateChannel? = runCatching {
        AssetOverlay.readText(context, AssetOverlay.CHANNEL_ASSET)?.let {
            json.decodeFromString<UpdateChannel>(it)
        }
    }.getOrNull()

    fun lastBundleSyncMs(context: Context): Long =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong(KEY_LAST_SYNC_MS, 0L)

    fun lastBundleRevision(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_LAST_REVISION, "").orEmpty()

    fun channelSummary(context: Context): String {
        val ch = loadChannel(context) ?: return "update-channel.json missing"
        val manifest = ch.bundleManifestUrl.take(80).let { if (it.length < ch.bundleManifestUrl.length) "$it…" else it }
        return "${ch.channelLabel.ifBlank { "default" }} · manifest $manifest"
    }

    /**
     * Tier A: fetch remote manifest and refresh overlay JSON (HTTPS only; optional SHA-256).
     */
    fun syncPlanBAssets(context: Context): BundleSyncResult {
        val channel = loadChannel(context)
            ?: throw LiveUpdateException("Missing ${AssetOverlay.CHANNEL_ASSET}")
        val manifestUrl = channel.bundleManifestUrl.trim()
        requireHttps(manifestUrl, "bundle manifest")
        val manifest = fetchManifest(manifestUrl)
        var updated = 0
        var skipped = 0
        for (entry in manifest.files) {
            val path = entry.path.trim().removePrefix("/")
            if (path.isBlank()) continue
            val url = entry.url?.trim()?.takeIf { it.isNotBlank() }
                ?: defaultRawUrl(path)
            requireHttps(url, path)
            val bytes = downloadBytes(url)
            entry.sha256?.trim()?.takeIf { it.isNotBlank() }?.let { expected ->
                val actual = sha256Hex(bytes)
                if (!actual.equals(expected, ignoreCase = true)) {
                    throw LiveUpdateException("SHA-256 mismatch for $path")
                }
            }
            val existing = AssetOverlay.overlayFile(context, path)
            if (existing.isFile && existing.readBytes().contentEquals(bytes)) {
                skipped++
                continue
            }
            AssetOverlay.writeOverlay(context, path, bytes)
            updated++
        }
        invalidateLoadedCaches()
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong(KEY_LAST_SYNC_MS, System.currentTimeMillis())
            .putString(KEY_LAST_REVISION, manifest.revision.ifBlank { manifest.publishedAt })
            .apply()
        Log.i(TAG, "syncPlanBAssets revision=${manifest.revision} updated=$updated skipped=$skipped")
        return BundleSyncResult(
            revision = manifest.revision.ifBlank { manifest.publishedAt },
            filesUpdated = updated,
            filesSkipped = skipped,
            channelLabel = channel.channelLabel,
        )
    }

    /**
     * Tier B: compare [BuildConfig.VERSION_CODE] to remote manifest / GitHub release metadata.
     */
    fun checkApkUpdate(context: Context): ApkUpdateResult {
        val channel = loadChannel(context)
        val localCode = BuildConfig.VERSION_CODE
        val manifestApk = channel?.bundleManifestUrl?.trim()?.takeIf { it.isNotBlank() }?.let { url ->
            runCatching { fetchManifest(url).apk }.getOrNull()
        }
        if (manifestApk != null && manifestApk.versionCode > localCode) {
            val dl = manifestApk.downloadUrl.ifBlank { channel?.apk?.downloadUrl.orEmpty() }
            requireHttps(dl, "apk download")
            return ApkUpdateResult(
                updateAvailable = true,
                remoteVersionCode = manifestApk.versionCode,
                localVersionCode = localCode,
                downloadUrl = dl,
                buildSha = manifestApk.buildSha ?: manifestApk.versionName,
                notes = "Remote manifest versionCode ${manifestApk.versionCode}",
            )
        }
        val info = Updater.checkLatest()
        val apkUrl = channel?.apk?.downloadUrl?.takeIf { it.isNotBlank() } ?: Updater.APK_URL_FALLBACK
        val newerBySha = Updater.isNewer(info)
        return ApkUpdateResult(
            updateAvailable = newerBySha,
            remoteVersionCode = manifestApk?.versionCode ?: localCode,
            localVersionCode = localCode,
            downloadUrl = apkUrl,
            buildSha = info.sha,
            notes = info.body.take(400),
        )
    }

    /** Copilot / Settings: Tier A then Tier B summary (does not install). */
    fun checkForUpdates(context: Context): String = buildString {
        appendLine("Tier A — live JSON overlay (no reinstall)")
        appendLine("channel: ${channelSummary(context)}")
        val lastMs = lastBundleSyncMs(context)
        if (lastMs > 0L) {
            appendLine("last bundle sync: $lastMs revision=${lastBundleRevision(context)}")
            appendLine("overlay files: ${overlayFileCount(context)}")
        } else {
            appendLine("last bundle sync: never")
        }
        appendLine()
        appendLine("Tier B — APK (install + usually restart)")
        appendLine("local: ${BuildConfig.BUILD_INFO} versionCode=${BuildConfig.VERSION_CODE}")
        val apk = checkApkUpdate(context)
        appendLine(
            if (apk.updateAvailable) {
                "APK update available — build ${apk.buildSha} versionCode>${apk.localVersionCode}"
            } else {
                "APK up to date (or same CI tag) — ${apk.buildSha}"
            },
        )
        if (apk.updateAvailable) {
            appendLine("download: ${apk.downloadUrl}")
            appendLine("User must tap Install — no silent APK install.")
        }
    }

    fun syncVehicleProfiles(context: Context): String {
        val result = syncPlanBAssets(context)
        val ids = VehicleProfileLoader.listProfileIds(context)
        return buildString {
            appendLine("Synced Plan B bundles (Tier A).")
            appendLine("revision=${result.revision} updated=${result.filesUpdated} skipped=${result.filesSkipped}")
            appendLine("vehicle profiles (${ids.size}): ${ids.joinToString(", ")}")
            appendLine("Windstar: ${if (ids.contains(VehicleProfileLoader.DEFAULT_WINDSTAR_ID)) "present" else "missing"}")
            appendLine("Next read uses overlay; Kotlin code changes still need Tier B APK.")
        }
    }

    private fun overlayFileCount(context: Context): Int {
        val root = File(context.filesDir, AssetOverlay.OVERLAY_ROOT)
        if (!root.exists()) return 0
        return root.walkTopDown().filter { it.isFile }.count()
    }

    private fun fetchManifest(url: String): RemoteUpdateManifest {
        val body = downloadBytes(url)
        return json.decodeFromString<RemoteUpdateManifest>(body.decodeToString())
    }

    private fun downloadBytes(url: String): ByteArray {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", "Together-Car-Works/${BuildConfig.VERSION_NAME}")
            .get()
            .build()
        http.newCall(req).execute().use { resp ->
            val bytes = resp.body?.bytes() ?: ByteArray(0)
            if (!resp.isSuccessful) {
                throw LiveUpdateException("HTTP ${resp.code} for $url")
            }
            val ct = resp.header("Content-Type").orEmpty()
            if (ct.contains("text/html", ignoreCase = true) && url.contains("github")) {
                throw LiveUpdateException("Got HTML instead of JSON — check raw URL or release artifact")
            }
            return bytes
        }
    }

    private fun defaultRawUrl(relativePath: String): String =
        "https://raw.githubusercontent.com/thisisthecoolesthting/rickys-control-center/main/_tcw-wave2/CU1/app/src/main/assets/$relativePath"

    private fun requireHttps(url: String, label: String) {
        if (!url.startsWith("https://", ignoreCase = true)) {
            throw LiveUpdateException("Refusing non-HTTPS URL for $label")
        }
    }

    private fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun invalidateLoadedCaches() {
        OemDecompileBundleLoader.invalidateCache()
        MarqueWedgeConfig.invalidateCache()
    }
}
