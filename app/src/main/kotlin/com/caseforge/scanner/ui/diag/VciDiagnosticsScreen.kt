@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.caseforge.scanner.ui.diag

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.caseforge.scanner.agent.ScannerAccessibilityService
import com.caseforge.scanner.data.SettingsRepo
import com.caseforge.scanner.diagnostics.UsbVciProbe
import com.caseforge.scanner.oem.OemDataIndex
import com.caseforge.scanner.pc.PcAssistantClient
import com.caseforge.scanner.pc.PcHealthInfo
import com.caseforge.scanner.pc.PcProcessState
import com.caseforge.scanner.pc.PcProcessStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun VciDiagnosticsScreen(
    settings: SettingsRepo,
    vin: String?,
    batteryVoltage: String? = null,
    onBack: () -> Unit,
    onOpenTransferLog: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pcClient = remember(settings) { PcAssistantClient(settings) }

    val resolvedVin = vin?.takeIf { it.isNotBlank() } ?: settings.lastVin
    val resolvedVoltage = batteryVoltage
        ?: settings.lastBatteryVoltage?.let { "%.1f V".format(it) }

    var oemLines by remember { mutableStateOf<List<String>>(emptyList()) }
    var oemBusy by remember { mutableStateOf(true) }

    var pcLines by remember { mutableStateOf<List<String>>(emptyList()) }
    var pcBusy by remember { mutableStateOf(true) }

    var usbSnap by remember { mutableStateOf<UsbVciProbe.Snapshot?>(null) }
    var usbPolling by remember { mutableStateOf(true) }

    val oemAppInstalled = remember(context) { detectOemDiagInstalled(context.packageManager) }

    fun refreshOem() {
        oemBusy = true
        scope.launch {
            val summary = withContext(Dispatchers.IO) { OemDataIndex.scan() }
            oemLines = summary.displayLines() + listOf(
                "Scan took ${summary.scanDurationMs} ms · ${summary.rootsChecked} root(s) checked",
            )
            oemBusy = false
        }
    }

    fun refreshPc() {
        pcBusy = true
        scope.launch {
            val host = settings.receiverPcHost
            val port = settings.receiverPcPort
            val healthResult = pcClient.health()
            val statusResult = pcClient.processStatus()
            pcLines = buildPcDisplayLines(host, port, healthResult.getOrNull(), statusResult.getOrNull())
                .ifEmpty {
                    listOf(
                        "Receiver: unreachable at $host:$port",
                        healthResult.exceptionOrNull()?.message?.let { "Health: ${it.take(120)}" }
                            ?: "Start scripts\\lan-export-receiver.ps1 on the office PC.",
                    )
                }
            pcBusy = false
        }
    }

    LaunchedEffect(Unit) {
        refreshOem()
        refreshPc()
    }

    LaunchedEffect(usbPolling) {
        while (isActive && usbPolling) {
            usbSnap = withContext(Dispatchers.IO) {
                UsbVciProbe.capture(context.applicationContext)
            }
            delay(2000)
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Diagnostics & capability") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            },
            actions = {
                TextButton(
                    onClick = {
                        refreshOem()
                        refreshPc()
                    },
                    enabled = !oemBusy && !pcBusy,
                ) {
                    Text("Refresh")
                }
            },
        )
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CapabilitySummaryCard(
                title = "OEM vehicle data inventory",
                lines = oemLines,
                busy = oemBusy,
            )

            CapabilitySummaryCard(
                title = "PC assistant",
                lines = pcLines,
                busy = pcBusy,
            )

            usbSnap?.let { snap ->
                CapabilitySummaryCard(
                    title = "USB / VCI blocker check — ${snap.verdict.name}",
                    lines = buildList {
                        add("Last scan: ${snap.ts}")
                        snap.blockers.forEach { f ->
                            add("[${f.severity}] ${f.detail}")
                        }
                        if (snap.usbDevices.isNotEmpty()) {
                            add("USB devices:")
                            snap.usbDevices.forEach { add("  $it") }
                        }
                        if (snap.btDevices.isNotEmpty()) {
                            add("BT VCI:")
                            snap.btDevices.forEach { add("  $it") }
                        }
                        if (snap.oemUiHints.isNotEmpty()) {
                            add("OEM app UI:")
                            snap.oemUiHints.forEach { add("  $it") }
                        }
                        snap.recommendations.forEach { add("→ $it") }
                    },
                )
                OutlinedButton(
                    onClick = { copyUsbDiagLog(context, snap.rawLog) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Copy USB/VCI diagnostic log")
                }
            } ?: CapabilitySummaryCard(
                title = "USB / VCI blocker check",
                lines = listOf("Scanning… plug VCI and open X431 connect screen."),
                busy = true,
            )

            SecurityReadinessCard(
                vin = resolvedVin,
                batteryVoltage = resolvedVoltage,
                oemAppInstalled = oemAppInstalled,
            )

            if (onOpenTransferLog != null) {
                OutlinedButton(
                    onClick = onOpenTransferLog,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Open transfer log")
                }
            } else {
                CapabilitySummaryCard(
                    title = "Transfer log",
                    lines = listOf(
                        "Transfer log route not wired from this entry point.",
                        "Open Settings → Send vehicle database, then use the log button there.",
                    ),
                )
            }

            Text(
                "Inventory lines are redacted (no full paths or vendor package names). " +
                    "Together does not read, store, or transmit immobilizer PINs.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SecurityReadinessCard(
    vin: String?,
    batteryVoltage: String?,
    oemAppInstalled: Boolean,
) {
    val vinReady = vin?.length == 17
    val voltageReady = settingsVoltageReady(batteryVoltage)

    CapabilitySummaryCard(
        title = "Security workflow readiness",
        lines = buildList {
            add(readinessLine("VIN on file", vinReady, vin ?: "Scan or enter a 17-character VIN"))
            add(
                readinessLine(
                    "Battery voltage",
                    voltageReady,
                    batteryVoltage ?: "Connect OBD and confirm 12.4 V or higher",
                ),
            )
            add(
                readinessLine(
                    "OEM diagnostic app",
                    oemAppInstalled,
                    if (oemAppInstalled) "Installed on this tablet" else "Not detected — install OEM diag before security work",
                ),
            )
            add("Owner authorization: confirm you own the vehicle or have written owner approval before key/security procedures.")
            add("PIN boundary: use only PINs from an authorized dealer or OEM account; Together will not extract or type immobilizer PINs.")
        },
    )
}

private fun readinessLine(label: String, ready: Boolean, detail: String): String =
    "${if (ready) "✓" else "○"} $label — $detail"

private fun settingsVoltageReady(formatted: String?): Boolean {
    if (formatted.isNullOrBlank()) return false
    val numeric = formatted.replace(Regex("[^0-9.]"), "").toFloatOrNull() ?: return false
    return numeric >= 12.4f
}

private fun detectOemDiagInstalled(pm: PackageManager): Boolean =
    ScannerAccessibilityService.OEM_DIAG_PACKAGES.any { pkg ->
        runCatching { pm.getPackageInfo(pkg, 0); true }.getOrDefault(false)
    }

private fun buildPcDisplayLines(
    host: String,
    port: Int,
    health: PcHealthInfo?,
    process: PcProcessStatus?,
): List<String> = buildList {
    add("Endpoint: $host:$port")
    if (health != null) {
        add(
            if (health.ok) "Receiver: online (${health.latencyMs} ms)"
            else "Receiver: reported unhealthy",
        )
        if (health.name.isNotBlank()) add("Service: ${health.name}")
        if (health.version.isNotBlank()) add("Version: ${health.version}")
        if (health.freeBytes > 0) {
            add("Disk free: ${formatBytesForPc(health.freeBytes)}")
        }
        redactedSavePathLine(health.savePath)?.let { add(it) }
    }
    if (process != null) {
        add("Process lane: ${process.state.toDisplayLabel()}")
        if (process.capabilities.isNotEmpty()) {
            add("Capabilities: ${process.capabilities.joinToString(", ")}")
        }
        process.activeJobId?.let { add("Active job: ${it.take(24)}") }
        if (process.progress in 1..99) add("Progress: ${process.progress}%")
        if (process.message.isNotBlank()) add(process.message.take(160))
    }
}

private fun redactedSavePathLine(savePath: String): String? {
    if (savePath.isBlank()) return null
    val leaf = savePath.substringAfterLast('\\').substringAfterLast('/')
    return if (leaf.isNotBlank()) "Save folder: …/$leaf" else "Save folder: configured"
}

private fun PcProcessState.toDisplayLabel(): String = when (this) {
    PcProcessState.IDLE -> "idle"
    PcProcessState.QUEUED -> "queued"
    PcProcessState.PROCESSING -> "processing"
    PcProcessState.COMPLETE -> "complete"
    PcProcessState.ERROR -> "error"
    PcProcessState.UNKNOWN -> "unknown"
}

// Local helper — OemDataSummary.formatBytes is internal to companion
private fun formatBytesForPc(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
    else -> String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0))
}

private fun copyUsbDiagLog(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("usb_vci_diag", text))
    Toast.makeText(context, "USB/VCI log copied", Toast.LENGTH_SHORT).show()
}
