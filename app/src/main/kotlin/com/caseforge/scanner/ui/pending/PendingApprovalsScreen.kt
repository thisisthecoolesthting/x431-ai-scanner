@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.caseforge.scanner.ui.pending

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.caseforge.scanner.agent.PendingActionQueue

@Composable
fun PendingApprovalsScreen(onBack: () -> Unit) {
    val items by PendingActionQueue.items.collectAsState()
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Approvals", style = MaterialTheme.typography.titleMedium)
        if (items.isEmpty()) {
            Text("Nothing waiting. When confirmation mode is enabled and the agent asks to run a " +
                "bidirectional test, it'll appear here.")
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
            items(items) { p ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(p.description, style = MaterialTheme.typography.bodyLarge)
                        Text("tool=${p.tool}", style = MaterialTheme.typography.labelSmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { PendingActionQueue.approve(p.id) }) { Text("Approve") }
                            OutlinedButton(onClick = { PendingActionQueue.deny(p.id) }) { Text("Deny") }
                        }
                    }
                }
            }
        }
        Button(onClick = onBack) { Text("Back") }
    }
}
