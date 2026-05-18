@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.caseforge.scanner.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
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
    var voice by remember { mutableStateOf(settings.voiceEnabled) }
    var theme by remember { mutableStateOf(settings.themeMode) }
    var overlayOnX431 by remember { mutableStateOf(settings.overlayOnX431) }
    val context = LocalContext.current
    val recordAudioLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        voice = granted; settings.voiceEnabled = granted
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("Settings") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } })
        Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Anthropic API key", style = MaterialTheme.typography.titleSmall)
                    OutlinedTextField(value = apiKey, onValueChange = { apiKey = it; settings.claudeApiKey = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                        visualTransformation = if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = { TextButton(onClick = { keyVisible = !keyVisible }) { Text(if (keyVisible) "Hide" else "Show") } })
                }
            }
            ListItem(headlineContent = { Text("Auto-start on VIN") }, trailingContent = { Switch(checked = autoStart, onCheckedChange = { autoStart = it; settings.autoStartOnVin = it }) })
            ListItem(headlineContent = { Text("Fully autonomous actuation") }, trailingContent = { Switch(checked = autonomous, onCheckedChange = { autonomous = it; settings.autonomousActuation = it }) })
            ListItem(headlineContent = { Text("Theme") }, supportingContent = { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("system","light","dark").forEach { m -> FilterChip(theme==m,{ theme=m; settings.themeMode=m },{ Text(m.replaceFirstChar{it.uppercase()}) }) } } })
            ListItem(headlineContent = { Text("Speak ticker out loud (TTS)") }, trailingContent = { Switch(checked = speak, onCheckedChange = { speak = it; settings.speakEnabled = it }) })
            ListItem(
                headlineContent = { Text("Enable Voice Mode") },
                supportingContent = { Text("Say \"Hey Together\" or hold the mic button in the overlay.") },
                trailingContent = { Switch(checked = voice, onCheckedChange = { c ->
                    if (c) { if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) { voice=true; settings.voiceEnabled=true } else recordAudioLauncher.launch(Manifest.permission.RECORD_AUDIO) }
                    else { voice=false; settings.voiceEnabled=false } }) },
            )
            ListItem(headlineContent = { Text("Confirm bidirectional tests") }, trailingContent = { Switch(checked = approval, onCheckedChange = { approval = it; settings.requireApproval = it }) })
            ListItem(headlineContent = { Text("Show overlay on X431") }, trailingContent = { Switch(checked = overlayOnX431, onCheckedChange = { overlayOnX431 = it; settings.overlayOnX431 = it }) })
            ListItem(headlineContent = { Text("Kill switch") }, trailingContent = { Switch(checked = kill, onCheckedChange = { kill = it; settings.killSwitch = it }) })
        }
    }
}
