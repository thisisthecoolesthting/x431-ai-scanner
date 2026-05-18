@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.caseforge.scanner.ui.log

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.caseforge.scanner.agent.AgentActionLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Live tail of the agent action log. Every step the agent takes — every tool call, every
 * Claude API request, every error — lands here. Refreshes every 1s while open.
 */
@Composable
fun ActionLogScreen(actionLog: AgentActionLog, onBack: () -> Unit) {
    var lines by remember { mutableStateOf<List<String>>(emptyList()) }

    LaunchedEffect(Unit) {
        while (true) {
            val read = withContext(Dispatchers.IO) { actionLog.tail(500) }
            if (read != lines) lines = read
            delay(1000)
        }
    }

    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Action log (${lines.size} entries)", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { actionLog.clear(); lines = emptyList() }) { Text("Clear") }
                Button(onClick = onBack) { Text("Back") }
            }
        }
        if (lines.isEmpty()) {
            Text("No activity yet. Run an agent session and entries will stream here.")
        } else {
            LazyColumn(modifier = Modifier.weight(1f), reverseLayout = true) {
                items(lines.asReversed()) { line ->
                    Text(
                        line,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(vertical = 1.dp),
                    )
                }
            }
        }
    }
}
