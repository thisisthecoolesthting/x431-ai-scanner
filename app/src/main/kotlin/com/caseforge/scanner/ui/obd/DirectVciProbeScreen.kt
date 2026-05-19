@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.caseforge.scanner.ui.obd

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
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
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.caseforge.scanner.App
import com.caseforge.scanner.vci.VciCommunicator
import com.caseforge.scanner.vci.VciProtocolConfig
import com.caseforge.scanner.vci.VciProtocolProbe
import com.caseforge.scanner.vci.BluetoothVciClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Day-1 protocol probe: sweep header magic + hex/binary transport, persist winners to settings.
 */
@Composable
fun DirectVciProbeScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val app = ctx.applicationContext as App
    val settings = app.settings
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        VciProtocolConfig.applyFromSettings(settings)
    }

    var status by remember {
        mutableStateOf("Pair the OEM VCI, connect vehicle ignition ON, then run sweep or single read.")
    }
    var busy by remember { mutableStateOf(false) }
    var logText by remember { mutableStateOf("") }
    var useHex by remember { mutableStateOf(settings.vciUseHexEncoding) }

    val btLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        if (results.values.all { it }) {
            scope.launch { runSingleRead(ctx, settings, useHex, { busy = it }, { status = it }, { logText = it }) }
        } else {
            status = "Bluetooth permissions denied."
        }
    }

    fun requestBtThen(block: suspend () -> Unit) {
        val perms = buildList {
            add(Manifest.permission.BLUETOOTH_CONNECT)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_SCAN)
            }
        }
        val missing = perms.any {
            ContextCompat.checkSelfPermission(ctx, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing) btLauncher.launch(perms.toTypedArray())
        else scope.launch { block() }
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Direct VCI probe") },
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
            if (settings.vciProtocolConfirmed) {
                AssistChip(
                    onClick = {},
                    label = {
                        Text(
                            "Confirmed: ${VciProtocolConfig.headerLabel(VciProtocolConfig.header)}" +
                                if (settings.vciUseHexEncoding) " hex-ASCII" else " binary",
                        )
                    },
                )
            }
            ListItem(
                headlineContent = { Text("Hex-ASCII transport") },
                trailingContent = {
                    Switch(checked = useHex, onCheckedChange = { useHex = it })
                },
            )
            Button(
                onClick = {
                    requestBtThen {
                        runProtocolSweep(ctx, settings, { busy = it }, { status = it }, { logText = it })
                    }
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (busy) "Working…" else "Sweep headers + transport (Day 1)")
            }
            OutlinedButton(
                onClick = {
                    requestBtThen {
                        runSingleRead(ctx, settings, useHex, { busy = it }, { status = it }, { logText = it })
                    }
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Single connect + read DTCs (current settings)")
            }
            Text(status, style = MaterialTheme.typography.bodyMedium)
            if (logText.isNotBlank()) {
                Text(logText, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private suspend fun runProtocolSweep(
    ctx: android.content.Context,
    settings: com.caseforge.scanner.data.SettingsRepo,
    setBusy: (Boolean) -> Unit,
    setStatus: (String) -> Unit,
    setLog: (String) -> Unit,
) {
    setBusy(true)
    setLog("")
    try {
        val client = BluetoothVciClient(ctx)
        val devices = withContext(Dispatchers.IO) { client.findBondedVciDevices() }
        if (devices.isEmpty()) {
            setStatus("No bonded VCI found.")
            return
        }
        val addr = devices.first().address
        setStatus("Sweeping ${devices.first().name ?: addr}…")
        val sweep = withContext(Dispatchers.IO) { VciProtocolProbe.sweep(ctx, addr) }
        val lines = sweep.attempts.map { a ->
            val hdr = VciProtocolConfig.headerLabel(a.header)
            val mode = if (a.useHex) "hex" else "bin"
            "${if (a.success) "OK" else "—"} $hdr $mode — ${a.detail}"
        }
        setLog(lines.joinToString("\n"))
        val winner = sweep.winner
        if (winner != null) {
            VciProtocolConfig.persistToSettings(settings, winner.header, winner.useHex)
            setStatus(
                "Locked in ${VciProtocolConfig.headerLabel(winner.header)} " +
                    "${if (winner.useHex) "hex-ASCII" else "binary"}. Update docs/VCI-SPIKE-VERDICT.md on tablet proof.",
            )
        } else {
            setStatus("No combo returned parseable DTCs — try Frida capture or verify ignition/vehicle.")
        }
    } finally {
        setBusy(false)
    }
}

private suspend fun runSingleRead(
    ctx: android.content.Context,
    settings: com.caseforge.scanner.data.SettingsRepo,
    useHex: Boolean,
    setBusy: (Boolean) -> Unit,
    setStatus: (String) -> Unit,
    setLog: (String) -> Unit,
) {
    VciProtocolConfig.applyFromSettings(settings)
    val client = BluetoothVciClient(ctx, useHexEncoding = useHex)
    val communicator = VciCommunicator(client)
    setBusy(true)
    setLog("")
    try {
        val devices = withContext(Dispatchers.IO) { client.findBondedVciDevices() }
        if (devices.isEmpty()) {
            setStatus("No bonded VCI found.")
            return
        }
        val target = devices.first()
        setStatus("Connecting to ${target.name ?: target.address}…")
        val connect = withContext(Dispatchers.IO) { client.connect(target.address) }
        if (connect.isFailure) {
            setStatus("Connect failed: ${connect.exceptionOrNull()?.message}")
            return
        }
        setStatus("Connected (${VciProtocolConfig.headerLabel(VciProtocolConfig.header)}). Reading Mode 03…")
        val result = withContext(Dispatchers.IO) { communicator.readDtcs() }
        result.fold(
            onSuccess = { list ->
                if (list.isEmpty()) {
                    setStatus("Connected — empty DTC list (may be valid or wrong opcode/transport).")
                } else {
                    setStatus("Read ${list.size} DTC(s) without the OEM diagnostic app.")
                    setLog(list.joinToString("\n") { it.code })
                }
            },
            onFailure = { e -> setStatus("readDtcs failed: ${e.message}") },
        )
    } finally {
        withContext(Dispatchers.IO) { client.disconnect() }
        setBusy(false)
    }
}
