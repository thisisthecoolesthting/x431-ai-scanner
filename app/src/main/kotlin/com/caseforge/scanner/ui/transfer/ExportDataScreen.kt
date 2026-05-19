@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.caseforge.scanner.ui.transfer

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.caseforge.scanner.R
import com.caseforge.scanner.agent.AgentActionLog
import com.caseforge.scanner.transfer.CnlaunchPathResolver
import com.caseforge.scanner.transfer.CnlaunchStorageAccess
import com.caseforge.scanner.transfer.CnlaunchZipper
import com.caseforge.scanner.transfer.LanExportConfig
import com.caseforge.scanner.transfer.LanFileServer
import com.caseforge.scanner.transfer.LanPushUploader
import com.caseforge.scanner.transfer.LanSelfTest
import com.caseforge.scanner.transfer.NetworkInterfaceHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ExportDataScreen(
    actionLog: AgentActionLog,
    onBack: () -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var inventory by remember { mutableStateOf(CnlaunchPathResolver.scan()) }
    val zipper = remember(inventory.root) { CnlaunchZipper(inventory.root) }

    fun refreshInventory() {
        inventory = CnlaunchPathResolver.scan()
    }

    LaunchedEffect(Unit) {
        refreshInventory()
    }

    val candidates = remember { NetworkInterfaceHelper.mergeCandidates(ctx) }
    var selectedHost by remember {
        mutableStateOf(
            NetworkInterfaceHelper.pickBest(candidates, NetworkInterfaceHelper.wifiIpv4FromConnectivity(ctx))
                ?.address,
        )
    }

    var serverRef by remember { mutableStateOf<LanFileServer?>(null) }
    var serverState by remember { mutableStateOf(LanFileServer.ServerState.STOPPED) }
    var serverError by remember { mutableStateOf<String?>(null) }
    var passCode by remember { mutableStateOf("") }
    var publicUrl by remember { mutableStateOf<String?>(null) }
    var actualPort by remember { mutableIntStateOf(8765) }
    var selfTestResult by remember { mutableStateOf<String?>(null) }
    var selfTestBusy by remember { mutableStateOf(false) }
    var startBusy by remember { mutableStateOf(false) }
    var pushBusy by remember { mutableStateOf(false) }
    var pushResult by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            serverRef?.stopServer()
            serverRef = null
        }
    }

    LaunchedEffect(serverRef) {
        val s = serverRef ?: return@LaunchedEffect
        launch {
            s.state.collectLatest { serverState = it }
        }
        launch {
            s.lastError.collectLatest { serverError = it }
        }
    }

    fun stopServer() {
        serverRef?.stopServer()
        serverRef = null
        publicUrl = null
        passCode = ""
        serverState = LanFileServer.ServerState.STOPPED
    }

    fun startServer() {
        error = null
        selfTestResult = null
        val host = selectedHost
        if (host.isNullOrBlank()) {
            error = ctx.getString(R.string.export_error_no_wifi)
            return
        }
        refreshInventory()
        if (!zipper.exists) {
            error = ctx.getString(R.string.export_error_no_cnlaunch)
            return
        }
        if (!zipper.hasExportableData) {
            error = CnlaunchZipper.EmptyCnlaunchException.emptyMessage(zipper.inventory)
            return
        }
        startBusy = true
        scope.launch {
            withContext(Dispatchers.IO) {
                serverRef?.stopServer()
                val code = LanFileServer.randomPassCode()
                LanFileServer.create(
                    context = ctx,
                    displayHost = host,
                    passCode = code,
                    zipper = CnlaunchZipper(inventory.root),
                    actionLog = actionLog,
                    scope = scope,
                    onAutoStop = {
                        scope.launch(Dispatchers.Main) {
                            serverRef = null
                            publicUrl = null
                            serverState = LanFileServer.ServerState.STOPPED
                        }
                    },
                )
            }.fold(
                onSuccess = { server ->
                    serverRef = server
                    passCode = server.passCode
                    publicUrl = server.publicUrl
                    actualPort = server.boundPort
                    serverState = LanFileServer.ServerState.LISTENING
                    startBusy = false
                },
                onFailure = { t ->
                    error = t.message ?: ctx.getString(R.string.export_error_start_failed)
                    serverState = LanFileServer.ServerState.ERROR
                    serverError = error
                    startBusy = false
                },
            )
        }
    }

    fun runSelfTest() {
        val port = serverRef?.boundPort ?: return
        val host = selectedHost ?: return
        selfTestBusy = true
        scope.launch {
            val r = LanSelfTest.healthCheck(host, port)
            selfTestResult = r.fold(
                onSuccess = { it },
                onFailure = { ctx.getString(R.string.export_self_test_fail, it.message ?: "failed") },
            )
            selfTestBusy = false
        }
    }

    fun pushToOfficePc() {
        error = null
        pushResult = null
        refreshInventory()
        if (!zipper.exists) {
            error = ctx.getString(R.string.export_error_no_cnlaunch)
            return
        }
        if (!zipper.hasExportableData) {
            error = CnlaunchZipper.EmptyCnlaunchException.emptyMessage(zipper.inventory)
            return
        }
        pushBusy = true
        scope.launch {
            val r = LanPushUploader.pushToOfficePc(ctx, CnlaunchZipper(inventory.root)) { msg ->
                pushResult = msg
            }
            pushResult = r.fold(
                onSuccess = { ctx.getString(R.string.export_push_ok, it) },
                onFailure = { ctx.getString(R.string.export_push_fail, it.message ?: "failed") },
            )
            pushBusy = false
        }
    }

    val qrBitmap = remember(publicUrl) { publicUrl?.let { QrCodeBitmap.encode(it) } }
    val listening = serverState == LanFileServer.ServerState.LISTENING
    val stateColor = when (serverState) {
        LanFileServer.ServerState.LISTENING -> Color(0xFF2E7D32)
        LanFileServer.ServerState.STARTING -> Color(0xFFF9A825)
        LanFileServer.ServerState.ERROR -> Color(0xFFC62828)
        else -> Color(0xFF757575)
    }
    val stateLabel = when (serverState) {
        LanFileServer.ServerState.LISTENING -> stringResource(
            R.string.export_state_listening,
            selectedHost ?: "?",
            actualPort,
        )
        LanFileServer.ServerState.STARTING -> stringResource(R.string.export_state_starting)
        LanFileServer.ServerState.ERROR -> stringResource(
            R.string.export_state_error,
            serverError ?: error ?: "?",
        )
        else -> stringResource(R.string.export_state_stopped)
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.export_screen_title)) },
            navigationIcon = {
                IconButton(onClick = {
                    stopServer()
                    onBack()
                }) {
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier
                        .size(12.dp)
                        .padding(end = 8.dp),
                    shape = MaterialTheme.shapes.small,
                    color = stateColor,
                ) {}
                Text(stateLabel, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            }

            Text(stringResource(R.string.export_screen_body), style = MaterialTheme.typography.bodyMedium)
            Text(
                stringResource(R.string.export_pc_host_label, LanExportConfig.RECEIVER_PC_HOST),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                stringResource(R.string.export_push_receiver_hint),
                style = MaterialTheme.typography.bodySmall,
            )
            Button(
                onClick = { pushToOfficePc() },
                enabled = !pushBusy && zipper.hasExportableData,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    if (pushBusy) stringResource(R.string.export_push_busy)
                    else stringResource(R.string.export_push_to_pc),
                )
            }
            pushResult?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
            }
            HorizontalDivider()
            Text(stringResource(R.string.export_firewall_hint), style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.export_ap_isolation_hint), style = MaterialTheme.typography.bodySmall)

            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            if (candidates.isEmpty()) {
                Text(
                    stringResource(R.string.export_error_no_wifi),
                    color = MaterialTheme.colorScheme.error,
                )
            } else {
                Text(stringResource(R.string.export_pick_ip), style = MaterialTheme.typography.titleSmall)
                candidates.forEach { c ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = selectedHost == c.address,
                                onClick = {
                                    selectedHost = c.address
                                    if (listening) {
                                        stopServer()
                                    }
                                },
                                role = Role.RadioButton,
                            )
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = selectedHost == c.address,
                            onClick = {
                                selectedHost = c.address
                                if (listening) stopServer()
                            },
                        )
                        Column(Modifier.padding(start = 8.dp)) {
                            Text(c.address, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold)
                            Text(
                                "${c.interfaceName} (${c.source})",
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                }
            }

            if (listening && publicUrl != null) {
                Text(
                    stringResource(R.string.export_pc_browser_hint),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(publicUrl!!, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold)
                Text(
                    "PC ${LanExportConfig.RECEIVER_PC_HOST} → paste tablet URL above (not the PC IP)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                qrBitmap?.let { bmp ->
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = stringResource(R.string.export_qr_content_description),
                        modifier = Modifier
                            .size(220.dp)
                            .align(Alignment.CenterHorizontally),
                    )
                }
                Text(
                    stringResource(R.string.export_pass_code_label, passCode),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                OutlinedButton(
                    onClick = { runSelfTest() },
                    enabled = !selfTestBusy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (selfTestBusy) "Testing…" else stringResource(R.string.export_self_test))
                }
                selfTestResult?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                }
                OutlinedButton(onClick = { stopServer() }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.export_stop_server))
                }
            } else {
                Button(
                    onClick = { startServer() },
                    enabled = !startBusy && selectedHost != null && zipper.hasExportableData,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        if (startBusy) stringResource(R.string.export_state_starting)
                        else stringResource(R.string.export_start_server),
                    )
                }
            }
        }
    }
}
