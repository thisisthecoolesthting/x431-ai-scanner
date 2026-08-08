package com.caseforge.scanner.ui.triage

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun TriageScreen(
    initialText: String,
    output: String,
    busy: Boolean,
    onRun: (String) -> Unit,
    onBack: () -> Unit,
) {
    var text by remember { mutableStateOf(initialText) }
    Column(
        Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Paste a diagnostic report or shared text, then run triage.")
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text("Report text") },
            modifier = Modifier.fillMaxWidth().height(220.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { onRun(text) }, enabled = !busy && text.isNotBlank()) {
                if (busy) CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                else Text("Run Triage")
            }
            OutlinedButton(onClick = onBack) { Text("Back") }
        }
        if (output.isNotBlank()) {
            HorizontalDivider()
            Text("AI Triage", style = MaterialTheme.typography.titleMedium)
            Text(output)
        }
    }
}
