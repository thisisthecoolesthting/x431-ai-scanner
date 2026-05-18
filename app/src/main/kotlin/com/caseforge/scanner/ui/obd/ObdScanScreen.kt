@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.caseforge.scanner.ui.obd

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.caseforge.scanner.agent.ObdBluetoothTool
import com.caseforge.scanner.ai.ClaudeClient
import com.caseforge.scanner.data.SettingsRepo
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Standalone diagnostic screen that talks to a cheap ELM327 OBD-II Bluetooth dongle.
 * No X431 app required — this is Launch AI's daily-driver custom UI:
 *   - Connect / disconnect
 *   - Read stored + pending DTCs (Mode 03 / 07)
 *   - Ask Claude to explain any code
 *   - Clear codes (Mode 04)
 *   - Live data: RPM, coolant, speed, throttle, MAP, intake temp polled in a loop
 */
@Composable
fun ObdScanScreen(
    settings: SettingsRepo,
    onBack: () -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var connected by remember { mutableStateOf(ObdBluetoothTool.isConnected()) }
    var status by remember {
        mutableStateOf(
            if (connected) "Connected to ${ObdBluetoothTool.connectedDeviceName() ?: "dongle"}"
            else "Not connected. Pair your OBD dongle in Android Settings first."
        )
    }
    var busy by remember { mutableStateOf(false) }
    var dtcsText by remember { mutableStateOf("") }
    var codes by remember { mutableStateOf(emptyList<String>()) }
    var explanationFor by remember { mutableStateOf<String?>(null) }
    var explanation by remember { mutableStateOf("") }

    // Live data poll
    var liveOn by remember { mutableStateOf(false) }
    var live by remember { mutableStateOf(LiveSnapshot()) }

    val btPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val allOk = results.values.all { it }
        if (allOk) {
            scope.launch {
                busy = true
                status = "Connecting…"
                val msg = ObdBluetoothTool.scanAndConnect()
                connected = ObdBluetoothTool.isConnected()
                status = msg
                busy = false
            }
        } else {
            status = "Bluetooth permission denied."
        }
    }

    fun requestConnect() {
        val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        val needed = perms.filter {
            ContextCompat.checkSelfPermission(ctx, it) != PackageManager.PERMISSION_GRANTED
        }
        if (needed.isEmpty()) {
            scope.launch {
                busy = true
                status = "Connecting…"
                val msg = ObdBluetoothTool.scanAndConnect()
                connected = ObdBluetoothTool.isConnected()
                status = msg
                busy = false
            }
        } else {
            btPermLauncher.launch(needed.toTypedArray())
        }
    }

    // Live-data poll loop
    LaunchedEffect(liveOn, connected) {
        if (liveOn && connected) {
            while (isActive && liveOn && connected) {
                live = LiveSnapshot(
                    rpm = pidNumberOrNull(ObdBluetoothTool.readPid("0C")),
                    coolantC = pidNumberOrNull(ObdBluetoothTool.readPid("05")),
                    speedKmh = pidNumberOrNull(ObdBluetoothTool.readPid("0D")),
                    throttlePct = pidNumberOrNull(ObdBluetoothTool.readPid("11")),
                    intakeC = pidNumberOrNull(ObdBluetoothTool.readPid("0F")),
                    mapKpa = pidNumberOrNull(ObdBluetoothTool.readPid("0B")),
                    raw = "${live.raw}",
                    ts = System.currentTimeMillis(),
                )
                delay(750)
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Scan vehicle") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
            },
        )
        Column(
            Modifier.fillMaxSize().padding(14.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Connection card
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(
                            if (connected) Icons.Default.Bluetooth else Icons.Default.BluetoothDisabled,
                            contentDescription = null,
                            tint = if (connected) Color(0xFF2E7D32) else MaterialTheme.colorScheme.outline,
                        )
                        Text(
                            if (connected) "Connected" else "Not connected",
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                    Text(status, style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (!connected) {
                            Button(onClick = { requestConnect() }, enabled = !busy) {
                                Text(if (busy) "Working…" else "Connect to OBD dongle")
                            }
                        } else {
                            OutlinedButton(
                                onClick = {
                                    ObdBluetoothTool.disconnect()
                                    connected = false
                                    liveOn = false
                                    status = "Disconnected."
                                },
                                enabled = !busy,
                            ) { Text("Disconnect") }
                        }
                    }
                    Text(
                        "Pair your ELM327 / OBDLink / Veepeak dongle in Android Bluetooth Settings " +
                            "first (it usually shows up as OBDII or similar with PIN 1234/0000). " +
                            "Then return here and tap Connect.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }

            // Scan actions
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    modifier = Modifier.weight(1f),
                    enabled = connected && !busy,
                    onClick = {
                        scope.launch {
                            busy = true
                            dtcsText = "Reading codes…"
                            codes = emptyList(); explanation = ""; explanationFor = null
                            val txt = ObdBluetoothTool.readDtcs()
                            dtcsText = txt
                            codes = Regex("[PCBU][0-9A-F]{4}").findAll(txt).map { it.value }.distinct().toList()
                            busy = false
                        }
                    },
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Scan for DTCs")
                }
                FilledTonalButton(
                    modifier = Modifier.weight(1f),
                    enabled = connected && !busy,
                    onClick = {
                        scope.launch {
                            busy = true
                            dtcsText = "Clearing codes…"
                            val txt = ObdBluetoothTool.clearCodes()
                            dtcsText = txt
                            codes = emptyList(); explanation = ""; explanationFor = null
                            busy = false
                        }
                    },
                ) {
                    Icon(Icons.Default.CleaningServices, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Clear codes")
                }
            }

            // DTC results
            if (dtcsText.isNotBlank()) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Codes", fontWeight = FontWeight.SemiBold)
                        Text(dtcsText, style = MaterialTheme.typography.bodySmall)
                        if (codes.isNotEmpty()) {
                            HorizontalDivider()
                            codes.forEach { code ->
                                Row(
                                    Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Text(code, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                    OutlinedButton(
                                        enabled = !busy,
                                        onClick = {
                                            scope.launch {
                                                busy = true
                                                explanationFor = code
                                                explanation = "Asking Claude…"
                                                explanation = explainCode(settings, code)
                                                busy = false
                                            }
                                        },
                                    ) { Text("Explain with AI") }
                                }
                            }
                            if (explanationFor != null) {
                                HorizontalDivider()
                                Text("$explanationFor — explanation", fontWeight = FontWeight.SemiBold)
                                Text(explanation, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }

            // Live data
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Bolt, contentDescription = null)
                        Text("Live data", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        if (liveOn) {
                            OutlinedButton(onClick = { liveOn = false }, enabled = connected) {
                                Icon(Icons.Default.Stop, contentDescription = null); Spacer(Modifier.width(6.dp)); Text("Stop")
                            }
                        } else {
                            Button(onClick = { liveOn = true }, enabled = connected) {
                                Icon(Icons.Default.Refresh, contentDescription = null); Spacer(Modifier.width(6.dp)); Text("Stream")
                            }
                        }
                    }
                    LiveRow("RPM", live.rpm?.let { "%.0f".format(it) } ?: "—")
                    LiveRow("Vehicle speed", live.speedKmh?.let { "%.0f km/h".format(it) } ?: "—")
                    LiveRow("Coolant temp", live.coolantC?.let { "%.0f °C".format(it) } ?: "—")
                    LiveRow("Intake air temp", live.intakeC?.let { "%.0f °C".format(it) } ?: "—")
                    LiveRow("Throttle", live.throttlePct?.let { "%.0f %%".format(it) } ?: "—")
                    LiveRow("MAP", live.mapKpa?.let { "%.0f kPa".format(it) } ?: "—")
                }
            }

            Text(
                "OBD-II Mode 01/03/04/07 over Bluetooth SPP. Works with any standard 16-pin port " +
                    "(1996+ US, 2001+ EU petrol, 2003+ EU diesel). For OEM-specific tests " +
                    "(key programming, ABS bleed, etc.) use Full Scan on the dashboard, which drives " +
                    "the X431 app.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

@Composable
private fun LiveRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}

private data class LiveSnapshot(
    val rpm: Double? = null,
    val coolantC: Double? = null,
    val speedKmh: Double? = null,
    val throttlePct: Double? = null,
    val intakeC: Double? = null,
    val mapKpa: Double? = null,
    val raw: String = "",
    val ts: Long = 0L,
)

/** Pull the first numeric value out of an ObdBluetoothTool.readPid() formatted string. */
private fun pidNumberOrNull(formatted: String): Double? {
    val m = Regex("(-?\\d+(?:\\.\\d+)?)").find(formatted) ?: return null
    return m.value.toDoubleOrNull()
}

private suspend fun explainCode(settings: SettingsRepo, code: String): String {
    val key = settings.claudeApiKey
    if (key.isBlank()) return "Set a Claude API key in Settings first."
    return try {
        val client = ClaudeClient(apiKey = key, model = settings.model)
        val prompt = "DTC $code. Give: (1) one-line meaning, (2) likely causes in order of probability, " +
            "(3) cheap tests a tech can run in 10 min. Be concise — bullet style, no preamble."
        val resp = client.sendMessages(
            system = "You are a master automotive technician. Give terse, useful answers.",
            messages = listOf(ClaudeClient.userText(prompt)),
            maxTokens = 700,
        )
        resp.firstText().orEmpty().ifBlank { "(no response)" }
    } catch (t: Throwable) {
        "Error: ${t.message?.take(200) ?: t.javaClass.simpleName}"
    }
}
