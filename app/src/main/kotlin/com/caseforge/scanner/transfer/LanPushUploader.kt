package com.caseforge.scanner.transfer

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Pushes cnlaunch zip to the hardcoded office PC ([LanExportConfig.RECEIVER_PC_HOST]).
 * Start [scripts/lan-export-receiver.ps1] on that PC first.
 */
object LanPushUploader {

    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(45, TimeUnit.MINUTES)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun pushToOfficePc(
        context: Context,
        zipper: CnlaunchZipper,
        onProgress: (String) -> Unit,
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            if (!zipper.exists) error("cnlaunch folder not found")
            val url = LanExportConfig.receiverUploadUrl()
            onProgress("Building zip…")
            val tmp = File(context.cacheDir, "cnlaunch-push-${System.currentTimeMillis()}.zip")
            try {
                tmp.outputStream().use { out ->
                    zipper.zipProgressFlow(out).last()
                }
                onProgress("Uploading to $url …")
                val body = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart(
                        "file",
                        "cnlaunch-bundle.zip",
                        tmp.asRequestBody("application/zip".toMediaType()),
                    )
                    .build()
                val req = Request.Builder().url(url).post(body).build()
                http.newCall(req).execute().use { resp ->
                    val text = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) error("HTTP ${resp.code}: ${text.take(200)}")
                    onProgress("Upload complete")
                    text.ifBlank { "OK" }
                }
            } finally {
                runCatching { tmp.delete() }
            }
        }
    }
}
