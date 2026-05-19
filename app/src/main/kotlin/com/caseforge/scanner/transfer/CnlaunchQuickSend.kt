package com.caseforge.scanner.transfer

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * One-shot: zip cnlaunch and POST to [LanExportConfig.RECEIVER_PC_HOST]. No passcode, no tablet HTTP server.
 */
object CnlaunchQuickSend {

    data class Result(val message: String, val bytesSent: Long, val filesZipped: Int)

    suspend fun zipAndSend(
        context: Context,
        onStatus: (String) -> Unit,
    ): kotlin.Result<Result> = withContext(Dispatchers.IO) {
        runCatching {
            if (CnlaunchStorageAccess.needsAllFilesAccess()) {
                error("Tap “Allow file access” first, then try again.")
            }
            val inv = CnlaunchPathResolver.scan()
            if (!inv.hasData) {
                throw CnlaunchZipper.EmptyCnlaunchException(inv)
            }
            val zipper = CnlaunchZipper(inv.root)
            val dest = LanExportConfig.RECEIVER_PC_HOST
            onStatus("Zipping ${inv.fileCount} files (${inv.totalBytes / (1024 * 1024)} MB)…")
            val upload = LanPushUploader.pushToOfficePc(context, zipper) { step ->
                onStatus(step)
            }.getOrThrow()
            Result(
                message = upload,
                bytesSent = inv.totalBytes,
                filesZipped = inv.fileCount,
            )
        }
    }
}
