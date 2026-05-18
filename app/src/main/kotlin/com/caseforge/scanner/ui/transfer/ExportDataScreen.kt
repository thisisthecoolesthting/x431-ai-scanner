@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.caseforge.scanner.ui.transfer

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.caseforge.scanner.R
import com.caseforge.scanner.agent.AgentActionLog
import com.caseforge.scanner.transfer.CnlaunchZipper
import com.caseforge.scanner.transfer.LanFileServer
import com.caseforge.scanner.transfer.LanNetwork
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ExportDataScreen(
    actionLog: AgentActionLog,
    onBack: () -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var running by remember { mutableStateOf(false) }
    var passCode by remember { mutableStateOf("") }
    var url by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var serverRef by remember { mutableStateOf<LanFileServer?>(null) }
    val zipper = remember { CnlaunchZipper() }

    DisposableEffect(Unit) {
        onDispose {
            serverRef?.stopServer()
            serverRef = null
        }
    }

    fun stopServer() {
        serverRef?.stopServer()
        serverRef = null
        running = false
        status = ctx.getString(R.string.export_status_stopped)
    }

    fun startServer() {
        error = null
        val host = LanNetwork.wifiIpv4OrNull(ctx)
        if (host == null) {
            error = ctx.getString(R.string.export_error_no_wifi)
            return
        }
        if (!zipper.exists) {
            error = ctx.getString(R.string.export_error_no_cnlaunch)
            return
        }
        scope.launch {
            withContext(Dispatchers.IO) {
                runCatching {
                    serverRef?.stopServer()
                    val code = LanFileServer.randomPassCode()
                    val server = LanFileServer(
                        bindHost = host,
                        port = LanNetwork.DEFAULT_PORT,
                        passCode = code,
                        zipper = zipper,
                        actionLog = actionLog,
                        scope = scope,
                        onAutoStop = {
                            scope.launch(Dispatchers.Main) {
                                running = false
                                status = ctx.getString(R.string.export_status_auto_stopped)
                                serverRef = null
                            }
                        },
                    )
                    server.startServer()
                    server
                }.fold(
                    onSuccess = { server ->
                        withContext(Dispatchers.Main) {
                            serverRef = server
                            passCode = server.passCode
                            url = server.url
                            running = true
                            status = ctx.getString(R.string.export_status_running)
                        }
                    },
                    onFailure = { t ->
                        withContext(Dispatchers.Main) {
                            error = t.message ?: ctx.getString(R.string.export_error_start_failed)
                            running = false
                        }
                    },
                )
            }
        }
    }

    val qrBitmap = remember(url) { url?.let { QrCodeBitmap.encode(it) } }

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
            Text(
                stringResource(R.string.export_screen_body),
                style = MaterialTheme.typography.bodyMedium,
            )
            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            if (running && url != null) {
                Text(
                    url!!,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
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
                Text(status, style = MaterialTheme.typography.bodySmall)
                OutlinedButton(
                    onClick = { stopServer() },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.export_stop_server))
                }
            } else {
                Button(
                    onClick = { startServer() },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.export_start_server))
                }
            }
        }
    }
}
