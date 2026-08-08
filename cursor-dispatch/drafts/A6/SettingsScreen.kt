@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.caseforge.scanner.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.caseforge.scanner.data.SettingsRepo

@Composable
fun SettingsScreen(settings: SettingsRepo, onBack: () -> Unit) {
    var apiKey by remember { mutableStateOf(settings.claudeApiKey) }
    var keyVisible by remember { mutableStateOf(false) }
    var autonomous by remember { mutableStateOf(settings.autonomousActuation) }
    var autoStart by remember { mutableStateOf(settings.autoStartOnVin) }
    var kill by remember { mutableStateOf(settings.killSwitch) }
    var approval by remember { mutableStateOf(settings.requireApproval) }
    var speak by remember { mutableStateOf(settings.speakEnabled) }
    var theme by remember { mutableStateOf(settings.themeMode) }
    // A6: overlay-on-X431 toggle
    var overlayOnX431 by remember { mutableStateOf(settings.overlayOnX431) }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Settings") },
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
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Anthropic API key", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Stored encrypted on this tablet only. Paste once.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it; settings.claudeApiKey = it },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("sk-ant-api03-…") },
                        visualTransformation = if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            TextButton(onClick = { keyVisible = !keyVisible }) {
                                Text(if (keyVisible) "Hide" else "Show")
                            }
                        },
                    )
                }
            }
            ListItem(
                headlineContent = { Text("Auto-start on VIN") },
                supportingContent = { Text("Run agent when a 17-char VIN is detected in the X431 app") },
                trailingContent = { Switch(checked = autoStart, onCheckedChange = { autoStart = it; settings.autoStartOnVin = it }) },
            )
            ListItem(
                headlineContent = { Text("Fully autonomous actuation") },
                supportingContent = { Text("Allow bidirectional tests without per-action confirmation.") },
                trailingContent = { Switch(checked = autonomous, onCheckedChange = { autonomous = it; settings.autonomousActuation = it }) },
            )
            ListItem(
                headlineContent = { Text("Theme") },
                supportingContent = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("system", "light", "dark").forEach { mode ->
                            FilterChip(
                                selected = theme == mode,
                                onClick = { theme = mode; settings.themeMode = mode },
                                label = { Text(mode.replaceFirstChar { it.uppercase() }) },
                            )
                        }
                    }
                },
            )
            ListItem(
                headlineContent = { Text("Speak ticker out loud (TTS)") },
                supportingContent = { Text("Hands-free under the dash. Agent narrates every step.") },
                trailingContent = { Switch(checked = speak, onCheckedChange = { speak = it; settings.speakEnabled = it }) },
            )
            ListItem(
                headlineContent = { Text("Confirm bidirectional tests") },
                supportingContent = { Text("When on, the agent must request approval before any actuation.") },
                trailingContent = { Switch(checked = approval, onCheckedChange = { approval = it; settings.requireApproval = it }) },
            )
            // A6: Show overlay on X431
            ListItem(
                headlineContent = { Text("Show overlay on X431") },
                supportingContent = { Text("Automatically overlay diagnostics when the X431 app is active") },
                trailingContent = {
                    Switch(
                        checked = overlayOnX431,
                        onCheckedChange = { overlayOnX431 = it; settings.overlayOnX431 = it },
                    )
                },
            )
            ListItem(
                headlineContent = { Text("Kill switch") },
                supportingContent = { Text("Stops any running agent immediately and blocks new sessions.") },
                trailingContent = { Switch(checked = kill, onCheckedChange = { kill = it; settings.killSwitch = it }) },
            )
        }
    }
}
