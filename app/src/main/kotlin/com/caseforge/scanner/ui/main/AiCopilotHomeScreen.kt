@file:OptIn(
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
)

package com.caseforge.scanner.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.caseforge.scanner.engine.EngineState
import com.caseforge.scanner.overlay.compose.LiveActivityTicker
import kotlinx.coroutines.launch

private data class CopilotMessage(
    val role: CopilotRole,
    val text: String,
    val actions: List<CopilotAction> = emptyList(),
)

private enum class CopilotRole { User, Assistant }

@Composable
fun AiCopilotHomeScreen(
    vciConnected: Boolean,
    vin: String?,
    engineBusy: Boolean,
    engineState: EngineState,
    buildInfo: String,
    onCopilotAction: (CopilotAction) -> Unit,
    onCheckUpdate: () -> Unit,
) {
    val messages = remember { mutableStateListOf<CopilotMessage>() }
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val suggestedChips = listOf(
        "Scan this vehicle" to CopilotAction.ScanVehicle,
        "Explain current codes" to CopilotAction.ExplainCurrentCodes,
        "Start live data" to CopilotAction.StartLiveData,
        "Check recalls" to CopilotAction.CheckRecalls,
        "Build customer report" to CopilotAction.BuildCustomerReport,
        "Send data to PC" to CopilotAction.SendDataToPc,
    )

    fun appendAssistant(text: String, actions: List<CopilotAction>) {
        messages.add(CopilotMessage(CopilotRole.Assistant, text, actions))
    }

    fun respondToSymptom(symptom: String) {
        val trimmed = symptom.trim()
        if (trimmed.isEmpty()) return
        messages.add(CopilotMessage(CopilotRole.User, trimmed))
        val actions = buildSuggestedActions(vciConnected, engineState)
        val lead = when {
            !vciConnected ->
                "I'll help with \"$trimmed\". Connect the OBD cable first, then run a scan."
            engineState.dtcs.isNotEmpty() ->
                "Got it — \"$trimmed\". You have ${engineState.dtcs.size} code(s) on file. Tap an action below."
            else ->
                "Got it — \"$trimmed\". When you're ready, run a scan or pick another action."
        }
        appendAssistant(lead, actions)
        onCopilotAction(CopilotAction.SubmitSymptom(trimmed))
    }

    fun dispatch(action: CopilotAction) {
        when (action) {
            is CopilotAction.SubmitSymptom -> respondToSymptom(action.text)
            else -> onCopilotAction(action)
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Together Copilot") },
            actions = {
                val dot = if (vciConnected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                }
                TextButton(onClick = { dispatch(CopilotAction.ConnectUsbObd) }) {
                    Text(
                        if (vciConnected) "Connected ●" else "Connect",
                        color = dot,
                    )
                }
                IconButton(onClick = onCheckUpdate) {
                    Icon(Icons.Default.SystemUpdate, contentDescription = "Check for update")
                }
            },
        )
        Text(
            buildInfo,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LiveActivityTicker(engineState = engineState)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    vin?.let { "VIN: $it" } ?: "No VIN yet — connect and scan",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    if (vciConnected) "Link ready" else "Disconnected — tap Connect or use an action card",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                engineState.errorBanner?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 8.dp),
        ) {
            if (messages.isEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        ),
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                "Tell Together what you're seeing",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                "Example: \"2014 Silverado misfire at idle\", \"No start\", \"Clear codes after repair\"",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            items(
                count = messages.size,
                key = { index -> "${messages[index].role}-$index-${messages[index].text.hashCode()}" },
            ) { index ->
                val msg = messages[index]
                CopilotBubble(message = msg, enabled = !engineBusy, onAction = { dispatch(it) })
            }
        }
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            suggestedChips.forEach { (label, action) ->
                AssistChip(
                    onClick = {
                        when (action) {
                            CopilotAction.ScanVehicle,
                            CopilotAction.ExplainCurrentCodes,
                            CopilotAction.StartLiveData,
                            -> {
                                messages.add(CopilotMessage(CopilotRole.User, label))
                                appendAssistant(
                                    "Opening $label…",
                                    buildSuggestedActions(vciConnected, engineState),
                                )
                                dispatch(action)
                            }
                            else -> dispatch(action)
                        }
                    },
                    label = { Text(label) },
                    enabled = !engineBusy,
                )
            }
        }
        Surface(shadowElevation = 8.dp) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    placeholder = {
                        Text(
                            "Tell Together what you're seeing: misfire at idle, no start…",
                        )
                    },
                    maxLines = 3,
                    enabled = !engineBusy,
                )
                Button(
                    onClick = {
                        val text = input.trim()
                        if (text.isEmpty()) return@Button
                        input = ""
                        scope.launch { respondToSymptom(text) }
                    },
                    enabled = !engineBusy && input.isNotBlank(),
                ) {
                    Text("Send")
                }
            }
        }
        Surface(tonalElevation = 2.dp) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                TextButton(onClick = { dispatch(CopilotAction.OpenScannerConsole) }) {
                    Text("Console")
                }
                TextButton(onClick = { dispatch(CopilotAction.OpenHistory) }) {
                    Icon(Icons.Default.History, contentDescription = null)
                    Text("History")
                }
                TextButton(onClick = { dispatch(CopilotAction.OpenSettings) }) {
                    Icon(Icons.Default.Settings, contentDescription = null)
                    Text("Settings")
                }
                TextButton(onClick = { dispatch(CopilotAction.OpenDiagnostics) }) {
                    Text("Diagnostics")
                }
            }
        }
    }
}

private fun buildSuggestedActions(vciConnected: Boolean, engineState: EngineState): List<CopilotAction> {
    val actions = mutableListOf<CopilotAction>()
    if (!vciConnected) {
        actions += CopilotAction.ConnectUsbObd
    } else {
        actions += CopilotAction.RunObdScan
        if (engineState.dtcs.isNotEmpty()) {
            actions += CopilotAction.ExplainCurrentCodes
            actions += CopilotAction.GenerateRepairStory
        }
        actions += CopilotAction.OpenLiveData
    }
    actions += CopilotAction.CheckRecalls
    actions += CopilotAction.BuildCustomerReport
    actions += CopilotAction.SendDataToPc
    if (engineState.dtcs.isNotEmpty() && vciConnected) {
        actions += CopilotAction.ClearCodes
    }
    if (engineState.dtcs.isNotEmpty()) {
        actions += CopilotAction.ShareReport
    }
    return actions.distinct()
}

@Composable
private fun CopilotBubble(
    message: CopilotMessage,
    enabled: Boolean,
    onAction: (CopilotAction) -> Unit,
) {
    val align = if (message.role == CopilotRole.User) Alignment.End else Alignment.Start
    val container = if (message.role == CopilotRole.User) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = align,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = container),
            modifier = Modifier.fillMaxWidth(if (message.role == CopilotRole.User) 0.92f else 1f),
        ) {
            Text(
                message.text,
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (message.actions.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                message.actions.forEach { action ->
                    CopilotActionCard(action = action, enabled = enabled, onClick = { onAction(action) })
                }
            }
        }
    }
}

@Composable
private fun CopilotActionCard(
    action: CopilotAction,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val (title, subtitle) = actionLabels(action)
    FilledTonalButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(vertical = 4.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun actionLabels(action: CopilotAction): Pair<String, String> = when (action) {
    CopilotAction.ScanVehicle, CopilotAction.RunObdScan ->
        "Run OBD-II scan" to "Read stored and pending codes"
    CopilotAction.ExplainCurrentCodes ->
        "Explain current codes" to "Plain-English causes and tests"
    CopilotAction.StartLiveData, CopilotAction.OpenLiveData ->
        "Open live data" to "RPM, trims, voltage, and more"
    CopilotAction.CheckRecalls ->
        "Check recalls" to "NHTSA lookup for this VIN"
    CopilotAction.BuildCustomerReport, CopilotAction.GenerateRepairStory ->
        "Generate repair story" to "Customer-ready summary and PDF"
    CopilotAction.SendDataToPc ->
        "Send data to PC" to "LAN export for office training"
    CopilotAction.ConnectUsbObd ->
        "Connect USB OBD" to "Probe cable or Bluetooth dongle"
    CopilotAction.ClearCodes ->
        "Clear codes" to "Requires confirmation"
    CopilotAction.ShareReport ->
        "Share report" to "SMS, email, or save PDF"
    CopilotAction.OpenScannerConsole ->
        "Scanner console" to "Tile-first home layout"
    CopilotAction.OpenHistory ->
        "History" to "Past sessions"
    CopilotAction.OpenSettings ->
        "Settings" to "Home mode, transport, receiver"
    CopilotAction.OpenDiagnostics ->
        "Diagnostics" to "VCI probe and logs"
    is CopilotAction.SubmitSymptom ->
        "Continue" to "Use suggested actions above"
}
