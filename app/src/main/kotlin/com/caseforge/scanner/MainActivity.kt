@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.caseforge.scanner

import android.Manifest
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import com.caseforge.scanner.agent.ObdBluetoothTool
import com.caseforge.scanner.agent.ObdUsbTool
import com.caseforge.scanner.vci.DiagnosticConnector
import com.caseforge.scanner.vci.VciUsbAttachState
import com.caseforge.scanner.vci.transport.UsbSerialTransport
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.caseforge.scanner.BuildConfig
import com.caseforge.scanner.agent.AgentRunner
import com.caseforge.scanner.agent.AgentStatus
import com.caseforge.scanner.agent.Updater
import com.caseforge.scanner.agent.ScannerAccessibilityService
import com.caseforge.scanner.ai.ClaudeClient
import com.caseforge.scanner.ai.Prompts
import com.caseforge.scanner.data.DtcEntity
import com.caseforge.scanner.data.SessionEntity
import com.caseforge.scanner.engine.CapabilityMap
import com.caseforge.scanner.engine.ScreenKind
import com.caseforge.scanner.overlay.ScreenCaptureService
import com.caseforge.scanner.overlay.compose.screens.ActuationScreen
import com.caseforge.scanner.overlay.compose.screens.LiveDataScreen
import com.caseforge.scanner.overlay.compose.screens.ModuleListScreen
import com.caseforge.scanner.overlay.compose.screens.ReportScreen
import com.caseforge.scanner.ui.history.HistoryScreen
import com.caseforge.scanner.ui.log.ActionLogScreen
import com.caseforge.scanner.ui.main.MainScreen
import com.caseforge.scanner.ui.main.RecallsScreen
import com.caseforge.scanner.ui.main.StandaloneVciController
import com.caseforge.scanner.ui.notes.AgentNotesScreen
import com.caseforge.scanner.ui.settings.SettingsScreen
import com.caseforge.scanner.ui.theme.TogetherCarWorksTheme
import com.caseforge.scanner.ui.triage.TriageScreen
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    companion object {
        const val EXTRA_REPORT_TEXT = "report_text"
        const val EXTRA_REPORT_SOURCE = "report_source"
    }

    private val app: App by lazy { application as App }
    private var latestDetectedVin: String? = null

    private val mediaProjectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val svc = Intent(this, ScreenCaptureService::class.java).apply {
                putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, result.resultCode)
                putExtra(ScreenCaptureService.EXTRA_RESULT_DATA, result.data)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(svc)
            else startService(svc)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleUsbIntent(intent)

        val sharedReport = intent.getStringExtra(EXTRA_REPORT_TEXT)

        ScannerAccessibilityService.onVinDetected = { vin ->
            latestDetectedVin = vin
            if (app.settings.autoStartOnVin && !app.settings.killSwitch) {
                lifecycleScope.launch { runAgent(vin = vin, symptom = null) }
            }
        }

        setContent {
            TogetherCarWorksTheme(mode = app.settings.themeMode) {
                val vci = remember { StandaloneVciController(this@MainActivity, app.settings) }
                val engineState by vci.engineState
                val context = LocalContext.current
                fun transportNeedsBluetooth(): Boolean {
                    val mode = DiagnosticConnector.userTransportFrom(app.settings)
                    return when (mode) {
                        DiagnosticConnector.UserTransport.OEM_BT,
                        DiagnosticConnector.UserTransport.ELM327_BT,
                        -> true
                        DiagnosticConnector.UserTransport.AUTO ->
                            app.settings.bluetoothTransportEnabled
                        else -> false
                    }
                }

                fun startConnect() {
                    lifecycleScope.launch {
                        AgentStatus.setActivity("Connecting…")
                        vci.connect()
                    }
                }

                val btPermissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions(),
                ) { results ->
                    if (results[Manifest.permission.BLUETOOTH_CONNECT] == true) {
                        startConnect()
                    }
                }

                fun requestConnect() {
                    if (!transportNeedsBluetooth()) {
                        startConnect()
                        return
                    }
                    val perms = buildList {
                        add(Manifest.permission.BLUETOOTH_CONNECT)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            add(Manifest.permission.BLUETOOTH_SCAN)
                        }
                    }
                    val missing = perms.any {
                        ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
                    }
                    if (missing) {
                        btPermissionLauncher.launch(perms.toTypedArray())
                    } else {
                        startConnect()
                    }
                }

                var usbCount by remember { mutableStateOf(ObdUsbTool(context).listDevices().size) }
                var selectedTransport by remember {
                    mutableStateOf(DiagnosticConnector.userTransportFrom(app.settings))
                }
                var btEnabled by remember { mutableStateOf(app.settings.bluetoothTransportEnabled) }

                val initialRoute = if (sharedReport != null) "triage" else "main"
                var route by remember { mutableStateOf(initialRoute) }
                var triageInput by remember { mutableStateOf(sharedReport.orEmpty()) }
                var triageOutput by remember { mutableStateOf("") }
                var busy by remember { mutableStateOf(false) }

                Box(Modifier.fillMaxSize()) {
                    when (route) {
                        "main" -> MainScreen(
                            vciConnected = vci.isConnected,
                            vin = engineState.vehicleVin,
                            linkDetail = vci.linkKind()?.name?.replace('_', ' '),
                            engineBusy = engineState.busy,
                            engineState = engineState,
                            settings = app.settings,
                            usbDeviceCount = usbCount,
                            selectedTransport = selectedTransport,
                            onTransportSelected = { t ->
                                selectedTransport = t
                                app.settings.linkTransport = when (t) {
                                    DiagnosticConnector.UserTransport.AUTO -> "auto"
                                    DiagnosticConnector.UserTransport.ELM327_USB -> "elm327_usb"
                                    DiagnosticConnector.UserTransport.OEM_USB -> "oem_usb"
                                    DiagnosticConnector.UserTransport.OEM_BT -> "oem_bt"
                                    DiagnosticConnector.UserTransport.ELM327_BT -> "elm327_bt"
                                }
                            },
                            bluetoothTransportEnabled = btEnabled,
                            onBluetoothTransportToggle = { on ->
                                btEnabled = on
                                app.settings.bluetoothTransportEnabled = on
                            },
                            onOpenBluetoothSettings = {
                                startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                            },
                            bondedObdDevices = ObdBluetoothTool.listBondedObdDevices(),
                            selectedBtAddress = app.settings.vciSelectedBtAddress,
                            onSelectBtDevice = { app.settings.vciSelectedBtAddress = it },
                            onConnectClick = {
                                usbCount = ObdUsbTool(context).listDevices().size
                                requestConnect()
                            },
                            onDisconnect = { vci.disconnect() },
                            onScan = {
                                vci.runFullScan(lifecycleScope) { ok ->
                                    if (ok) route = "report"
                                }
                            },
                            onLiveData = {
                                vci.startLiveData(lifecycleScope)
                                route = "live_data"
                            },
                            onService = { route = "service" },
                            onBidirectional = { route = "bidirectional" },
                            onRecalls = { route = "recalls" },
                            onHistory = { route = "history" },
                            onNotes = { route = "notes" },
                            onSettings = { route = "settings" },
                            onDiagnostics = { route = "vci_diagnostics" },
                            onCheckUpdate = { checkForAppUpdate() },
                            buildInfo = BuildConfig.BUILD_INFO,
                            onAiPrompt = { symptom ->
                                lifecycleScope.launch {
                                    runStandaloneAgent(
                                        vin = engineState.vehicleVin,
                                        symptom = symptom,
                                        dtcs = engineState.dtcs,
                                    )
                                }
                            },
                        )
                        "report" -> SubScreenScaffold(
                            title = "Scan results",
                            onBack = { route = "main" },
                        ) {
                            ReportScreen(state = engineState, onAction = {})
                        }
                        "live_data" -> SubScreenScaffold(
                            title = "Live data",
                            onBack = {
                                vci.stopLiveData()
                                route = "main"
                            },
                        ) {
                            LiveDataScreen(state = engineState, onAction = {})
                        }
                        "service" -> SubScreenScaffold(
                            title = "Service",
                            onBack = { route = "main" },
                        ) {
                            ModuleListScreen(
                                state = engineState.copy(screen = ScreenKind.HomeMenu),
                                onAction = {},
                                initialCategory = CapabilityMap.Category.Service,
                            )
                        }
                        "bidirectional" -> SubScreenScaffold(
                            title = "Bidirectional",
                            onBack = { route = "main" },
                        ) {
                            ActuationScreen(
                                state = engineState.copy(screen = ScreenKind.ActuationTest),
                                onAction = {},
                            )
                        }
                        "recalls" -> RecallsScreen(
                            vin = engineState.vehicleVin,
                            onBack = { route = "main" },
                        )
                        "settings" -> SettingsScreen(
                            settings = app.settings,
                            onBack = { route = "main" },
                            onOpenDataExport = { route = "export_data" },
                            onOpenDirectVciProbe = { route = "direct_vci" },
                            onOpenVciDiagnostics = { route = "vci_diagnostics" },
                            onCheckUpdate = { checkForAppUpdate() },
                        )
                        "vci_diagnostics" -> com.caseforge.scanner.ui.diag.VciDiagnosticsScreen(
                            onBack = { route = "settings" },
                        )
                        "export_data" -> com.caseforge.scanner.ui.transfer.ExportDataScreen(
                            settings = app.settings,
                            onBack = { route = "main" },
                            onOpenTransferLog = { route = "transfer_log" },
                        )
                        "transfer_log" -> com.caseforge.scanner.ui.transfer.TransferLogScreen(
                            onBack = { route = "export_data" },
                        )
                        "direct_vci" -> com.caseforge.scanner.ui.obd.DirectVciProbeScreen(
                            onBack = { route = "settings" },
                        )
                        "history" -> HistoryScreen(db = app.db, onBack = { route = "main" })
                        "log" -> ActionLogScreen(actionLog = app.actionLog, onBack = { route = "main" })
                        "notes" -> AgentNotesScreen(settings = app.settings, onBack = { route = "main" })
                        "triage" -> TriageScreen(
                            initialText = triageInput,
                            output = triageOutput,
                            busy = busy,
                            onRun = { text ->
                                busy = true
                                lifecycleScope.launch {
                                    val out = runReportTriage(text)
                                    triageOutput = out
                                    busy = false
                                }
                            },
                            onBack = { route = "main" },
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleUsbIntent(intent)
    }

    private fun handleUsbIntent(intent: Intent?) {
        if (intent == null) return
        when (intent.action) {
            UsbManager.ACTION_USB_DEVICE_ATTACHED,
            UsbSerialTransport.ACTION_USB_PERMISSION -> {
                val device: UsbDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                }
                if (device != null) {
                    VciUsbAttachState.pendingDevice = device
                    app.settings.directVciExperimental = true
                    val usb = ObdUsbTool(this)
                    if (!usb.hasPermission(device)) {
                        usb.requestPermission(device)
                    } else {
                        toast("USB OBD cable detected — tap Connect")
                    }
                }
            }
        }
    }

    private fun checkForAppUpdate() {
        if (Updater.needsInstallPermission(this)) {
            toast("Allow Install unknown apps for Together, then try again.")
            Updater.openInstallPermissionSettings(this)
            return
        }
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                AgentStatus.setActivity("Checking for update…")
                val info = Updater.checkLatest()
                if (Updater.isNewer(info)) {
                    AgentStatus.setActivity("New build ${info.sha} — downloading…")
                    Updater.downloadAndInstall(applicationContext) { msg ->
                        AgentStatus.setActivity(msg)
                    }
                } else {
                    val msg = "Already on latest (${info.sha})"
                    AgentStatus.setActivity(msg)
                    toast(msg)
                }
            } catch (t: Throwable) {
                val msg = when (t) {
                    is Updater.UpdateException -> t.message
                    else -> t.message
                }?.take(200) ?: t.javaClass.simpleName
                AgentStatus.setActivity("Update: $msg")
                toast("Update: $msg")
            }
        }
    }

    private suspend fun runStandaloneAgent(
        vin: String?,
        symptom: String?,
        dtcs: List<com.caseforge.scanner.engine.ScrapedDtc>,
    ) {
        val key = app.settings.claudeApiKey
        if (key.isBlank()) {
            toast("Set a Claude API key in Settings first.")
            return
        }
        AgentStatus.setActivity("Asking Together…")
        val userText = buildString {
            if (!vin.isNullOrBlank()) appendLine("VIN: $vin")
            if (dtcs.isNotEmpty()) {
                appendLine("DTCs from last scan:")
                dtcs.forEach { d -> appendLine("  ${d.code} ${d.module.orEmpty()} ${d.description.orEmpty()}") }
            }
            appendLine(
                symptom?.ifBlank { null } ?: "What should I check next on this vehicle?",
            )
        }
        val reply = withContext(Dispatchers.IO) {
            try {
                val client = ClaudeClient(apiKey = key, model = app.settings.model)
                val resp = client.sendMessages(
                    system = Prompts.DTC_TRIAGE_FROM_REPORT,
                    messages = listOf(ClaudeClient.userText(userText)),
                    maxTokens = 2048,
                )
                resp.firstText().orEmpty()
            } catch (t: Throwable) {
                "Error: ${t.message}"
            }
        }
        AgentStatus.setActivity(reply.take(220))
        toast(reply.take(120).ifBlank { "Together replied — see ticker." })
    }

    private suspend fun runReportTriage(reportText: String): String {
        val key = app.settings.claudeApiKey
        if (key.isBlank()) return "Set a Claude API key in Settings first."
        return withContext(Dispatchers.IO) {
            try {
                val client = ClaudeClient(apiKey = key, model = app.settings.model)
                val resp = client.sendMessages(
                    system = Prompts.DTC_TRIAGE_FROM_REPORT,
                    messages = listOf(ClaudeClient.userText(reportText)),
                    maxTokens = 2048,
                )
                resp.firstText().orEmpty()
            } catch (t: Throwable) {
                "Error: ${t.message}"
            }
        }
    }

    private suspend fun runAgent(vin: String?, symptom: String?) {
        runAgentSession(vin = vin, symptom = symptom, scope = "diagnostic")
    }

    private suspend fun runFullScan(vin: String?) {
        runAgentSession(vin = vin, symptom = Prompts.FULL_SCAN_SENTINEL, scope = "fullscan")
    }

    private suspend fun runAgentSession(vin: String?, symptom: String?, scope: String) {
        val key = app.settings.claudeApiKey
        if (key.isBlank()) { toast("Set a Claude API key in Settings first."); return }
        if (app.settings.killSwitch) { toast("Kill switch is on — disable in Settings."); return }
        if (ScannerAccessibilityService.instance() == null) {
            toast("Enable the Together Car Works accessibility service first.")
            app.actionLog.event("session.aborted", "a11y service not running")
            return
        }
        try {
            withContext(Dispatchers.IO) {
                val client = ClaudeClient(apiKey = key, model = app.settings.model)
                val runner = AgentRunner(
                    context = applicationContext,
                    claude = client,
                    log = app.actionLog,
                    screenshot = {
                        val base64 = ScreenCaptureService.captureJpegBase64()
                        if (base64 != null) AgentRunner.ImagePayload("image/jpeg", base64) else null
                    },
                    requireApproval = app.settings.requireApproval,
                    agentNotes = app.settings.agentNotes,
                )
                val started = System.currentTimeMillis()
                val outcome = runner.run(vin = vin, symptom = symptom)
                runCatching {
                    val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
                    val summary = outcome.summary
                    val symptomToPersist = if (symptom == Prompts.FULL_SCAN_SENTINEL) null else symptom
                    val geo = com.caseforge.scanner.location.SessionLocationCapture.capture(applicationContext)
                    val sessionId = app.db.sessionDao().insertSession(
                        SessionEntity(
                            vin = vin,
                            startedAt = started,
                            endedAt = System.currentTimeMillis(),
                            symptom = symptomToPersist,
                            rootCause = jsonStringOrNull(summary, "root_cause"),
                            recommendedRepair = jsonStringOrNull(summary, "recommended_repair"),
                            transcriptJson = json.encodeToString(
                                kotlinx.serialization.builtins.ListSerializer(ClaudeClient.Message.serializer()),
                                outcome.transcript
                            ),
                            scope = scope,
                            latitude = geo.latitude,
                            longitude = geo.longitude,
                        )
                    )
                    extractDtcs(summary).forEach { dtc ->
                        app.db.sessionDao().insertDtc(dtc.copy(sessionId = sessionId))
                    }
                    app.actionLog.event("session.persisted", "id=$sessionId scope=$scope reason=${outcome.stoppedReason}")
                }.onFailure { app.actionLog.event("session.persist_error", it.message.orEmpty()) }

                lifecycleScope.launch(Dispatchers.Main) {
                    toast(
                        if (outcome.finished) "Agent finished — see History."
                        else "Agent stopped: ${outcome.stoppedReason.take(220)}"
                    )
                }
            }
        } catch (t: Throwable) {
            app.actionLog.event("session.error", t.message.orEmpty())
            com.caseforge.scanner.agent.AgentStatus.setActivity("Agent error: ${t.message?.take(220) ?: t.javaClass.simpleName}")
            lifecycleScope.launch(Dispatchers.Main) {
                toast("Agent error: ${t.message?.take(100) ?: t.javaClass.simpleName}")
            }
        }
    }

    private fun toast(msg: String) {
        runOnUiThread { Toast.makeText(this, msg, Toast.LENGTH_LONG).show() }
    }

    private fun jsonStringOrNull(obj: JsonObject?, key: String): String? {
        val el = obj?.get(key) ?: return null
        val s = (el as? JsonPrimitive)?.contentOrNullSafe ?: return null
        return s.ifBlank { null }
    }

    private fun extractDtcs(summary: JsonObject?): List<DtcEntity> {
        val arr = (summary?.get("dtcs_found") as? JsonArray) ?: return emptyList()
        return arr.mapNotNull { el ->
            val obj = (el as? JsonObject) ?: return@mapNotNull null
            val code = (obj["code"] as? JsonPrimitive)?.contentOrNullSafe?.takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            DtcEntity(
                sessionId = 0L,
                code = code,
                module = (obj["module"] as? JsonPrimitive)?.contentOrNullSafe,
                description = (obj["description"] as? JsonPrimitive)?.contentOrNullSafe,
                status = (obj["status"] as? JsonPrimitive)?.contentOrNullSafe,
            )
        }
    }
}

@Composable
private fun SubScreenScaffold(
    title: String,
    onBack: () -> Unit,
    content: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(title) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            },
        )
        Box(Modifier.weight(1f).fillMaxWidth()) {
            content()
        }
    }
}

private val JsonPrimitive.contentOrNullSafe: String?
    get() = try { if (this is kotlinx.serialization.json.JsonNull) null else content } catch (_: Throwable) { null }
