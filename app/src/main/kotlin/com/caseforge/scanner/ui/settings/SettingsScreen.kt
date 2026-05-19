@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.caseforge.scanner.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.caseforge.scanner.BuildConfig
import com.caseforge.scanner.R
import com.caseforge.scanner.data.FastWorkflowState
import com.caseforge.scanner.data.SettingsRepo
import com.caseforge.scanner.transfer.TransferDeliveryMode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    var homeMode by remember { mutableStateOf(settings.homeMode) }
    var fastWorkflow by remember { mutableStateOf(settings.fastWorkflowState) }
    var showReceiverAdvanced by remember { mutableStateOf(false) }
    var receiverHost by remember { mutableStateOf(settings.receiverPcHost) }
    var receiverPortText by remember { mutableStateOf(settings.receiverPcPort.toString()) }
    var multipartFallback by remember { mutableStateOf(settings.useMultipartFallback) }
    var transferMode by remember { mutableStateOf(settings.transferDeliveryMode) }
    var transferDropUrl by remember { mutableStateOf(settings.transferDropUrl) }
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

    fun refreshFastWorkflow() {
        fastWorkflow = settings.fastWorkflowState
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
        TopAppBar(
            title = { Text("Settings") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            },
        )
        Column(
            Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(R.string.build_info_label, BuildConfig.BUILD_INFO),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (onCheckUpdate != null) {
                        Button(
                            onClick = onCheckUpdate,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        ) {
                            Text(stringResource(R.string.check_for_update))
                        }
                    }
                }
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        stringResource(R.string.settings_section_app_behavior),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        stringResource(R.string.settings_home_mode_label),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(
                            selected = homeMode == SettingsRepo.HOME_SCANNER_CONSOLE,
                            onClick = {
                                homeMode = SettingsRepo.HOME_SCANNER_CONSOLE
                                settings.homeMode = SettingsRepo.HOME_SCANNER_CONSOLE
                            },
                            label = { Text(stringResource(R.string.settings_home_mode_scanner)) },
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                        )
                        FilterChip(
                            selected = homeMode == SettingsRepo.HOME_AI_COPILOT,
                            onClick = {
                                homeMode = SettingsRepo.HOME_AI_COPILOT
                                settings.homeMode = SettingsRepo.HOME_AI_COPILOT
                            },
                            label = { Text(stringResource(R.string.settings_home_mode_copilot)) },
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                        )
                    }
                }
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(R.string.settings_section_fast_workflow),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    FastWorkflowSummary(fastWorkflow)
                    OutlinedButton(
                        onClick = {
                            settings.fastWorkflowState = FastWorkflowState()
                            refreshFastWorkflow()
                        },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        enabled = fastWorkflow.hasAnyMemory,
                    ) {
                        Text(stringResource(R.string.settings_fast_clear_memory))
                    }
                }
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Vehicle data export", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Default is free Share (no paid upload API). Optional: your own server URL or LAN PC.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            selected = transferMode == TransferDeliveryMode.SHARE,
                            onClick = {
                                transferMode = TransferDeliveryMode.SHARE
                                settings.transferDeliveryMode = TransferDeliveryMode.SHARE
                            },
                            label = { Text("Share") },
                        )
                        FilterChip(
                            selected = transferMode == TransferDeliveryMode.SELF_HOSTED,
                            onClick = {
                                transferMode = TransferDeliveryMode.SELF_HOSTED
                                settings.transferDeliveryMode = TransferDeliveryMode.SELF_HOSTED
                            },
                            label = { Text("Your server") },
                        )
                        FilterChip(
                            selected = transferMode == TransferDeliveryMode.LAN_PC,
                            onClick = {
                                transferMode = TransferDeliveryMode.LAN_PC
                                settings.transferDeliveryMode = TransferDeliveryMode.LAN_PC
                            },
                            label = { Text("LAN PC") },
                        )
                    }
                    if (transferMode == TransferDeliveryMode.SELF_HOSTED) {
                        OutlinedTextField(
                            value = transferDropUrl,
                            onValueChange = {
                                transferDropUrl = it
                                settings.transferDropUrl = it
                            },
                            label = { Text("Drop URL (http://your-vps:8765)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    onOpenDataExport?.let { openExport ->
                        OutlinedButton(onClick = openExport, modifier = Modifier.fillMaxWidth()) {
                            Text("Open export screen")
                        }
                    }
                }
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            stringResource(R.string.settings_section_receiver_advanced),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        TextButton(onClick = { showReceiverAdvanced = !showReceiverAdvanced }) {
                            Text(
                                stringResource(
                                    if (showReceiverAdvanced) R.string.settings_hide_advanced
                                    else R.string.settings_show_advanced,
                                ),
                            )
                        }
                    }
                    if (showReceiverAdvanced) {
                        OutlinedTextField(
                            value = receiverHost,
                            onValueChange = {
                                receiverHost = it
                                settings.receiverPcHost = it
                            },
                            label = { Text(stringResource(R.string.settings_receiver_host_label)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = receiverPortText,
                            onValueChange = { raw ->
                                receiverPortText = raw.filter { ch -> ch.isDigit() }.take(5)
                                receiverPortText.toIntOrNull()?.let { port ->
                                    settings.receiverPcPort = port.coerceIn(1, 65535)
                                }
                            },
                            label = { Text(stringResource(R.string.settings_receiver_port_label)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        ListItem(
                            headlineContent = { Text(stringResource(R.string.settings_receiver_multipart)) },
                            trailingContent = {
                                Switch(
                                    checked = multipartFallback,
                                    onCheckedChange = {
                                        multipartFallback = it
                                        settings.useMultipartFallback = it
                                    },
                                )
                            },
                        )
                    }
                }
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Anthropic API key", style = MaterialTheme.typography.titleSmall)
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it; settings.claudeApiKey = it },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = if (keyVisible) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
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
                trailingContent = {
                    Switch(checked = autoStart, onCheckedChange = { autoStart = it; settings.autoStartOnVin = it })
                },
            )
            ListItem(
                headlineContent = { Text("Fully autonomous actuation") },
                trailingContent = {
                    Switch(checked = autonomous, onCheckedChange = { autonomous = it; settings.autonomousActuation = it })
                },
            )
            ListItem(
                headlineContent = { Text("Theme") },
                supportingContent = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("system", "light", "dark").forEach { m ->
                            FilterChip(
                                selected = theme == m,
                                onClick = { theme = m; settings.themeMode = m },
                                label = { Text(m.replaceFirstChar { it.uppercase() }) },
                            )
                        }
                    }
                },
            )
            ListItem(
                headlineContent = { Text("Speak ticker out loud (TTS)") },
                trailingContent = {
                    Switch(checked = speak, onCheckedChange = { speak = it; settings.speakEnabled = it })
                },
            )
            ListItem(
                headlineContent = { Text("Enable Voice Mode") },
                supportingContent = { Text("Say \"Hey Together\" or hold the mic button in the overlay.") },
                trailingContent = {
                    Switch(
                        checked = voice,
                        onCheckedChange = { c ->
                            if (c) {
                                if (ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.RECORD_AUDIO,
                                    ) == PackageManager.PERMISSION_GRANTED
                                ) {
                                    voice = true
                                    settings.voiceEnabled = true
                                } else {
                                    recordAudioLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            } else {
                                voice = false
                                settings.voiceEnabled = false
                            }
                        },
                    )
                },
            )
            ListItem(
                headlineContent = { Text("Confirm bidirectional tests") },
                trailingContent = {
                    Switch(checked = approval, onCheckedChange = { approval = it; settings.requireApproval = it })
                },
            )
            ListItem(
                headlineContent = { Text("Show overlay on the OEM diagnostic app") },
                trailingContent = {
                    Switch(checked = overlayOnOemDiag, onCheckedChange = { overlayOnOemDiag = it; settings.overlayOnOemDiag = it })
                },
            )
            ListItem(
                headlineContent = { Text("Kill switch") },
                trailingContent = {
                    Switch(checked = kill, onCheckedChange = { kill = it; settings.killSwitch = it })
                },
            )

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
                        OutlinedButton(
                            onClick = onOpenVciDiagnostics,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        ) {
                            Text("Direct VCI connection diagnostics")
                        }
                    }
                    if (directVci && onOpenDirectVciProbe != null) {
                        OutlinedButton(
                            onClick = onOpenDirectVciProbe,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        ) {
                            Text("Open Direct VCI probe")
                        }
                    }
                    if (onOpenDataExport != null) {
                        Text(
                            stringResource(R.string.export_screen_body),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Button(
                            onClick = onOpenDataExport,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        ) {
                            Text(stringResource(R.string.export_settings_entry))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FastWorkflowSummary(state: FastWorkflowState) {
    if (!state.hasAnyMemory) {
        Text(
            stringResource(R.string.settings_fast_workflow_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    val transport = state.lastGoodTransport?.takeIf { it.isNotBlank() }
        ?: state.lastTransportLabel
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        state.lastVin?.takeIf { it.isNotBlank() }?.let { vin ->
            Text(stringResource(R.string.settings_fast_last_vin, vin), style = MaterialTheme.typography.bodyMedium)
        }
        transport?.takeIf { it.isNotBlank() }?.let { label ->
            Text(stringResource(R.string.settings_fast_last_transport, label), style = MaterialTheme.typography.bodyMedium)
        }
        state.lastBatteryVoltage?.let { v ->
            Text(
                stringResource(R.string.settings_fast_last_battery, String.format(Locale.US, "%.1f", v)),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        state.lastReceiverHost?.takeIf { it.isNotBlank() }?.let { host ->
            Text(stringResource(R.string.settings_fast_receiver_host, host), style = MaterialTheme.typography.bodyMedium)
        }
        if (state.lastSuccessfulScanAt > 0L) {
            Text(
                stringResource(R.string.settings_fast_last_scan, formatScanTime(state.lastSuccessfulScanAt)),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

private fun formatScanTime(epochMillis: Long): String {
    val fmt = SimpleDateFormat("MMM d, yyyy h:mm a", Locale.getDefault())
    return fmt.format(Date(epochMillis))
}
