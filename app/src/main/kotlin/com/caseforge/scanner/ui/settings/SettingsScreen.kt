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
import com.caseforge.scanner.BuildConfig
import com.caseforge.scanner.R
import com.caseforge.scanner.data.SettingsRepo
import androidx.compose.ui.res.stringResource

@Composable
fun SettingsScreen(
    settings: SettingsRepo,
    onBack: () -> Unit,
    onOpenDataExport: (() -> Unit)? = null,
    onOpenDirectVciProbe: (() -> Unit)? = null,
    onOpenVciDiagnostics: (() -> Unit)? = null,
    onCheckUpdate: (() -> Unit)? = null,
) {
    var apiKey by remember { mutableStateOf(settings.claudeApiKey) }
    var keyVisible by remember { mutableStateOf(false) }
    var autonomous by remember { mutableStateOf(settings.autonomousActuation) }
    var autoStart by remember { mutableStateOf(settings.autoStartOnVin) }
    var kill by remember { mutableStateOf(settings.killSwitch) }
    var approval by remember { mutableStateOf(settings.requireApproval) }
    var speak by remember { mutableStateOf(settings.speakEnabled) }
    var voice by remember { mutableStateOf(settings.voiceEnabled) }
    var theme by remember { mutableStateOf(settings.themeMode) }
    var overlayOnOemDiag by remember { mutableStateOf(settings.overlayOnOemDiag) }
    var directVci by remember { mutableStateOf(settings.directVciExperimental) }
    val context = LocalContext.current
    val recordAudioLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        voice = granted; settings.voiceEnabled = granted
    }
    val btConnectLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        if (results[Manifest.permission.BLUETOOTH_CONNECT] == true) {
            directVci = true
            settings.directVciExperimental = true
        }
    }

    fun enableDirectVciWithPermissions() {
        val perms = buildList {
            add(Manifest.permission.BLUETOOTH_CONNECT)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_SCAN)
            }
        }
        val missing = perms.any {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing) {
            btConnectLauncher.launch(perms.toTypedArray())
        } else {
            directVci = true
            settings.directVciExperimental = true
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("Settings") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } })
        Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(R.string.build_info_label, BuildConfig.BUILD_INFO),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (onCheckUpdate != null) {
                        Button(onClick = onCheckUpdate, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.check_for_update))
                        }
                    }
                }
            }
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
            ListItem(headlineContent = { Text("Show overlay on the OEM diagnostic app") }, trailingContent = { Switch(checked = overlayOnOemDiag, onCheckedChange = { overlayOnOemDiag = it; settings.overlayOnOemDiag = it }) })
            ListItem(headlineContent = { Text("Kill switch") }, trailingContent = { Switch(checked = kill, onCheckedChange = { kill = it; settings.killSwitch = it }) })

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Developer / Experimental", style = MaterialTheme.typography.titleSmall)
                    ListItem(
                        headlineContent = { Text("Direct VCI (experimental)") },
                        supportingContent = { Text("Bypass OEM diagnostic app; generic OBD-II over Bluetooth dongle.") },
                        trailingContent = {
                            Switch(
                                checked = directVci,
                                onCheckedChange = { on ->
                                    if (on) enableDirectVciWithPermissions()
                                    else {
                                        directVci = false
                                        settings.directVciExperimental = false
                                    }
                                },
                            )
                        },
                    )
                    if (directVci && onOpenVciDiagnostics != null) {
                        OutlinedButton(onClick = onOpenVciDiagnostics, modifier = Modifier.fillMaxWidth()) {
                            Text("Direct VCI connection diagnostics")
                        }
                    }
                    if (directVci && onOpenDirectVciProbe != null) {
                        OutlinedButton(onClick = onOpenDirectVciProbe, modifier = Modifier.fillMaxWidth()) {
                            Text("Open Direct VCI probe")
                        }
                    }
                    if (onOpenDataExport != null) {
                        Text(
                            stringResource(R.string.export_screen_body),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Button(onClick = onOpenDataExport, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.export_settings_entry))
                        }
                    }
                }
            }
        }
    }
}
