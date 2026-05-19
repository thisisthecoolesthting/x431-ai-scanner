@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.caseforge.scanner.ui.transfer

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.caseforge.scanner.R
import com.caseforge.scanner.data.SettingsRepo
import com.caseforge.scanner.transfer.LanPushUploader
import com.caseforge.scanner.transfer.Remediation
import com.caseforge.scanner.transfer.SendState
import com.caseforge.scanner.transfer.VehicleDatabasePathResolver
import com.caseforge.scanner.transfer.VehicleDatabaseStorageAccess
import com.caseforge.scanner.transfer.VehicleDatabaseZipper
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val ColorGray   = Color(0xFF8A9099)
private val ColorBlue   = Color(0xFF0B5FFF)
private val ColorGreen  = Color(0xFF10A26A)
private val ColorRed    = Color(0xFFD63B3B)

private val STEP_LABELS = listOf("Check", "Scan", "Zip", "Upload", "Verify", "Done")

/** Returns 0-based index of the active step for the given [state]. */
private fun activeStep(state: SendState): Int = when (state) {
    is SendState.Idle            -> -1
    is SendState.CheckingPc      -> 0
    is SendState.PcReady         -> 0
    is SendState.PcUnreachable   -> 0
    is SendState.ScanningFiles   -> 1
    is SendState.Zipping         -> 2
    is SendState.Uploading       -> 3
    is SendState.Verifying       -> 4
    is SendState.Done            -> 5
    is SendState.Failed          -> -1
}

/** Returns true when the step at [index] is complete (green). */
private fun stepDone(state: SendState, index: Int): Boolean =
    activeStep(state).let { active -> active > index || (state is SendState.Done && index == 5) }

private fun stepFailed(state: SendState, index: Int): Boolean =
    (state is SendState.Failed || state is SendState.PcUnreachable) && index == activeStep(state)

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1_073_741_824L -> "%.1f GB".format(bytes / 1_073_741_824.0)
    bytes >= 1_048_576L     -> "%.0f MB".format(bytes / 1_048_576.0)
    bytes >= 1_024L         -> "%.0f KB".format(bytes / 1_024.0)
    else                    -> "$bytes B"
}

private fun formatEta(ms: Long): String {
    val s = ms / 1000
    return when {
        s < 60   -> "0:%02d".format(s)
        else     -> "%d:%02d".format(s / 60, s % 60)
    }
}

/**
 * 6-step send card. Polls /health every 30 s while visible. Shows PC pill, step dots,
 * progress bars, ticker text, and per-remediation action buttons.
 *
 * Note for K-lane merge: this composable needs a route registration in MainActivity
 * for the TransferLogScreen ("transfer_log") — that wiring belongs to the C-lane pass.
 */
@Composable
fun OneTapSendCard(
    settings: SettingsRepo,
    modifier: Modifier = Modifier,
    onOpenTransferLog: (() -> Unit)? = null,
    onSent: (() -> Unit)? = null,
) {
    val ctx   = LocalContext.current
    val scope = rememberCoroutineScope()

    val sendState by LanPushUploader.state.collectAsState()

    var inventory by remember { mutableStateOf(VehicleDatabasePathResolver.scan()) }
    var pcHealth  by remember { mutableStateOf<LanPushUploader.PcHealthResult?>(null) }
    var pcError   by remember { mutableStateOf<String?>(null) }

    // Refresh inventory once on mount
    LaunchedEffect(Unit) {
        inventory = VehicleDatabasePathResolver.scan()
    }

    // Poll /health every 30 s while card is visible
    LaunchedEffect(Unit) {
        while (true) {
            val host = settings.receiverPcHost
            val port = settings.receiverPcPort
            LanPushUploader.checkHealth(host, port)
                .onSuccess { pcHealth = it; pcError = null }
                .onFailure { pcHealth = null; pcError = it.message }
            delay(30_000)
        }
    }

    // Auto-callback on Done
    LaunchedEffect(sendState) {
        if (sendState is SendState.Done) onSent?.invoke()
    }

    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {

            // ---- Header row: title + log button ----------------------------
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Vehicle Data → Office PC",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                if (onOpenTransferLog != null) {
                    IconButton(onClick = onOpenTransferLog) {
                        Icon(Icons.Default.List, contentDescription = "Transfer log")
                    }
                }
            }

            // ---- PC health pill --------------------------------------------
            PcPill(pcHealth, pcError, settings.receiverPcHost, settings.receiverPcPort)

            // ---- Permission card (All Files Access) ------------------------
            if (VehicleDatabaseStorageAccess.needsAllFilesAccess()) {
                PermissionCard(
                    onAllow = {
                        VehicleDatabaseStorageAccess.openAllFilesAccessSettings(ctx)
                    },
                    onRescan = {
                        inventory = VehicleDatabasePathResolver.scan()
                    },
                )
            }

            // ---- Inventory summary -----------------------------------------
            if (inventory.hasData) {
                Text(
                    "Ready: ${inventory.fileCount} files · ${formatBytes(inventory.totalBytes)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // ---- 6-step dot row --------------------------------------------
            StepDots(sendState)

            // ---- Ticker text -----------------------------------------------
            val ticker = stateTickerText(sendState, settings)
            if (ticker.isNotBlank()) {
                Text(
                    ticker,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                )
            }

            // ---- Progress bars (Zipping / Uploading) -----------------------
            when (val s = sendState) {
                is SendState.Zipping -> {
                    val progress = if (s.bytesTotal > 0) s.bytesDone.toFloat() / s.bytesTotal else 0f
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                    Text(
                        "${s.filesDone}/${s.filesTotal} files · ${formatBytes(s.bytesDone)} / ${formatBytes(s.bytesTotal)}",
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                is SendState.Uploading -> {
                    val progress = if (s.bytesTotal > 0) s.bytesSent.toFloat() / s.bytesTotal else 0f
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                    Text(
                        "${formatBytes(s.bytesSent)} / ${formatBytes(s.bytesTotal)}" +
                            (if (s.bytesPerSec > 0) " · ${formatBytes(s.bytesPerSec)}/s" else "") +
                            (if (s.etaMs > 0) " · ETA ${formatEta(s.etaMs)}" else ""),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                is SendState.CheckingPc, is SendState.ScanningFiles, is SendState.Verifying -> {
                    CircularProgressIndicator(Modifier.size(20.dp).align(Alignment.CenterHorizontally))
                }
                else -> Unit
            }

            // ---- Error / remediation banner --------------------------------
            when (val s = sendState) {
                is SendState.PcUnreachable ->
                    ErrorBanner(s.reason, s.remediation, settings, ctx, scope) {
                        inventory = VehicleDatabasePathResolver.scan()
                    }
                is SendState.Failed ->
                    ErrorBanner(s.reason, s.remediation, settings, ctx, scope) {
                        inventory = VehicleDatabasePathResolver.scan()
                    }
                else -> Unit
            }

            // ---- Action buttons --------------------------------------------
            val isBusy = sendState is SendState.CheckingPc ||
                sendState is SendState.ScanningFiles ||
                sendState is SendState.Zipping ||
                sendState is SendState.Uploading ||
                sendState is SendState.Verifying

            if (!isBusy) {
                Button(
                    onClick = {
                        if (VehicleDatabaseStorageAccess.needsAllFilesAccess()) {
                            VehicleDatabaseStorageAccess.openAllFilesAccessSettings(ctx)
                            return@Button
                        }
                        inventory = VehicleDatabasePathResolver.scan()
                        scope.launch {
                            val zipper = VehicleDatabaseZipper(inventory.root)
                            LanPushUploader.send(ctx, settings, zipper)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (sendState is SendState.Done) "Send again" else "Send to PC")
                }
            }

            // Resume button when a partial upload was interrupted
            if (sendState is SendState.Failed &&
                (sendState as SendState.Failed).remediation == Remediation.RESUME
            ) {
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            val zipper = VehicleDatabaseZipper(inventory.root)
                            LanPushUploader.send(ctx, settings, zipper)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Resume upload") }
            }
        }
    }
}

// ---- PC health pill ----------------------------------------------------------

@Composable
private fun PcPill(
    health: LanPushUploader.PcHealthResult?,
    error: String?,
    host: String,
    port: Int,
) {
    val green = ColorGreen
    val red   = ColorRed
    val dotColor by animateColorAsState(if (health != null) green else red, label = "pill_dot")

    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.small)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(dotColor))
        if (health != null) {
            val freeMb = health.freeBytes / (1024 * 1024)
            Text(
                "PC ready · ${health.savePath.take(30)} · ${freeMb} MB free · ${health.latencyMs}ms",
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
            )
        } else {
            Text(
                "PC offline ($host:$port)" + (if (!error.isNullOrBlank()) " — ${error.take(60)}" else ""),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                maxLines = 1,
            )
        }
    }
}

// ---- Step dots row -----------------------------------------------------------

@Composable
private fun StepDots(state: SendState) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        STEP_LABELS.forEachIndexed { index, label ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val dotColor = when {
                    stepFailed(state, index) -> ColorRed
                    stepDone(state, index)   -> ColorGreen
                    activeStep(state) == index -> ColorBlue
                    else -> ColorGray
                }
                Box(Modifier.size(10.dp).clip(CircleShape).background(dotColor))
                Spacer(Modifier.height(2.dp))
                Text(label, style = MaterialTheme.typography.labelSmall, color = dotColor)
            }
        }
    }
}

// ---- Permission card ---------------------------------------------------------

@Composable
private fun PermissionCard(onAllow: () -> Unit, onRescan: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "Together needs All files access to read the vehicle databases the OEM diagnostic app saved.",
                style = MaterialTheme.typography.bodySmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onAllow) { Text("Allow file access") }
                OutlinedButton(onClick = onRescan) { Text("Rescan") }
            }
        }
    }
}

// ---- Error banner ------------------------------------------------------------

@Composable
private fun ErrorBanner(
    reason: String,
    remediation: Remediation,
    settings: SettingsRepo,
    ctx: android.content.Context,
    scope: kotlinx.coroutines.CoroutineScope,
    onRescan: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(reason, style = MaterialTheme.typography.bodySmall)
            val actionPair: Pair<String?, () -> Unit> = when (remediation) {
                Remediation.OPEN_SETTINGS        -> "Settings" to {
                    val intent = android.content.Intent(android.provider.Settings.ACTION_SETTINGS)
                    intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    ctx.startActivity(intent)
                }
                Remediation.GRANT_ALL_FILES      -> "Allow file access" to {
                    VehicleDatabaseStorageAccess.openAllFilesAccessSettings(ctx)
                }
                Remediation.RESCAN               -> "Rescan" to onRescan
                Remediation.EDIT_PC_IP           -> "Settings" to {
                    val intent = android.content.Intent(android.provider.Settings.ACTION_SETTINGS)
                    intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    ctx.startActivity(intent)
                }
                Remediation.OPEN_WIFI_SETTINGS   -> "Wi-Fi settings" to {
                    val intent = android.content.Intent(android.provider.Settings.ACTION_WIFI_SETTINGS)
                    intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    ctx.startActivity(intent)
                }
                Remediation.SHOW_FIREWALL_COMMAND -> "Show fix" to {
                    // Show the netsh command in log (UI can expand; full log via log screen)
                    com.caseforge.scanner.transfer.TransferLog.append(
                        "FIREWALL",
                        "Run on PC as Admin: New-NetFirewallRule -DisplayName 'TCW Receiver' -Direction Inbound -LocalPort ${settings.receiverPcPort} -Protocol TCP -Action Allow",
                    )
                }
                Remediation.OPEN_DIAGNOSTIC_APP  -> "Open diagnostic app" to {
                    runCatching {
                        val pm = ctx.packageManager
                        val packages = listOf(
                            "com.cnlaunch.x431padv",
                            "com.cnlaunch.diagnosemodule",
                            "com.cnlaunch.x431pro",
                        )
                        val launch = packages.mapNotNull { pm.getLaunchIntentForPackage(it) }.firstOrNull()
                        if (launch != null) {
                            launch.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            ctx.startActivity(launch)
                        }
                    }
                }
                Remediation.RETRY, Remediation.RESUME, Remediation.NONE -> null to {}
            }
            val (btnLabel, btnAction) = actionPair
            if (btnLabel != null) {
                OutlinedButton(onClick = btnAction) { Text(btnLabel) }
            }
        }
    }
}

// ---- Ticker text helper ------------------------------------------------------

private fun stateTickerText(state: SendState, settings: SettingsRepo): String = when (state) {
    is SendState.Idle            -> ""
    is SendState.CheckingPc      -> "Pinging PC at ${state.host}:${state.port}…"
    is SendState.PcReady         -> "PC ready · ${state.savePath}"
    is SendState.PcUnreachable   -> state.reason
    is SendState.ScanningFiles   -> "Scanning vehicle database (${state.files} files, ${formatBytes(state.bytes)})"
    is SendState.Zipping         -> "Zipping ${state.filesDone} / ${state.filesTotal} files · ${formatBytes(state.bytesDone)}"
    is SendState.Uploading       -> "Uploading ${formatBytes(state.bytesSent)} · " +
        (if (state.bytesPerSec > 0) "${formatBytes(state.bytesPerSec)}/s · ETA ${formatEta(state.etaMs)}" else "…")
    is SendState.Verifying       -> "Receiver re-hashing…"
    is SendState.Done            -> "Saved to ${state.pcPath} · ${formatBytes(state.bytes)} · ${state.elapsedMs / 1000}s"
    is SendState.Failed          -> state.reason
}
