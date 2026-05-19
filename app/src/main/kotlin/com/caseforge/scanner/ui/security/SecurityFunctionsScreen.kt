@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.caseforge.scanner.ui.security

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun SecurityFunctionsScreen(
    vin: String?,
    batteryVoltage: String?,
    onBack: () -> Unit,
    onOpenOemApp: () -> Unit,
    onOpenDiagnostics: () -> Unit,
) {
    var authorized by remember { mutableStateOf(false) }
    var understandsBoundary by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Security & Keys") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Authorized use only",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "Use this only on a vehicle you own or are authorized to service. Together Car Works guides and logs the workflow; it does not read, extract, calculate, store, or type immobilizer PINs.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Jeep Liberty checklist", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    ChecklistLine("VIN", vin ?: "Not read yet")
                    ChecklistLine("Battery voltage", batteryVoltage ?: "Connect USB OBD and confirm 12.4 V or higher")
                    ChecklistLine("Ignition", "Key ON, engine OFF unless the OEM diagnostic app says otherwise")
                    ChecklistLine("Tool", "OEM VCI and active diagnostic subscription may be required")
                    ChecklistLine("PIN/source", "Use only a PIN from an authorized dealer, OEM account, or registered security-access provider")
                }
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Before opening the OEM app", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    AttestationRow(
                        checked = authorized,
                        onCheckedChange = { authorized = it },
                        text = "I own this vehicle or have explicit authorization from the owner.",
                    )
                    AttestationRow(
                        checked = understandsBoundary,
                        onCheckedChange = { understandsBoundary = it },
                        text = "I understand Together will not provide or recover immobilizer PINs.",
                    )
                }
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("What Together can do", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("• Open the OEM diagnostic app and keep a support log.")
                    Text("• Track VIN, voltage, timestamps, and visible OEM success/failure text.")
                    Text("• Help you follow the OEM screen prompts without storing any secret.")
                    Text("• Explain lockout risks, voltage requirements, and safe retry behavior.")
                }
            }

            Button(
                onClick = onOpenOemApp,
                enabled = authorized && understandsBoundary,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Open OEM security workflow")
            }
            OutlinedButton(onClick = onOpenDiagnostics, modifier = Modifier.fillMaxWidth()) {
                Text("Open diagnostics / support log")
            }
        }
    }
}

@Composable
private fun ChecklistLine(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun AttestationRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    text: String,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}
