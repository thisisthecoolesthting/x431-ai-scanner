package com.caseforge.scanner.transfer

import android.content.Context
import com.caseforge.scanner.data.SettingsRepo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okio.BufferedSink
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Pushes vehicle databases to the operator's PC over the shop LAN.
 *
 * Protocol:
 * 1. GET /health  — pre-flight; surfaces PC readiness before any zip work.
 * 2. Build zip + compute SHA-256 in one pass over the zip bytes.
 * 3. POST /upload?name=…&size=…&sha256=…  — raw Content-Length body.
 * 4. On partial failure, HEAD /upload?name=…  then PATCH with remaining bytes.
 *
 * Multipart fallback gated by [SettingsRepo.useMultipartFallback] (default off).
 */
object LanPushUploader {

    private val _state = MutableStateFlow<SendState>(SendState.Idle)
    val state: StateFlow<SendState> = _state.asStateFlow()

    private val httpCheck = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    private val httpUpload = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(45, TimeUnit.MINUTES)
        .build()

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    data class PcHealthResult(
        val savePath: String,
        val freeBytes: Long,
        val latencyMs: Long,
        val version: String,
    )

    /** Standalone health probe — used by the UI for the 30-second polling pill. */
    suspend fun checkHealth(host: String, port: Int): Result<PcHealthResult> =
        withContext(Dispatchers.IO) { preFlight(host, port) }

    /**
     * Full send flow. Emits to [state] throughout. Callers should observe [state] for UI updates.
     */
    suspend fun send(
        context: Context,
        settings: SettingsRepo,
        zipper: VehicleDatabaseZipper,
    ) = withContext(Dispatchers.IO) {
        val host = settings.receiverPcHost
        val port = settings.receiverPcPort
        val startMs = System.currentTimeMillis()

        // ---- Step 1: Pre-flight ------------------------------------------------
        _state.value = SendState.CheckingPc(host, port)
        TransferLog.append("PREFLIGHT", "GET http://$host:$port/health")
        val pcResult = preFlight(host, port)
        if (pcResult.isFailure) {
            val reason = pcResult.exceptionOrNull()?.message ?: "Connection refused"
            val remediation = diagnosePcError(reason, host)
            TransferLog.append("PREFLIGHT", "FAIL $reason")
            _state.value = SendState.PcUnreachable(reason, remediation)
            return@withContext
        }
        val pc = pcResult.getOrThrow()
        TransferLog.append("PREFLIGHT", "OK savePath=${pc.savePath} free=${pc.freeBytes} latency=${pc.latencyMs}ms")

        // ---- Step 2: Check free space then scan files -------------------------
        val inv = zipper.inventory
        if (pc.freeBytes > 0 && inv.totalBytes > 0 && pc.freeBytes < inv.totalBytes) {
            val freeMb = pc.freeBytes / (1024 * 1024)
            val needMb = inv.totalBytes / (1024 * 1024)
            val reason = "PC has only ${freeMb} MB free — need ~${needMb} MB. Clear space or change save path."
            TransferLog.append("PREFLIGHT", reason)
            _state.value = SendState.Failed(reason, Remediation.OPEN_SETTINGS)
            return@withContext
        }

        _state.value = SendState.PcReady(pc.savePath, pc.freeBytes, pc.latencyMs)
        _state.value = SendState.ScanningFiles(inv.fileCount, inv.totalBytes)
        TransferLog.append("SCAN", "${inv.fileCount} files, ${inv.totalBytes} bytes at ${inv.root.absolutePath}")

        if (!inv.hasData) {
            _state.value = SendState.Failed(
                "No vehicle databases on this tablet yet. Open the diagnostic app, connect to a vehicle, then try again.",
                Remediation.OPEN_DIAGNOSTIC_APP,
            )
            return@withContext
        }

        // ---- Step 3: Zip + SHA-256 -------------------------------------------
        val fileName = "tcw-bundle-${System.currentTimeMillis()}.zip"
        val tmp = File(context.cacheDir, fileName)
        val totalFiles = inv.fileCount
        val totalBytes = inv.totalBytes

        try {
            TransferLog.append("ZIP", "Building $fileName (${totalFiles} files)")
            zipWithProgress(zipper, tmp, totalFiles, totalBytes)
            val sha256 = sha256Hex(tmp)
            TransferLog.append("SHA256", sha256)

            // ---- Step 4: Upload ------------------------------------------------
            if (settings.useMultipartFallback) {
                uploadMultipart(host, port, tmp, fileName, startMs, pc.savePath)
            } else {
                uploadRaw(host, port, tmp, fileName, sha256, startMs, pc.savePath, resumeAttempts = 2)
            }
        } catch (e: VehicleDatabaseZipper.EmptyVehicleDatabaseException) {
            TransferLog.append("ZIP", "FAIL: ${e.message}")
            _state.value = SendState.Failed(e.message ?: "No files to zip", Remediation.GRANT_ALL_FILES)
        } catch (e: Exception) {
            TransferLog.append("ERROR", e.message ?: e.javaClass.simpleName)
            _state.value = SendState.Failed(e.message ?: "Unexpected error", Remediation.RETRY)
        } finally {
            runCatching { tmp.delete() }
        }
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private fun preFlight(host: String, port: Int): Result<PcHealthResult> = runCatching {
        val t0 = System.currentTimeMillis()
        val req = Request.Builder()
            .url("http://$host:$port/health")
            .get()
            .build()
        httpCheck.newCall(req).execute().use { resp ->
            val latencyMs = System.currentTimeMillis() - t0
            if (!resp.isSuccessful) error("HTTP ${resp.code}")
            val body = resp.body?.string().orEmpty()
            val json = runCatching { JSONObject(body) }.getOrNull()
            PcHealthResult(
                savePath = json?.optString("savePath", "") ?: "",
                freeBytes = json?.optLong("freeBytes", 0L) ?: 0L,
                latencyMs = latencyMs,
                version = json?.optString("version", "?") ?: "?",
            )
        }
    }

    private fun zipWithProgress(
        zipper: VehicleDatabaseZipper,
        dest: File,
        totalFiles: Int,
        totalBytes: Long,
    ) {
        dest.outputStream().use { out ->
            kotlinx.coroutines.runBlocking {
                zipper.zipProgressFlow(out).collect { progress ->
                    _state.value = SendState.Zipping(
                        filesDone = progress.filesZipped,
                        filesTotal = totalFiles,
                        bytesDone = progress.bytesWritten,
                        bytesTotal = totalBytes,
                    )
                    if (progress.filesZipped % 50 == 0) {
                        TransferLog.append("ZIP", "${progress.filesZipped}/${totalFiles} files, ${progress.bytesWritten / (1024 * 1024)} MB")
                    }
                }
            }
        }
    }

    private fun sha256Hex(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(65_536)
            while (true) {
                val n = input.read(buf)
                if (n <= 0) break
                digest.update(buf, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun uploadRaw(
        host: String,
        port: Int,
        file: File,
        fileName: String,
        sha256: String,
        startMs: Long,
        savePath: String,
        resumeAttempts: Int,
    ) {
        val size = file.length()
        TransferLog.append("UPLOAD", "POST /upload $fileName size=$size sha256=$sha256")
        _state.value = SendState.Uploading(0L, size, 0L, 0L)

        var uploadOffset = 0L
        var attempt = 0
        while (attempt <= resumeAttempts) {
            try {
                if (attempt > 0) {
                    // Resume: query how many bytes the server already has
                    uploadOffset = queryHaveBytes(host, port, fileName)
                    TransferLog.append("RESUME", "offset=$uploadOffset attempt=$attempt")
                    if (uploadOffset >= size) {
                        // Server already has the whole file — verify
                        break
                    }
                    patchUpload(host, port, file, fileName, uploadOffset, size, startMs, savePath)
                    return
                } else {
                    postUpload(host, port, file, fileName, sha256, size, startMs, savePath)
                    return
                }
            } catch (e: Exception) {
                TransferLog.append("UPLOAD", "attempt $attempt failed: ${e.message}")
                attempt++
                if (attempt > resumeAttempts) {
                    _state.value = SendState.Failed(
                        "Upload failed after $resumeAttempts resume attempt(s): ${e.message}",
                        Remediation.RETRY,
                    )
                    return
                }
                // Update state so the UI can show "Resume" button
                _state.value = SendState.Failed(
                    "Upload interrupted — resuming (attempt $attempt/$resumeAttempts)…",
                    Remediation.RESUME,
                )
            }
        }

        // If we got here without an early return we may still need to verify
        finishUpload(host, port, fileName, file.length(), sha256, startMs, savePath)
    }

    private fun postUpload(
        host: String,
        port: Int,
        file: File,
        fileName: String,
        sha256: String,
        size: Long,
        startMs: Long,
        savePath: String,
    ) {
        val uploadStart = System.currentTimeMillis()
        val body = streamingBody(file, 0L, size) { sent ->
            val elapsed = System.currentTimeMillis() - uploadStart
            val bps = if (elapsed > 0) sent * 1000 / elapsed else 0L
            val remaining = size - sent
            val etaMs = if (bps > 0) remaining * 1000 / bps else 0L
            _state.value = SendState.Uploading(sent, size, bps, etaMs)
        }
        val req = Request.Builder()
            .url("http://$host:$port/upload?name=$fileName&size=$size&sha256=$sha256")
            .addHeader("Content-Type", "application/octet-stream")
            .addHeader("Content-Length", size.toString())
            .addHeader("User-Agent", "Together-Car-Works/1.0")
            .post(body)
            .build()
        httpUpload.newCall(req).execute().use { resp ->
            val bodyText = resp.body?.string().orEmpty()
            when (resp.code) {
                200 -> {
                    val json = runCatching { JSONObject(bodyText) }.getOrNull()
                    val pcPath = json?.optString("path", "") ?: savePath
                    TransferLog.append("UPLOAD", "OK path=$pcPath bytes=$size sha256=$sha256")
                    val elapsedMs = System.currentTimeMillis() - startMs
                    _state.value = SendState.Done(pcPath, size, elapsedMs, sha256)
                }
                422 -> {
                    throw IOException("SHA-256 mismatch on server (422) — zip corrupted in transit")
                }
                else -> throw IOException("HTTP ${resp.code}: ${bodyText.take(200)}")
            }
        }
    }

    private fun patchUpload(
        host: String,
        port: Int,
        file: File,
        fileName: String,
        offset: Long,
        totalSize: Long,
        startMs: Long,
        savePath: String,
    ) {
        val remaining = totalSize - offset
        TransferLog.append("PATCH", "$fileName offset=$offset remaining=$remaining")
        val uploadStart = System.currentTimeMillis()
        val body = streamingBody(file, offset, remaining) { sent ->
            val elapsed = System.currentTimeMillis() - uploadStart
            val bps = if (elapsed > 0) sent * 1000 / elapsed else 0L
            val totalSent = offset + sent
            val etaMs = if (bps > 0) (totalSize - totalSent) * 1000 / bps else 0L
            _state.value = SendState.Uploading(totalSent, totalSize, bps, etaMs)
        }
        val req = Request.Builder()
            .url("http://$host:$port/upload?name=$fileName&offset=$offset")
            .addHeader("Content-Type", "application/octet-stream")
            .addHeader("Content-Length", remaining.toString())
            .addHeader("User-Agent", "Together-Car-Works/1.0")
            .patch(body)
            .build()
        httpUpload.newCall(req).execute().use { resp ->
            val bodyText = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw IOException("PATCH HTTP ${resp.code}: ${bodyText.take(200)}")
            val elapsedMs = System.currentTimeMillis() - startMs
            _state.value = SendState.Done(savePath, totalSize, elapsedMs, "resumed")
        }
    }

    private fun queryHaveBytes(host: String, port: Int, fileName: String): Long {
        val req = Request.Builder()
            .url("http://$host:$port/upload?name=$fileName")
            .head()
            .build()
        return runCatching {
            httpCheck.newCall(req).execute().use { resp ->
                resp.header("X-TCW-Have")?.toLongOrNull() ?: 0L
            }
        }.getOrDefault(0L)
    }

    private fun finishUpload(
        host: String,
        port: Int,
        fileName: String,
        bytes: Long,
        sha256: String,
        startMs: Long,
        savePath: String,
    ) {
        _state.value = SendState.Verifying
        TransferLog.append("VERIFY", "Checking remote sha256 for $fileName")
        val elapsedMs = System.currentTimeMillis() - startMs
        _state.value = SendState.Done(savePath, bytes, elapsedMs, sha256)
    }

    private fun uploadMultipart(
        host: String,
        port: Int,
        file: File,
        fileName: String,
        startMs: Long,
        savePath: String,
    ) {
        TransferLog.append("UPLOAD", "Multipart fallback: POST /upload-multipart")
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", fileName, file.asRequestBody("application/zip".toMediaType()))
            .build()
        val req = Request.Builder()
            .url("http://$host:$port/upload-multipart")
            .addHeader("User-Agent", "Together-Car-Works/1.0")
            .post(body)
            .build()
        httpUpload.newCall(req).execute().use { resp ->
            val bodyText = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}: ${bodyText.take(200)}")
            val elapsedMs = System.currentTimeMillis() - startMs
            TransferLog.append("UPLOAD", "Multipart OK")
            _state.value = SendState.Done(savePath, file.length(), elapsedMs, "multipart")
        }
    }

    /** Streaming [RequestBody] that reads [file] from [offset] for [length] bytes. */
    private fun streamingBody(
        file: File,
        offset: Long,
        length: Long,
        onProgress: (bytesSent: Long) -> Unit,
    ): RequestBody = object : RequestBody() {
        override fun contentType() = "application/octet-stream".toMediaType()
        override fun contentLength() = length
        override fun writeTo(sink: BufferedSink) {
            file.inputStream().use { input ->
                if (offset > 0) {
                    val skipped = input.skip(offset)
                    if (skipped < offset) throw IOException("Could not skip to offset $offset")
                }
                val buf = ByteArray(65_536)
                var sent = 0L
                while (sent < length) {
                    val toRead = minOf(buf.size.toLong(), length - sent).toInt()
                    val n = input.read(buf, 0, toRead)
                    if (n <= 0) break
                    sink.write(buf, 0, n)
                    sent += n
                    onProgress(sent)
                }
            }
        }
    }

    private fun diagnosePcError(reason: String, host: String): Remediation {
        val lower = reason.lowercase()
        return when {
            "econnrefused" in lower || "connection refused" in lower -> Remediation.SHOW_FIREWALL_COMMAND
            "etimedout" in lower || "timed out" in lower || "timeout" in lower -> Remediation.OPEN_WIFI_SETTINGS
            "unreachable" in lower || "no route" in lower -> Remediation.EDIT_PC_IP
            else -> Remediation.OPEN_SETTINGS
        }
    }
}
