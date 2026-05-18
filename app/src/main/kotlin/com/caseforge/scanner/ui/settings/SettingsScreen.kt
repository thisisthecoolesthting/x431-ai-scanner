@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.caseforge.scanner.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.caseforge.scanner.data.SettingsRepo

@Composable
fun SettingsScreen(settings: SettingsRepo, onBack: () -> Unit) {
    var apiKey by remember { mutableStateOf(settings.claudeApiKey) }
    var model by remember { mutableStateOf(settings.model) }
    var autonomous by remember { mutableStateOf(settings.autonomousActuation) }
    var autoStart by remember { mutableStateOf(settings.autoStartOnVin) }
    var kill by remember { mutableStateOf(settings.killSwitch) }
    var approval by remember { mutableStateOf(settings.requireApproval) }

    Column(
        Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedTextField(
            value = apiKey,
            onValueChange = { apiKey = it; settings.claudeApiKey = it },
            label = { Text("Claude API key") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = model,
            onValueChange = { model = it; settings.model = it },
            label = { Text("Model (e.g. claude-sonnet-4-6)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        ListItem(
            headlineContent = { Text("Auto-start on VIN") },
            supportingContent = { Text("Run agent when a 17-char VIN is detected in the X431 app") },
            trailingContent = { Switch(checked = autoStart, onCheckedChange = { autoStart = it; settings.autoStartOnVin = it }) },
        )
        ListItem(
            headlineContent = { Text("Fully autonomous actuation") },
            supportingContent = { Text("Allow the agent to run bidirectional tests without per-action confirmation. Disable for read-only mode.") },
            trailingContent = { Switch(checked = autonomous, onCheckedChange = { autonomous = it; settings.autonomousActuation = it }) },
        )
        ListItem(
            headlineContent = { Text("Confirm bidirectional tests") },
            supportingContent = { Text("When on, the agent must request approval (Approvals screen) before any actuation.") },
            trailingContent = { Switch(checked = approval, onCheckedChange = { approval = it; settings.requireApproval = it }) },
        )
        ListItem(
            headlineContent = { Text("Kill switch") },
            supportingContent = { Text("Stops any running agent immediately and blocks new sessions.") },
            trailingContent = { Switch(checked = kill, onCheckedChange = { kill = it; settings.killSwitch = it }) },
        )
        Button(onClick = onBack) { Text("Back") }
    }
}
