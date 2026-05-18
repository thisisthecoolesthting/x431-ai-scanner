@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.caseforge.scanner.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.caseforge.scanner.vci.DiagnosticConnector

@Composable
fun ConnectionDrawerSheet(
    connected: Boolean,
    statusLine: String,
    linkDetail: String?,
    lastError: String?,
    usbDeviceCount: Int,
    selectedTransport: DiagnosticConnector.UserTransport,
    onTransportSelected: (DiagnosticConnector.UserTransport) -> Unit,
    bluetoothEnabled: Boolean,
    onBluetoothToggle: (Boolean) -> Unit,
    onOpenBluetoothSettings: () -> Unit,
    bondedObdDevices: List<Pair<String, String>>,
    selectedBtAddress: String?,
    onSelectBtDevice: (String) -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onDiagnostics: () -> Unit,
    onDismiss: () -> Unit,
) {
    var showBtHint by remember { mutableStateOf(false) }

    if (showBtHint) {
        AlertDialog(
            onDismissRequest = { showBtHint = false },
            title = { Text("Pair in Android Settings") },
            text = {
                Text(
                    "Pairing happens in Android's Bluetooth Settings, not inside Together. " +
                        "Tap Continue to open Bluetooth settings, pair your dongle, then return here and Connect.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showBtHint = false
                        onBluetoothToggle(true)
                        onOpenBluetoothSettings()
                    },
                ) { Text("Continue") }
            },
            dismissButton = {
                TextButton(onClick = { showBtHint = false }) { Text("Cancel") }
            },
        )
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Vehicle connection", style = MaterialTheme.typography.titleLarge)
            Text(statusLine, style = MaterialTheme.typography.bodyMedium)
            linkDetail?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
            lastError?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            Text("USB (default)", style = MaterialTheme.typography.titleSmall)
            Text(
                if (usbDeviceCount > 0) {
                    "$usbDeviceCount USB serial device(s) attached — plug cable from OBD port to tablet."
                } else {
                    "No USB cable detected. Plug a USB OBD cable from the vehicle's OBD port into the tablet."
                },
                style = MaterialTheme.typography.bodySmall,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                TransportChip("Auto", selectedTransport == DiagnosticConnector.UserTransport.AUTO) {
                    onTransportSelected(DiagnosticConnector.UserTransport.AUTO)
                }
                TransportChip("USB OBD", selectedTransport == DiagnosticConnector.UserTransport.ELM327_USB) {
                    onTransportSelected(DiagnosticConnector.UserTransport.ELM327_USB)
                }
                TransportChip("Launch USB", selectedTransport == DiagnosticConnector.UserTransport.LAUNCH_USB) {
                    onTransportSelected(DiagnosticConnector.UserTransport.LAUNCH_USB)
                }
            }

            HorizontalDivider()
            Text("Bluetooth (optional — requires pairing)", style = MaterialTheme.typography.titleSmall)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Enable Bluetooth transports", style = MaterialTheme.typography.bodyMedium)
                Switch(
                    checked = bluetoothEnabled,
                    onCheckedChange = { enabled ->
                        if (enabled) {
                            showBtHint = true
                        } else {
                            onBluetoothToggle(false)
                        }
                    },
                )
            }

            if (bluetoothEnabled) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    TransportChip("Launch BT", selectedTransport == DiagnosticConnector.UserTransport.LAUNCH_BT) {
                        onTransportSelected(DiagnosticConnector.UserTransport.LAUNCH_BT)
                    }
                    TransportChip("ELM327 BT", selectedTransport == DiagnosticConnector.UserTransport.ELM327_BT) {
                        onTransportSelected(DiagnosticConnector.UserTransport.ELM327_BT)
                    }
                }
                if (bondedObdDevices.isEmpty()) {
                    Text(
                        "No bonded devices yet. Pair your dongle in Android Bluetooth Settings.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                } else {
                    Text("Bonded devices:", style = MaterialTheme.typography.bodySmall)
                    bondedObdDevices.forEach { (name, address) ->
                        FilterChip(
                            selected = selectedBtAddress == address,
                            onClick = { onSelectBtDevice(address) },
                            label = { Text("$name ($address)") },
                        )
                    }
                }
            } else {
                Text(
                    "Bluetooth is off — Together will not scan or connect over BT until you enable this toggle.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            if (connected) {
                OutlinedButton(onClick = onDisconnect, modifier = Modifier.fillMaxWidth()) {
                    Text("Disconnect")
                }
            } else {
                Button(onClick = onConnect, modifier = Modifier.fillMaxWidth()) {
                    Text("Connect")
                }
            }
            OutlinedButton(onClick = onDiagnostics, modifier = Modifier.fillMaxWidth()) {
                Text("Connection diagnostics")
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun TransportChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label) })
}
