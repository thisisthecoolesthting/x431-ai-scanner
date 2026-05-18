@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.caseforge.scanner.ui.main

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionDrawerSheet(
    connected: Boolean,
    statusLine: String,
    lastError: String?,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onDiagnostics: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("VCI connection", style = MaterialTheme.typography.titleLarge)
            Text(statusLine, style = MaterialTheme.typography.bodyMedium)
            lastError?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Text(
                "Transport: Bluetooth SPP (USB OTG in task 202). Force-stop X431 if SPP fails.",
                style = MaterialTheme.typography.bodySmall,
            )
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
