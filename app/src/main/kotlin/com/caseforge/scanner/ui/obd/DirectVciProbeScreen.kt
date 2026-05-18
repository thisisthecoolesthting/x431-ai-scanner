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
import com.caseforge.scanner.vci.VciCommunicator
import com.caseforge.scanner.vci.VciSocketClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Experimental Direct VCI probe (Phase 2 spike). Talks SPP to the Launch dongle without X431.
 */
@Composable
fun DirectVciProbeScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf("Pair the Launch VCI in Android Bluetooth settings, then connect.") }
    var busy by remember { mutableStateOf(false) }
    var dtcsText by remember { mutableStateOf("") }
    var useHex by remember { mutableStateOf(false) }

    val btLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        if (results.values.all { it }) {
            scope.launch { runProbe(ctx, useHex) { busy = it }, { status = it }, { dtcsText = it }) }
        } else {
            status = "Bluetooth permissions denied."
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Direct VCI (experimental)") },
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
            Text(
                "Bypasses X431. Header 0x55 0xAA and transport mode may need Frida capture on a live vehicle.",
                style = MaterialTheme.typography.bodySmall,
            )
            ListItem(
                headlineContent = { Text("Hex-ASCII transport (vs raw binary)") },
                trailingContent = {
                    Switch(checked = useHex, onCheckedChange = { useHex = it })
                },
            )
            Button(
                onClick = {
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
                    else {
                        scope.launch {
                            runProbe(ctx, useHex) { busy = it }, { status = it }, { dtcsText = it }
                        }
                    }
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (busy) "Working…" else "Connect bonded VCI and read DTCs")
            }
            Text(status, style = MaterialTheme.typography.bodyMedium)
            if (dtcsText.isNotBlank()) {
                Text(dtcsText, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private suspend fun runProbe(
    ctx: android.content.Context,
    useHex: Boolean,
    setBusy: (Boolean) -> Unit,
    setStatus: (String) -> Unit,
    setDtcs: (String) -> Unit,
) {
    val client = VciSocketClient(ctx, useHexEncoding = useHex)
    val communicator = VciCommunicator(client)
    setBusy(true)
    setDtcs("")
    try {
        val devices = withContext(Dispatchers.IO) { client.findBondedVciDevices() }
        if (devices.isEmpty()) {
            setStatus("No bonded VCI found. Pair the dongle in system Bluetooth settings.")
            return
        }
        val target = devices.first()
        setStatus("Connecting to ${target.name ?: target.address}…")
        val connect = withContext(Dispatchers.IO) { client.connect(target.address) }
        if (connect.isFailure) {
            setStatus("Connect failed: ${connect.exceptionOrNull()?.message}")
            return
        }
        setStatus("Connected. Requesting Mode 03 DTCs…")
        val result = withContext(Dispatchers.IO) { communicator.readDtcs() }
        result.fold(
            onSuccess = { list ->
                if (list.isEmpty()) {
                    setStatus("Connected. No DTCs returned (or opcode/transport mismatch — try hex toggle).")
                } else {
                    setStatus("Read ${list.size} DTC(s) without X431.")
                    setDtcs(list.joinToString("\n") { "${it.code}: ${it.description}" })
                }
            },
            onFailure = { e ->
                setStatus("readDtcs failed: ${e.message}")
            },
        )
    } finally {
        withContext(Dispatchers.IO) { client.disconnect() }
        setBusy(false)
    }
}
