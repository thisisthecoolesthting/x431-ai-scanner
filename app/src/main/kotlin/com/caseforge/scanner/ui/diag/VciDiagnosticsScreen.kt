@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.caseforge.scanner.ui.diag

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.caseforge.scanner.R
import androidx.core.content.ContextCompat
import com.caseforge.scanner.App
import com.caseforge.scanner.vci.VciConnectionDiagnostics
import com.caseforge.scanner.vci.VciDiagnosticStep
import com.caseforge.scanner.vci.BluetoothVciClient
import com.caseforge.scanner.vci.OemUsbVciClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

@Composable
fun VciDiagnosticsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val app = ctx.applicationContext as App
    val settings = app.settings
    val scope = rememberCoroutineScope()

    var steps by remember { mutableStateOf<List<VciDiagnosticStep>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var bonded by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var selectedMac by remember { mutableStateOf(settings.vciSelectedBtAddress) }
    var transportMode by remember { mutableStateOf(settings.vciTransportMode) }
    var usbAttached by remember { mutableStateOf(0) }
    var usbDevices by remember { mutableStateOf<List<UsbDeviceSnapshot>>(emptyList()) }
    var oemUsbBusy by remember { mutableStateOf(false) }
    var oemUsbOpenResult by remember { mutableStateOf<String?>(null) }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        if (results[Manifest.permission.BLUETOOTH_CONNECT] == true) {
            scope.launch { refreshBonded(ctx) { bonded = it } }
        }
    }

    fun ensurePerms(then: () -> Unit) {
        val perms = buildList {
            add(Manifest.permission.BLUETOOTH_CONNECT)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(Manifest.permission.BLUETOOTH_SCAN)
        }
        val missing = perms.any {
            ContextCompat.checkSelfPermission(ctx, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing) permLauncher.launch(perms.toTypedArray()) else then()
    }

    LaunchedEffect(Unit) {
        val usbClient = OemUsbVciClient(ctx, useHexEncoding = settings.vciUseHexEncoding)
        usbDevices = withContext(Dispatchers.IO) {
            usbClient.listAttachedDevices().map { device ->
                UsbDeviceSnapshot(
                    vendorId = device.vendorId,
                    productId = device.productId,
                    name = device.deviceName ?: "unknown",
                    hasPermission = usbClient.hasPermission(device),
                )
            }
        }
        usbAttached = usbDevices.size
        if (VciConnectionDiagnostics.hasBluetoothConnectPermission(ctx)) {
            bonded = withContext(Dispatchers.IO) { BluetoothVciClient(ctx).listBondedDevices() }
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Direct VCI diagnostics") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.a11y_nav_back),
                    )
                }
            },
        )
        Column(
            Modifier.padding(16.dp).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Transport: Auto tries USB OTG first, then Bluetooth. Force-stop the OEM diagnostic app before connecting.",
                style = MaterialTheme.typography.bodyMedium,
            )
            (App.lastOemForegroundBlockReason ?: settings.lastOemDiagConnectBlockReason.takeIf { it.isNotBlank() })
                ?.let { reason ->
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        stringResource(R.string.vci_oem_foreground_block, reason),
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            Text("USB serial devices attached: $usbAttached", style = MaterialTheme.typography.bodySmall)
            if (usbDevices.isNotEmpty()) {
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text("Attached USB devices", style = MaterialTheme.typography.titleSmall)
                        usbDevices.forEach { device ->
                            val vidHex = String.format(Locale.US, "0x%04X", device.vendorId and 0xFFFF)
                            val pidHex = String.format(Locale.US, "0x%04X", device.productId and 0xFFFF)
                            Text(
                                "$vidHex/$pidHex  ${device.name}  (${if (device.hasPermission) "perm OK" else "needs USB permission"})",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
            if (transportMode.equals("auto", ignoreCase = true)) {
                val autoDecision = remember(usbDevices, bonded, settings.bluetoothTransportEnabled) {
                    describeAutoSelection(
                        usbDevices = usbDevices,
                        bondedCount = bonded.size,
                        bluetoothEnabled = settings.bluetoothTransportEnabled,
                    )
                }
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text("AUTO selected: ${autoDecision.transport}", style = MaterialTheme.typography.titleSmall)
                        Text(autoDecision.reason, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("auto" to "Auto", "usb" to "USB", "bluetooth" to "Bluetooth").forEach { (id, label) ->
                    FilterChip(
                        selected = transportMode == id,
                        onClick = {
                            transportMode = id
                            settings.vciTransportMode = id
                        },
                        label = { Text(label) },
                    )
                }
            }
            Button(
                onClick = {
                    ensurePerms {
                        scope.launch {
                            busy = true
                            steps = withContext(Dispatchers.IO) {
                                VciConnectionDiagnostics.runChain(
                                    ctx,
                                    settings,
                                    tryLiveConnect = true,
                                    macOverride = selectedMac,
                                    transportMode = transportMode,
                                )
                            }
                            busy = false
                        }
                    }
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    if (busy) {
                        stringResource(R.string.vci_run_diagnostics_busy)
                    } else {
                        stringResource(R.string.vci_run_diagnostics)
                    },
                )
            }
            OutlinedButton(
                onClick = {
                    scope.launch {
                        oemUsbBusy = true
                        oemUsbOpenResult = ctx.getString(R.string.vci_oem_usb_opening)
                        val message = withContext(Dispatchers.IO) {
                            val usbClient = OemUsbVciClient(ctx, useHexEncoding = settings.vciUseHexEncoding)
                            usbClient.connectFirstAvailable().fold(
                                onSuccess = {
                                    usbClient.disconnect()
                                    ctx.getString(R.string.vci_oem_usb_open_success)
                                },
                                onFailure = { e ->
                                    ctx.getString(
                                        R.string.vci_oem_usb_open_failed,
                                        e.message ?: ctx.getString(R.string.vci_unknown_error),
                                    )
                                },
                            )
                        }
                        oemUsbOpenResult = message
                        oemUsbBusy = false
                    }
                },
                enabled = !busy && !oemUsbBusy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (oemUsbBusy) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Text(stringResource(R.string.vci_testing_oem_usb_open))
                    }
                } else {
                    Text(stringResource(R.string.vci_test_oem_usb_open))
                }
            }
            oemUsbOpenResult?.let { result ->
                Text(result, style = MaterialTheme.typography.bodySmall)
            }
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text("DB15 troubleshooting", style = MaterialTheme.typography.titleSmall)
                    Text("1) Verify DB15 cable is fully seated on both ends.", style = MaterialTheme.typography.bodySmall)
                    Text("2) Turn ignition ON (engine can stay off).", style = MaterialTheme.typography.bodySmall)
                    Text("3) In app Settings, enable 'Native OBD via VCI'.", style = MaterialTheme.typography.bodySmall)
                    Text("4) Compare behavior with the Launch/OEM app test on same vehicle.", style = MaterialTheme.typography.bodySmall)
                }
            }
            if (bonded.isNotEmpty()) {
                Text("Pick Bluetooth VCI if no prefix match:", style = MaterialTheme.typography.titleSmall)
                bonded.forEach { (name, mac) ->
                    FilterChip(
                        selected = selectedMac == mac,
                        onClick = {
                            selectedMac = mac
                            settings.vciSelectedBtAddress = mac
                        },
                        label = { Text("$name ($mac)") },
                    )
                }
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(steps) { s ->
                    ListItem(
                        headlineContent = {
                            Text(
                                s.name,
                                color = if (s.pass) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.error,
                            )
                        },
                        supportingContent = { Text(s.detail) },
                    )
                }
            }
        }
    }
}

private suspend fun refreshBonded(ctx: android.content.Context, onResult: (List<Pair<String, String>>) -> Unit) {
    val list = withContext(Dispatchers.IO) { BluetoothVciClient(ctx).listBondedDevices() }
    onResult(list)
}

private data class UsbDeviceSnapshot(
    val vendorId: Int,
    val productId: Int,
    val name: String,
    val hasPermission: Boolean,
)

private data class AutoSelectionSummary(
    val transport: String,
    val reason: String,
)

private fun describeAutoSelection(
    usbDevices: List<UsbDeviceSnapshot>,
    bondedCount: Int,
    bluetoothEnabled: Boolean,
): AutoSelectionSummary {
    if (usbDevices.isNotEmpty()) {
        return AutoSelectionSummary(
            transport = "USB OTG (first attempt)",
            reason = "AUTO checks USB first because ${usbDevices.size} USB serial device(s) are attached. If USB connect fails, it falls back to Bluetooth.",
        )
    }
    if (!bluetoothEnabled) {
        return AutoSelectionSummary(
            transport = "None (blocked)",
            reason = "No USB serial device is attached and Bluetooth transport is disabled in the connection drawer.",
        )
    }
    return AutoSelectionSummary(
        transport = "Bluetooth (fallback path)",
        reason = "No USB serial device is attached, so AUTO moves directly to Bluetooth. Bonded devices visible: $bondedCount.",
    )
}
