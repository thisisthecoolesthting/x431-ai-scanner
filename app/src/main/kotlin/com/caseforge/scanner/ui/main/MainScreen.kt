@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.caseforge.scanner.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.caseforge.scanner.agent.AgentStatus
import com.caseforge.scanner.data.SettingsRepo
import com.caseforge.scanner.overlay.compose.LiveActivityTicker
import com.caseforge.scanner.ui.components.TcwBusyOverlay
import com.caseforge.scanner.ui.components.TcwCommercialHero
import com.caseforge.scanner.ui.components.TcwSetupStrip
import com.caseforge.scanner.ui.transfer.DataTransferCard
import com.caseforge.scanner.vci.DiagnosticConnector
import kotlinx.coroutines.launch

@Composable
fun MainScreen(
    vciConnected: Boolean,
    vin: String?,
    linkDetail: String?,
    engineBusy: Boolean,
    engineState: com.caseforge.scanner.engine.EngineState,
    settings: SettingsRepo,
    usbDeviceCount: Int,
    selectedTransport: DiagnosticConnector.UserTransport,
    onTransportSelected: (DiagnosticConnector.UserTransport) -> Unit,
    bluetoothTransportEnabled: Boolean,
    onBluetoothTransportToggle: (Boolean) -> Unit,
    onOpenBluetoothSettings: () -> Unit,
    bondedObdDevices: List<Pair<String, String>>,
    selectedBtAddress: String?,
    onSelectBtDevice: (String) -> Unit,
    onConnectClick: () -> Unit,
    onDisconnect: () -> Unit,
    onScan: () -> Unit,
    onLiveData: () -> Unit,
    onService: () -> Unit,
    onBidirectional: () -> Unit,
    onSecurity: () -> Unit,
    onRecalls: () -> Unit,
    onHistory: () -> Unit,
    onNotes: () -> Unit,
    onSettings: () -> Unit,
    onDiagnostics: () -> Unit,
    onCheckUpdate: () -> Unit,
    buildInfo: String,
    onAiPrompt: (String?) -> Unit,
) {
    var showDrawer by remember { mutableStateOf(false) }
    var aiInput by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    if (showDrawer) {
        ConnectionDrawerSheet(
            connected = vciConnected,
            statusLine = if (vciConnected) "Connected" else "Not connected",
            linkDetail = linkDetail,
            lastError = engineState.errorBanner,
            usbDeviceCount = usbDeviceCount,
            selectedTransport = selectedTransport,
            onTransportSelected = onTransportSelected,
            bluetoothEnabled = bluetoothTransportEnabled,
            onBluetoothToggle = onBluetoothTransportToggle,
            onOpenBluetoothSettings = onOpenBluetoothSettings,
            bondedObdDevices = bondedObdDevices,
            selectedBtAddress = selectedBtAddress,
            onSelectBtDevice = onSelectBtDevice,
            onConnect = {
                onConnectClick()
                showDrawer = false
            },
            onDisconnect = {
                onDisconnect()
                showDrawer = false
            },
            onDiagnostics = onDiagnostics,
            onDismiss = { showDrawer = false },
        )
    }

    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Together Car Works") },
            actions = {
                val dot = if (vciConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                TextButton(onClick = { showDrawer = true }) {
                    Text(
                        if (vciConnected) "VCI ●" else "Connect",
                        color = dot,
                    )
                }
                IconButton(onClick = onHistory) {
                    Icon(Icons.Default.History, contentDescription = "History")
                }
                IconButton(onClick = onNotes) {
                    Icon(Icons.Default.Notes, contentDescription = "Notes")
                }
                IconButton(onClick = onCheckUpdate) {
                    Icon(Icons.Default.SystemUpdate, contentDescription = "Check for update")
                }
                IconButton(onClick = onSettings) {
                    Icon(Icons.Default.Settings, contentDescription = "Settings")
                }
            },
        )
        Text(
            buildInfo,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LiveActivityTicker(engineState = engineState)
        Column(
            Modifier
                .weight(1f)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OneTapSendCard(
                settings = settings,
                modifier = Modifier.fillMaxWidth(),
            )
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        vin?.let { "VIN: $it" } ?: "Connect USB OBD cable to read VIN",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    engineState.errorBanner?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            val gridEnabled = !engineBusy
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    ActionTile(
                        "Scan",
                        "Read all DTCs",
                        onClick = {
                            if (!vciConnected) showDrawer = true else onScan()
                        },
                        modifier = Modifier.weight(1f),
                        enabled = gridEnabled,
                    )
                    ActionTile(
                        "Live Data",
                        "Sensor stream",
                        onClick = {
                            if (!vciConnected) showDrawer = true else onLiveData()
                        },
                        modifier = Modifier.weight(1f),
                        enabled = gridEnabled,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    ActionTile(
                        "Service",
                        "Resets & relearns",
                        onClick = onService,
                        modifier = Modifier.weight(1f),
                        enabled = gridEnabled,
                    )
                    ActionTile(
                        "Bidirectional",
                        "Actuation tests",
                        onClick = onBidirectional,
                        modifier = Modifier.weight(1f),
                        enabled = gridEnabled,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    ActionTile(
                        "Recalls / TSB",
                        "NHTSA lookup",
                        onClick = onRecalls,
                        modifier = Modifier.weight(1f),
                        enabled = gridEnabled,
                    )
                    ActionTile(
                        "History",
                        "Past sessions",
                        onClick = onHistory,
                        modifier = Modifier.weight(1f),
                        enabled = gridEnabled,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    ActionTile(
                        "Security & Keys",
                        "Authorized OEM workflow",
                        onClick = onSecurity,
                        modifier = Modifier.weight(1f),
                        enabled = gridEnabled,
                    )
                    ActionTile(
                        "Diagnostics",
                        "Probe and support logs",
                        onClick = onDiagnostics,
                        modifier = Modifier.weight(1f),
                        enabled = gridEnabled,
                    )
                }
            }
        }
        Surface(shadowElevation = 8.dp) {
            Row(
                Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = aiInput,
                    onValueChange = { aiInput = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Tell Together what is wrong…") },
                    maxLines = 2,
                )
                Button(
                    onClick = {
                        val t = aiInput.trim()
                        aiInput = ""
                        scope.launch {
                            AgentStatus.setActivity("AI: ${t.take(40)}")
                            onAiPrompt(t.ifBlank { null })
                        }
                    },
                    enabled = !engineBusy,
                ) {
                    Text("Ask")
                }
            }
        }
    }
        TcwBusyOverlay(
            visible = engineBusy,
            title = "Working…",
            subtitle = engineState.screen.name.replace('_', ' '),
        )
    }
}
