package com.caseforge.scanner.update

import android.content.Context
import java.io.File
import java.io.InputStream

/**
 * Tier A overlay: JSON and bundles downloaded into [OVERLAY_ROOT] under app files dir
 * take precedence over shipped assets on the next read (no APK reinstall).
 */
object AssetOverlay {

    const val OVERLAY_ROOT = "live-update-overlay"
    const val CHANNEL_ASSET = "update-channel.json"

    fun overlayFile(context: Context, relativePath: String): File {
        val safe = relativePath.trim().removePrefix("/").replace('\\', '/')
        return File(File(context.filesDir, OVERLAY_ROOT), safe)
    }

    fun hasOverlay(context: Context, relativePath: String): Boolean =
        overlayFile(context, relativePath).isFile

    /** Overlay file if present, otherwise asset stream. */
    fun openStream(context: Context, assetPath: String): InputStream? {
        val overlay = overlayFile(context, assetPath)
        if (overlay.isFile) return overlay.inputStream()
        return runCatching { context.assets.open(assetPath) }.getOrNull()
    }

    fun readText(context: Context, assetPath: String): String? =
        openStream(context, assetPath)?.bufferedReader()?.use { it.readText() }

    fun writeOverlay(context: Context, relativePath: String, bytes: ByteArray) {
        val out = overlayFile(context, relativePath)
        out.parentFile?.mkdirs()
        out.writeBytes(bytes)
    }

    fun clearOverlay(context: Context) {
        val root = File(context.filesDir, OVERLAY_ROOT)
        if (root.exists()) root.deleteRecursively()
    }
}
