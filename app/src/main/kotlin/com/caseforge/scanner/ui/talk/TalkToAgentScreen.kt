@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.caseforge.scanner.ui.talk

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.caseforge.scanner.agent.AgentStatus

/**
 * Tell the agent what to do. Type or tap the mic to dictate. Send starts a session with
 * the text as the goal (or symptom, which gets folded into the agent's standard prompt).
 *
 * If a session is already running, the ticker below shows what it's doing in real time.
 */
@Composable
fun TalkToAgentScreen(
    onSend: (symptom: String) -> Unit,
    onBack: () -> Unit,
) {
    var input by remember { mutableStateOf("") }
    val running by AgentStatus.running.collectAsState()
    val activity by AgentStatus.activity.collectAsState()
    val step by AgentStatus.step.collectAsState()

    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val matches = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val text = matches?.firstOrNull().orEmpty()
            if (text.isNotBlank()) {
                input = if (input.isBlank()) text else "$input $text"
            }
        }
    }

    fun launchSpeech() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Tell the agent what to check")
        }
        speechLauncher.launch(intent)
    }

    Column(
        Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Talk to agent", style = MaterialTheme.typography.titleMedium)
        Text(
            "Type or speak what you want the agent to do — e.g. \"check ABS codes\", " +
                "\"clear stored DTCs on the engine\", \"why is the parking brake light on\".",
            style = MaterialTheme.typography.bodyMedium,
        )

        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            label = { Text("Goal / symptom") },
            modifier = Modifier.fillMaxWidth().heightIn(min = 110.dp),
            placeholder = { Text("e.g. Why is the engine light on?") },
            trailingIcon = {
                IconButton(onClick = { launchSpeech() }) {
                    Icon(Icons.Default.Mic, contentDescription = "Voice input")
                }
            },
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    onSend(input.trim())
                    input = ""
                },
                enabled = input.isNotBlank() && !running,
            ) {
                Text(if (running) "Agent busy…" else "Send to Agent")
            }
            OutlinedButton(onClick = onBack) { Text("Back") }
        }

        HorizontalDivider()

        // Live ticker
        Text("Live status", style = MaterialTheme.typography.titleSmall)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    if (running) "Step $step" else "Idle",
                    style = MaterialTheme.typography.labelMedium,
                )
                Text(activity, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
