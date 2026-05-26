@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.caseforge.scanner.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.caseforge.scanner.BuildConfig

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
    onTalkToAgent: () -> Unit,
    onOpenLog: () -> Unit,
    onOpenVciDiagnostics: () -> Unit = {},
    onBack: () -> Unit = {},
) {
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Setup") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            },
        )
        Column(
            Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Permissions", style = MaterialTheme.typography.titleMedium)
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Open Settings to paste your Anthropic key and pick theme.")
                    Button(onClick = onOpenSettings) { Text("Open Settings") }
                    Text("Enable the Together Car Works accessibility service so the agent can see and tap the OEM diagnostic app.")
                    OutlinedButton(onClick = onOpenA11y) { Text("Open Accessibility Settings") }
                    Text("Grant screen capture (used when the UI tree alone isn't enough).")
                    OutlinedButton(onClick = onGrantCapture) { Text("Grant Screen Capture") }
                    Text("Start the floating bubble.")
                    OutlinedButton(onClick = onStartBubble) { Text("Start Bubble") }
                }
            }

            HorizontalDivider()
            Text("Other", style = MaterialTheme.typography.titleMedium)
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onRunAgentNow) { Text("Start Agent Session Now") }
                    Button(onClick = onRunFullScan) { Text("Full Scan All Modules") }
                    OutlinedButton(onClick = onTalkToAgent) { Text("Talk to Agent (Voice / Text)") }
                    OutlinedButton(onClick = onManualTriage) { Text("Triage a PDF Report / Pasted Text") }
                    OutlinedButton(onClick = onOpenApprovals) { Text("Pending Approvals") }
                    OutlinedButton(onClick = onOpenHistory) { Text("Session History") }
                    OutlinedButton(onClick = onOpenLog) { Text("View Action Log (debug)") }
                    Button(onClick = onOpenVciDiagnostics) { Text("USB / VCI Blocker Check") }
                }
            }

            Text(
                BuildConfig.BUILD_INFO,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}
