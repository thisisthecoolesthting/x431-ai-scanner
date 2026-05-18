@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.caseforge.scanner.ui.dashboard

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.caseforge.scanner.BuildConfig
import com.caseforge.scanner.agent.AgentStatus

/**
 * Unified diagnostic dashboard. Becomes the app's main view. Three regions:
 *   1. Vehicle card (top) — VIN, year/make/model, last seen, status badge
 *   2. Action grid — Full Scan, Talk, Key Programming, Oil Reset, Clear Codes, etc.
 *   3. Live ticker / mini-chat — what the agent is doing now, voice mic, send box
 *
 * Setup steps moved behind the gear icon (top-right).
 */
@Composable
fun DashboardScreen(
    detectedVin: String?,
    vehicleSummary: String?,
    speakEnabled: Boolean,
    onSpeakToggle: (Boolean) -> Unit,
    onAgentStart: (symptom: String?) -> Unit,
    onAgentStop: () -> Unit,
    onFullScan: () -> Unit,
    onQuickProcedure: (id: String, label: String) -> Unit,
    onOpenSetup: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenLog: () -> Unit,
    onOpenNotes: () -> Unit,
    onCheckUpdate: () -> Unit,
    onTakeOverX431: () -> Unit = {},
) {
    val running by AgentStatus.running.collectAsState()
    val step by AgentStatus.step.collectAsState()
    val activity by AgentStatus.activity.collectAsState()

    var chatInput by remember { mutableStateOf("") }

    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val text = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull().orEmpty()
            if (text.isNotBlank()) {
                chatInput = if (chatInput.isBlank()) text else "$chatInput $text"
            }
        }
    }

    fun launchSpeech() {
        speechLauncher.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Tell the agent what to do")
        })
    }

    Column(Modifier.fillMaxSize()) {
        // Top app bar with title + settings/history icons
        TopAppBar(
            title = { Text("Launch AI") },
            actions = {
                IconButton(onClick = { onSpeakToggle(!speakEnabled) }) {
                    Icon(
                        if (speakEnabled) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                        contentDescription = if (speakEnabled) "Mute voice" else "Unmute voice",
                    )
                }
IconButton(onClick = onOpenNotes) {
                    Icon(Icons.Default.Notes, contentDescription = "Agent notes")
                }
                IconButton(onClick = onOpenHistory) {
                    Icon(Icons.Default.DirectionsCar, contentDescription = "History")
                }
                IconButton(onClick = onOpenLog) {
                    Icon(Icons.Default.Build, contentDescription = "Log")
                }
                IconButton(onClick = onOpenSetup) {
                    Icon(Icons.Default.Settings, contentDescription = "Setup")
                }
            },
        )

        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Vehicle card
            VehicleCard(detectedVin, vehicleSummary, running, step, activity)

            // Take-over-X431 — the headline feature: hide X431 behind our UI
            Button(
                onClick = onTakeOverX431,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.Layers, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Take over X431 (custom UI)")
            }

            // Action grid
            Text("Quick actions", style = MaterialTheme.typography.titleMedium)
            ActionGrid(
                running = running,
                onAgentStart = { onAgentStart(null) },
                onAgentStop = onAgentStop,
                onFullScan = onFullScan,
                onQuickProcedure = onQuickProcedure,
            )

            Text(
                "Build ${BuildConfig.BUILD_INFO}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }

        // Sticky bottom: chat input + mic + send
        Surface(
            shadowElevation = 8.dp,
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Row(
                Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = chatInput,
                    onValueChange = { chatInput = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Tell agent: \"check ABS\", \"why won't it crank\"…") },
                    singleLine = false,
                    maxLines = 3,
                    trailingIcon = {
                        IconButton(onClick = { launchSpeech() }) {
                            Icon(Icons.Default.Mic, contentDescription = "Voice")
                        }
                    },
                )
                FilledIconButton(
                    onClick = {
                        val text = chatInput.trim()
                        chatInput = ""
                        onAgentStart(text.ifBlank { null })
                    },
                    enabled = !running,
                ) {
                    Icon(Icons.Default.Send, contentDescription = "Send")
                }
            }
        }
    }
}

@Composable
private fun VehicleCard(
    vin: String?,
    summary: String?,
    running: Boolean,
    step: Int,
    activity: String,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Default.DirectionsCar, contentDescription = null)
                Column(Modifier.weight(1f)) {
                    Text(
                        if (vin != null) "VIN: $vin" else "No vehicle detected",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        summary ?: "Connect the VCI and open the X431 app to detect a vehicle.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // Status badge
                val (badgeColor, badgeText) = if (running)
                    Color(0xFF2E7D32) to "RUNNING"
                else
                    Color(0xFF616161) to "IDLE"
                Box(
                    Modifier.background(badgeColor, RoundedCornerShape(6.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(badgeText, color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }
            }
            HorizontalDivider()
            // Live ticker row
            if (running) {
                Text(
                    "Step $step: $activity",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            } else {
                Text(
                    activity.takeIf { it.isNotBlank() && it != "Idle" }
                        ?: "Tap an action below or speak a goal.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private data class QuickAction(val id: String, val label: String, val subtitle: String)

private val QUICK_ACTIONS = listOf(
    QuickAction("clear_codes", "Clear codes", "after a repair, clear stored DTCs"),
    QuickAction("oil_reset", "Oil reset", "service reminder relearn"),
    QuickAction("battery_reg", "Battery register", "after replacement / BMS reset"),
    QuickAction("tpms", "TPMS relearn", "after sensor swap / tire rotation"),
    QuickAction("epb", "EPB service", "retract caliper for brake pad job"),
    QuickAction("sas", "Steering angle", "post-alignment / wheel work"),
    QuickAction("throttle", "Throttle relearn", "after TB clean / replace"),
    QuickAction("key_program", "Program key", "add a new key / fob"),
    QuickAction("dpf_regen", "DPF regen", "diesel particulate filter burn"),
    QuickAction("injector_code", "Injector coding", "after injector replacement"),
)

@Composable
private fun ActionGrid(
    running: Boolean,
    onAgentStart: () -> Unit,
    onAgentStop: () -> Unit,
    onFullScan: () -> Unit,
    onQuickProcedure: (id: String, label: String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Primary row — Full Scan + Start/Stop agent
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = onFullScan,
                enabled = !running,
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Full Scan")
            }
            if (running) {
                FilledTonalButton(onClick = onAgentStop, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Stop, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Stop")
                }
            } else {
                Button(onClick = onAgentStart, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Build, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Start Agent")
                }
            }
        }
        // Secondary grid — 2-wide
        QUICK_ACTIONS.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                pair.forEach { qa ->
                    OutlinedCard(
                        onClick = { if (!running) onQuickProcedure(qa.id, qa.label) },
                        modifier = Modifier.weight(1f),
                        enabled = !running,
                    ) {
                        Column(Modifier.padding(10.dp)) {
                            Text(qa.label, fontWeight = FontWeight.SemiBold)
                            Text(
                                qa.subtitle,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                if (pair.size == 1) Box(Modifier.weight(1f))
            }
        }
    }
}
