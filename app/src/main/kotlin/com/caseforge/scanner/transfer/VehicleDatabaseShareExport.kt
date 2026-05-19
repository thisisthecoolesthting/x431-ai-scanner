package com.caseforge.scanner.transfer

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Free path: zip vehicle DBs on tablet, open the system share sheet (Drive, email, Files).
 * No cloud API keys and no LAN PC discovery.
 */
object VehicleDatabaseShareExport {

    private val _state = MutableStateFlow<SendState>(SendState.Idle)
    val state: StateFlow<SendState> = _state.asStateFlow()

    suspend fun exportAndShare(context: Context) = withContext(Dispatchers.IO) {
        val startMs = System.currentTimeMillis()
        if (VehicleDatabaseStorageAccess.needsAllFilesAccess()) {
            _state.value = SendState.Failed(
                "Allow All files access first so Together can read OEM vehicle databases.",
                Remediation.GRANT_ALL_FILES,
            )
            return@withContext
        }

        val inv = VehicleDatabasePathResolver.scan()
        _state.value = SendState.ScanningFiles(inv.fileCount, inv.totalBytes)
        if (!inv.hasData) {
            _state.value = SendState.Failed(
                "No vehicle databases on this tablet yet. Open the diagnostic app, connect to a vehicle, then try again.",
                Remediation.OPEN_DIAGNOSTIC_APP,
            )
            return@withContext
        }

        val fileName = "tcw-bundle-${System.currentTimeMillis()}.zip"
        val exportDir = File(context.cacheDir, "tcw-export").apply { mkdirs() }
        val zipFile = File(exportDir, fileName)
        val zipper = VehicleDatabaseZipper(inv.root)
        val totalFiles = inv.fileCount
        val totalBytes = inv.totalBytes

        try {
            TransferLog.append("SHARE", "Zipping $fileName (${totalFiles} files)")
            zipFile.outputStream().use { out ->
                zipper.zipProgressFlow(out).collect { progress ->
                    _state.value = SendState.Zipping(
                        filesDone = progress.filesZipped,
                        filesTotal = totalFiles,
                        bytesDone = progress.bytesWritten,
                        bytesTotal = totalBytes,
                    )
                }
            }

            val elapsedMs = System.currentTimeMillis() - startMs
            _state.value = SendState.Done(
                pcPath = zipFile.name,
                bytes = zipFile.length(),
                elapsedMs = elapsedMs,
                sha256 = "share",
            )
            TransferLog.append("SHARE", "Ready ${zipFile.length()} bytes — opening share sheet")

            withContext(Dispatchers.Main) {
                launchShareIntent(context, zipFile)
            }
        } catch (e: VehicleDatabaseZipper.EmptyVehicleDatabaseException) {
            _state.value = SendState.Failed(e.message ?: "No files to zip", Remediation.GRANT_ALL_FILES)
        } catch (e: Exception) {
            TransferLog.append("SHARE", "FAIL ${e.message}")
            _state.value = SendState.Failed(e.message ?: "Export failed", Remediation.RETRY)
        }
    }

    fun reset() {
        _state.value = SendState.Idle
    }

    private fun launchShareIntent(context: Context, zipFile: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            zipFile,
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, zipFile.name)
            putExtra(Intent.EXTRA_TEXT, "Together Car Works vehicle database export")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, "Send vehicle data"))
    }
}
