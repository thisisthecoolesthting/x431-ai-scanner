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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.caseforge.scanner.App
import com.caseforge.scanner.vci.VciConnectionDiagnostics
import com.caseforge.scanner.vci.VciConnector
import com.caseforge.scanner.vci.VciDiagnosticStep
import com.caseforge.scanner.vci.BluetoothVciClient
import com.caseforge.scanner.vci.OemUsbVciClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
        usbAttached = withContext(Dispatchers.IO) { OemUsbVciClient(ctx).listAttachedDevices().size }
        if (VciConnectionDiagnostics.hasBluetoothConnectPermission(ctx)) {
            bonded = withContext(Dispatchers.IO) { BluetoothVciClient(ctx).listBondedDevices() }
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Direct VCI diagnostics") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
            Text("USB serial devices attached: $usbAttached", style = MaterialTheme.typography.bodySmall)
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
                Text(if (busy) "Running…" else "Run diagnostics")
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
