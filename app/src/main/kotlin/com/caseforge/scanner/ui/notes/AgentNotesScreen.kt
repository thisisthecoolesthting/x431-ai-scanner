@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.caseforge.scanner.ui.notes

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.caseforge.scanner.data.SettingsRepo

/**
 * Free-form notes the AGENT reads. These are prepended to the system prompt on every
 * Claude call, so the agent always knows: what this app is, what's been built, the
 * tech's preferences, the shop's quirks. Edit and the next session uses them.
 */
@Composable
fun AgentNotesScreen(settings: SettingsRepo, onBack: () -> Unit) {
    var notes by remember { mutableStateOf(settings.agentNotes) }
    Column(
        Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Agent notes", style = MaterialTheme.typography.titleLarge)
        Text(
            "Everything below is given to the agent at the start of every session. " +
            "Use it to teach the agent about you, the shop, this app, your standard " +
            "procedures, common cars you work on, anything.",
            style = MaterialTheme.typography.bodySmall,
        )

        OutlinedTextField(
            value = notes,
            onValueChange = { notes = it },
            modifier = Modifier.fillMaxWidth().heightIn(min = 360.dp),
            label = { Text("Notes for the agent") },
            placeholder = { Text("e.g. \"I mostly work on GM trucks; default to bidirectional confirmation before parts swaps.\"") },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { settings.agentNotes = notes; onBack() }) { Text("Save & Back") }
            OutlinedButton(onClick = { notes = SettingsRepo.DEFAULT_AGENT_NOTES }) { Text("Reset to default") }
            OutlinedButton(onClick = onBack) { Text("Cancel") }
        }

        HorizontalDivider()
        Text(
            "These notes are local — they stay on this tablet. They never leave except " +
            "as part of the Claude API call, the same way the agent's tool calls do.",
            style = MaterialTheme.typography.labelSmall,
        )
    }
}
