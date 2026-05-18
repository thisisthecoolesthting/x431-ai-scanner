package com.caseforge.scanner.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun HomeScreen(
    onOpenSettings: () -> Unit,
    onStartBubble: () -> Unit,
    onGrantCapture: () -> Unit,
    onOpenA11y: () -> Unit,
    onManualTriage: () -> Unit,
    onRunAgentNow: () -> Unit,
    onRunFullScan: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenApprovals: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Setup", style = MaterialTheme.typography.titleMedium)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("1. Add your Claude API key.")
                Button(onClick = onOpenSettings) { Text("Open Settings") }
                Text("2. Enable the CaseForge accessibility service so the agent can see and tap the X431 app.")
                OutlinedButton(onClick = onOpenA11y) { Text("Open Accessibility Settings") }
                Text("3. Grant screen capture (used when the UI tree alone isn't enough).")
                OutlinedButton(onClick = onGrantCapture) { Text("Grant Screen Capture") }
                Text("4. Start the floating bubble.")
                OutlinedButton(onClick = onStartBubble) { Text("Start Bubble") }
            }
        }

        HorizontalDivider()
        Text("Run", style = MaterialTheme.typography.titleMedium)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Auto-start: when the X431 app shows a VIN, the agent will start a diagnostic session automatically.")
                Button(onClick = onRunAgentNow) { Text("Start Agent Session Now") }
                Button(onClick = onRunFullScan) { Text("Full Scan All Modules") }
                OutlinedButton(onClick = onManualTriage) { Text("Triage a PDF Report / Pasted Text") }
                OutlinedButton(onClick = onOpenApprovals) { Text("Pending Approvals") }
                OutlinedButton(onClick = onOpenHistory) { Text("Session History") }
            }
        }
    }
}
