package com.caseforge.scanner.transfer

import android.content.Context
import com.caseforge.scanner.data.SettingsRepo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * One-shot convenience wrapper: zip vehicle databases and POST to the configured PC.
 * Delegates to [LanPushUploader] for the actual transfer logic.
 * No passcode, no tablet HTTP server.
 */
object VehicleDatabaseQuickSend {

    data class Result(val message: String, val bytesSent: Long, val filesZipped: Int)

    suspend fun zipAndSend(
        context: Context,
        settings: SettingsRepo,
        uploader: LanPushUploader,
        onStatus: (String) -> Unit,
        vehicleProfileId: String? = null,
        vinHint: String? = null,
    ): kotlin.Result<Result> = withContext(Dispatchers.IO) {
        runCatching {
            if (VehicleDatabaseStorageAccess.needsAllFilesAccess()) {
                error("Tap \"Allow file access\" first, then try again.")
            }
            val inv = VehicleDatabasePathResolver.scan()
            onStatus("Harvesting drivers + zipping ${inv.fileCount} files (${inv.totalBytes / (1024 * 1024)} MB)…")
            HarvestUploadCoordinator.harvestAndUpload(
                context,
                settings,
                vehicleProfileId = vehicleProfileId,
                vinHint = vinHint,
            )
            Result(
                message = "Sent to ${settings.receiverPcHost}",
                bytesSent = inv.totalBytes,
                filesZipped = inv.fileCount,
            )
        }
    }
}
