@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.caseforge.scanner.ui.transfer

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.caseforge.scanner.R
import com.caseforge.scanner.data.SettingsRepo
import com.caseforge.scanner.transfer.GoldenCaptureStorage
import com.caseforge.scanner.transfer.SessionEventLogger
import com.caseforge.scanner.transfer.TabletDataHarvester
import com.caseforge.scanner.transfer.VehicleDatabasePathResolver
import com.caseforge.scanner.transfer.VehicleDatabaseStorageAccess
import com.caseforge.scanner.transfer.VehicleDatabaseZipper
import com.caseforge.scanner.ui.components.LoadingState
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class ZipStep { Idle, Collecting, Zipping, Ready, Error }

private data class FoundItem(
    val label: String,
    val bytes: Long,
    val sidecar: Boolean,
)

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1_073_741_824L -> "%.1f GB".format(bytes / 1_073_741_824.0)
    bytes >= 1_048_576L -> "%.0f MB".format(bytes / 1_048_576.0)
    bytes >= 1_024L -> "%.0f KB".format(bytes / 1_024.0)
    else -> "$bytes B"
}

/** Transfer data card — zip locally, then copy to USB via ES File Explorer. */
@Composable
fun OneTapSendCard(
    settings: SettingsRepo,
    modifier: Modifier = Modifier,
    vehicleProfileId: String? = null,
    vinHint: String? = null,
    onOpenTransferLog: (() -> Unit)? = null,
    onSent: (() -> Unit)? = null,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var zipStep by remember { mutableStateOf(ZipStep.Idle) }
    var showUsbGuide by remember { mutableStateOf(false) }
    var itemsFound by remember { mutableStateOf<List<FoundItem>>(emptyList()) }
    var zipProgress by remember { mutableStateOf(0f) }
    var zipStatus by remember { mutableStateOf("") }
    var zipPath by remember { mutableStateOf("") }
    var zipBytes by remember { mutableStateOf(0L) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var inventory by remember { mutableStateOf(VehicleDatabasePathResolver.scan()) }

    val building = zipStep == ZipStep.Collecting || zipStep == ZipStep.Zipping
    val zipProgressValue = when (zipStep) {
        ZipStep.Idle -> 0f
        ZipStep.Collecting -> 0.25f
        ZipStep.Zipping -> 0.25f + (0.75f * zipProgress.coerceIn(0f, 1f))
        ZipStep.Ready -> 1f
        ZipStep.Error -> if (itemsFound.isNotEmpty()) 0.25f else 0f
    }

    Card(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                stringResource(R.string.transfer_card_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                stringResource(R.string.transfer_card_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (VehicleDatabaseStorageAccess.needsAllFilesAccess()) {
                PermissionCard(
                    onAllow = { VehicleDatabaseStorageAccess.openAllFilesAccessSettings(ctx) },
                    onRescan = { inventory = VehicleDatabasePathResolver.scan() },
                )
            }

            if (inventory.hasData) {
                Text(
                    stringResource(
                        R.string.transfer_inventory_summary,
                        inventory.fileCount,
                        formatBytes(inventory.totalBytes),
                        inventory.root.absolutePath,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (building) {
                LinearProgressIndicator(progress = { zipProgressValue }, modifier = Modifier.fillMaxWidth())
            }

            when (zipStep) {
                ZipStep.Collecting -> LoadingState(message = "Collecting documents", animatedDots = true)
                ZipStep.Zipping -> {
                    LoadingState(
                        message = "Building ZIP",
                        animatedDots = true,
                        showLinearProgress = true,
                        progress = zipProgress,
                    )
                    if (zipStatus.isNotBlank()) {
                        Text(zipStatus, style = MaterialTheme.typography.bodySmall)
                    }
                }
                else -> Unit
            }

            if (itemsFound.isNotEmpty()) {
                Text(stringResource(R.string.transfer_found_documents), style = MaterialTheme.typography.labelLarge)
                itemsFound.take(8).forEach { item ->
                    val marker = if (item.sidecar) "[sidecar]" else "[doc]"
                    Text("$marker ${item.label} (${formatBytes(item.bytes)})", style = MaterialTheme.typography.bodySmall)
                }
                if (itemsFound.size > 8) {
                    Text("+ ${itemsFound.size - 8} more", style = MaterialTheme.typography.bodySmall)
                }
            }

            if (zipStep == ZipStep.Ready && zipPath.isNotBlank()) {
                Text(
                    stringResource(R.string.transfer_zip_ready, formatBytes(zipBytes)),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                )
                Text(zipPath, style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { openZipWithExplorerIntent(ctx, zipPath) },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    ) { Text(stringResource(R.string.transfer_open_zip_location)) }
                    OutlinedButton(
                        onClick = { shareZip(ctx, zipPath) },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    ) { Text(stringResource(R.string.transfer_share_zip)) }
                }
            }

            errorText?.let { err ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(err, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
                }
            }

            Button(
                onClick = {
                    if (VehicleDatabaseStorageAccess.needsAllFilesAccess()) {
                        VehicleDatabaseStorageAccess.openAllFilesAccessSettings(ctx)
                        return@Button
                    }
                    scope.launch {
                        try {
                            errorText = null
                            zipStep = ZipStep.Collecting
                            itemsFound = emptyList()
                            zipProgress = 0f
                            zipStatus = "Preparing harvest data..."
                            zipPath = ""
                            zipBytes = 0L
                            val result = buildHarvestZip(
                                context = ctx,
                                settings = settings,
                                vehicleProfileId = vehicleProfileId,
                                vinHint = vinHint,
                                onCollected = { docs ->
                                    itemsFound = docs
                                    zipStep = ZipStep.Zipping
                                },
                                onZipProgress = { done, total, bytesDone, bytesTotal ->
                                    zipProgress = if (bytesTotal > 0) bytesDone.toFloat() / bytesTotal else 0f
                                    zipStatus =
                                        "Building ZIP... $done/$total files (${formatBytes(bytesDone)} / ${formatBytes(bytesTotal)})"
                                },
                            )
                            zipPath = result.absolutePath
                            zipBytes = result.length()
                            zipStep = ZipStep.Ready
                            showUsbGuide = true
                            onSent?.invoke()
                        } catch (t: Throwable) {
                            zipStep = ZipStep.Error
                            errorText = t.message ?: "Failed to build ZIP export."
                        }
                    }
                },
                enabled = !building,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) {
                Text(
                    if (zipStep == ZipStep.Ready) {
                        stringResource(R.string.transfer_zip_again)
                    } else {
                        stringResource(R.string.transfer_zip_files)
                    },
                )
            }

            OutlinedButton(
                onClick = { showUsbGuide = !showUsbGuide },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) {
                Text(stringResource(R.string.transfer_es_file_usb))
            }

            if (showUsbGuide) {
                UsbTransferGuideCard(zipPath = zipPath.takeIf { it.isNotBlank() })
            }

            if (onOpenTransferLog != null) {
                OutlinedButton(
                    onClick = onOpenTransferLog,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) {
                    Text(stringResource(R.string.tcw_export_log_screen_title))
                }
            }
        }
    }
}

@Composable
private fun UsbTransferGuideCard(zipPath: String?) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(R.string.transfer_usb_guide_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            if (zipPath == null) {
                Text(
                    stringResource(R.string.transfer_usb_guide_need_zip),
                    style = MaterialTheme.typography.bodySmall,
                )
            } else {
                Text(
                    stringResource(R.string.transfer_usb_guide_zip_path, zipPath),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Text(
                stringResource(R.string.transfer_usb_guide_steps),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun PermissionCard(onAllow: () -> Unit, onRescan: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(R.string.tcw_export_need_files_access),
                style = MaterialTheme.typography.bodySmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onAllow, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.tcw_export_allow_files))
                }
                OutlinedButton(onClick = onRescan, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.tcw_export_rescan))
                }
            }
        }
    }
}

private fun shareZip(context: android.content.Context, zipPath: String) {
    val file = File(zipPath)
    if (!file.exists()) {
        Toast.makeText(context, "ZIP file not found.", Toast.LENGTH_LONG).show()
        return
    }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/zip"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching {
        context.startActivity(Intent.createChooser(intent, "Share export ZIP"))
    }.onFailure {
        Toast.makeText(context, "No app available to share ZIP.", Toast.LENGTH_LONG).show()
    }
}

private fun openZipWithExplorerIntent(context: android.content.Context, zipPath: String) {
    val file = File(zipPath)
    if (!file.exists()) {
        Toast.makeText(context, "ZIP file not found.", Toast.LENGTH_LONG).show()
        return
    }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val esIntent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "application/zip")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        setPackage("com.estrongs.android.pop")
    }
    val fallbackIntent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "application/zip")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    if (runCatching { context.startActivity(esIntent) }.isFailure) {
        runCatching { context.startActivity(Intent.createChooser(fallbackIntent, "Open export ZIP")) }
    }
}

private suspend fun buildHarvestZip(
    context: android.content.Context,
    settings: SettingsRepo,
    vehicleProfileId: String?,
    vinHint: String?,
    onCollected: (List<FoundItem>) -> Unit,
    onZipProgress: (filesDone: Int, filesTotal: Int, bytesDone: Long, bytesTotal: Long) -> Unit,
): File = withContext(Dispatchers.IO) {
    val vinResolved = vinHint?.takeIf { it.isNotBlank() } ?: settings.lastVin
    val profileId = TabletDataHarvester.resolveProfileId(context, vinResolved, vehicleProfileId)
    val batch = TabletDataHarvester.build(context, profileId, discoveryReport = null, settings = settings)
    val inventory = VehicleDatabasePathResolver.scan()
    val sidecars = batch.asZipSidecars() +
        GoldenCaptureStorage.zipSidecarsIfPresent(context) +
        SessionEventLogger.zipSidecarsIfPresent(context)
    val zipper = VehicleDatabaseZipper(sourceRoot = inventory.root, sidecarFiles = sidecars)
    val totalFiles = inventory.fileCount + zipper.sidecarFileCount
    val totalBytes = zipper.totalBytesEstimate().coerceAtLeast(1L)
    val found = buildFoundItems(inventory, sidecars)
    withContext(Dispatchers.Main) { onCollected(found) }

    val exportDir = File(context.getExternalFilesDir(null) ?: context.filesDir, "harvest-exports").apply { mkdirs() }
    pruneOldZips(exportDir)
    val zipFile = File(exportDir, "tcw-harvest-${System.currentTimeMillis()}.zip")
    zipFile.outputStream().use { output ->
        zipper.zipProgressFlow(output).collect { progress ->
            withContext(Dispatchers.Main) {
                onZipProgress(progress.filesZipped, totalFiles, progress.bytesWritten, totalBytes)
            }
        }
    }
    zipFile
}

private fun buildFoundItems(
    inventory: VehicleDatabasePathResolver.Inventory,
    sidecars: Map<String, ByteArray>,
): List<FoundItem> {
    val items = mutableListOf<FoundItem>()
    sidecars.entries.sortedBy { it.key }.forEach { (path, bytes) ->
        items += FoundItem(path, bytes.size.toLong(), sidecar = true)
    }
    inventory.root.walkTopDown().maxDepth(32).onFail { _, _ -> }.filter { it.isFile && it.canRead() }.take(24).forEach { file ->
        items += FoundItem(file.relativeTo(inventory.root).path.replace('\\', '/'), file.length(), sidecar = false)
    }
    return items
}

private fun pruneOldZips(exportDir: File, keep: Int = 10) {
    exportDir.listFiles().orEmpty()
        .filter { it.isFile && it.name.endsWith(".zip") }
        .sortedByDescending { it.lastModified() }
        .drop(keep)
        .forEach { runCatching { it.delete() } }
}
