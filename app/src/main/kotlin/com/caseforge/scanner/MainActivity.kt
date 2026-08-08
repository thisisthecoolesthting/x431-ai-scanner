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
import com.caseforge.scanner.vci.OemUsbVciClient
import com.caseforge.scanner.vci.VciUsbAttachState
import com.caseforge.scanner.vci.transport.UsbSerialTransport
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
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
import com.caseforge.scanner.update.LiveUpdateCoordinator
import com.caseforge.scanner.transfer.HarvestUploadCoordinator
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
import com.caseforge.scanner.ui.main.AiCopilotHomeScreen
import com.caseforge.scanner.ui.main.CopilotAction
import com.caseforge.scanner.ui.main.MainScreen
import com.caseforge.scanner.ui.setup.SetupCompactHomeScreen
import com.caseforge.scanner.ui.setup.SetupLiveWizardScreen
import com.caseforge.scanner.ui.setup.SetupProgressStore
import com.caseforge.scanner.ui.main.RecallsScreen
import com.caseforge.scanner.ui.session.ActiveCustomerSession
import com.caseforge.scanner.ui.session.SessionChatScreen
import com.caseforge.scanner.ui.session.SessionRoutes
import com.caseforge.scanner.ui.session.SessionWizardNav
import com.caseforge.scanner.data.session.CustomerSessionRepository
import com.caseforge.scanner.data.SettingsRepo
import com.caseforge.scanner.ui.main.StandaloneVciController
import com.caseforge.scanner.ui.notes.AgentNotesScreen
import com.caseforge.scanner.ui.security.SecurityFunctionsScreen
import com.caseforge.scanner.ui.settings.CapabilitiesBrowseScreen
import com.caseforge.scanner.ui.settings.SettingsScreen
import com.caseforge.scanner.ui.obd.ObdScanScreen
import com.caseforge.scanner.ui.planb.BodyReadScreen
import com.caseforge.scanner.ui.planb.CodingChecklistScreen
import com.caseforge.scanner.ui.planb.ImmoInfoScreen
import com.caseforge.scanner.ui.planb.ProgrammingScreen
import com.caseforge.scanner.ui.theme.TogetherCarWorksTheme
import com.caseforge.scanner.planb.PlanbMarque
import com.caseforge.scanner.planb.immo.SkreemModule
import com.caseforge.scanner.ui.skreem.SkreemTrialGateScreen
import com.caseforge.scanner.ui.tier4.Tier4TrialGateScreen
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
    private var pendingUsbDevice: UsbDevice? = null
    private var usbPermissionRetry: (() -> Unit)? = null
    @Volatile
    private var connectInFlight: Boolean = false

    private val usbPermissionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent == null) return
            val action = intent.action ?: return
            if (
                action != OemUsbVciClient.ACTION_USB_PERMISSION &&
                action != UsbSerialTransport.ACTION_USB_PERMISSION
            ) {
                return
            }
            val attachedDevice = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
            } ?: return
            if (!intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) return
            VciUsbAttachState.signalPermissionGranted(attachedDevice)
            VciUsbAttachState.pendingDevice = attachedDevice
            pendingUsbDevice = attachedDevice
            lifecycleScope.launch(Dispatchers.IO) {
                runCatching { DiagnosticConnector.detectUsbKind(this@MainActivity, attachedDevice) }
                withContext(Dispatchers.Main) {
                    toast("USB permission granted — connecting…")
                    if (!connectInFlight) {
                        usbPermissionRetry?.invoke()
                    }
                }
            }
        }
    }

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
            if (
                app.settings.autoStartOnVin &&
                !app.settings.killSwitch &&
                ScannerAccessibilityService.instance() != null
            ) {
                lifecycleScope.launch { runAgent(vin = vin, symptom = null) }
            }
        }

        setContent {
            TogetherCarWorksTheme(mode = app.settings.themeMode) {
                val vci = remember { StandaloneVciController(this@MainActivity, app.settings) }
                DisposableEffect(vci) {
                    usbPermissionRetry = {
                        lifecycleScope.launch {
                            AgentStatus.setActivity("Connecting…")
                            vci.connect()
                        }
                    }
                    onDispose { usbPermissionRetry = null }
                }
                val engineState by vci.engineState
                val context = LocalContext.current
                var usbCount by remember { mutableStateOf(ObdUsbTool(context).listDevices().size) }
                var selectedTransport by remember {
                    mutableStateOf(DiagnosticConnector.userTransportFrom(app.settings))
                }
                var btEnabled by remember { mutableStateOf(app.settings.bluetoothTransportEnabled) }
                var connectBusy by remember { mutableStateOf(false) }

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
                    if (connectBusy || vci.isConnected) return
                    connectBusy = true
                    connectInFlight = true
                    lifecycleScope.launch {
                        try {
                            AgentStatus.setActivity("Connecting…")
                            usbCount = ObdUsbTool(context).listDevices().size
                            vci.connect()
                        } catch (t: Throwable) {
                            toast(
                                "Connect error: ${t.message?.take(120) ?: t.javaClass.simpleName}",
                            )
                        } finally {
                            connectBusy = false
                            connectInFlight = false
                        }
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

                val setupProgress = remember { SetupProgressStore(context.applicationContext, app.settings) }
                var setupLiveComplete by remember { mutableStateOf(app.settings.setupLiveComplete) }
                val initialRoute = if (sharedReport != null) "triage" else "main"
                var route by remember { mutableStateOf(initialRoute) }
                var triageInput by remember { mutableStateOf(sharedReport.orEmpty()) }
                var triageOutput by remember { mutableStateOf("") }
                var busy by remember { mutableStateOf(false) }
                var homeMode by remember { mutableStateOf(app.settings.homeMode) }
                var pendingCopilotSymptom by remember { mutableStateOf<String?>(null) }
                var planBSyncTick by remember { mutableIntStateOf(0) }
                var planBSyncInFlight by remember { mutableStateOf(false) }
                var activeCustomerSession by remember { mutableStateOf<ActiveCustomerSession?>(null) }
                var newSessionId by remember { mutableStateOf<String?>(null) }
                val sessionRepo = remember { CustomerSessionRepository(context) }

                fun runScanFromHome() {
                    vci.runFullScan(lifecycleScope) { ok ->
                        if (ok) route = "report"
                    }
                }

                fun runWithConnection(block: () -> Unit) {
                    if (vci.isConnected) {
                        block()
                    } else {
                        usbCount = ObdUsbTool(context).listDevices().size
                        requestConnect()
                        toast("Connect OBD first, then try again.")
                    }
                }

                fun handleCopilotAction(action: CopilotAction) {
                    when (action) {
                        CopilotAction.ConnectUsbObd -> {
                            usbCount = ObdUsbTool(context).listDevices().size
                            requestConnect()
                        }
                        CopilotAction.ScanVehicle,
                        CopilotAction.RunObdScan,
                        -> runWithConnection { runScanFromHome() }
                        CopilotAction.StartLiveData,
                        CopilotAction.OpenLiveData,
                        -> runWithConnection {
                            vci.startLiveData(lifecycleScope)
                            route = "live_data"
                        }
                        CopilotAction.CheckRecalls -> route = "recalls"
                        CopilotAction.SendDataToPc -> route = "export_data"
                        CopilotAction.OpenHistory -> route = "history"
                        CopilotAction.OpenSettings -> route = "settings"
                        CopilotAction.OpenDiagnostics -> route = "vci_diagnostics"
                        CopilotAction.OpenScannerConsole -> {
                            app.settings.homeMode = SettingsRepo.HOME_SCANNER_CONSOLE
                            homeMode = SettingsRepo.HOME_SCANNER_CONSOLE
                        }
                        CopilotAction.ClearCodes -> {
                            toast("Clear codes: open Service and confirm there.")
                            route = "service"
                        }
                        CopilotAction.ShareReport -> {
                            if (engineState.dtcs.isEmpty()) {
                                toast("Run a scan first to share a report.")
                            } else {
                                route = "report"
                            }
                        }
                        CopilotAction.ExplainCurrentCodes,
                        CopilotAction.GenerateRepairStory,
                        CopilotAction.BuildCustomerReport,
                        -> {
                            val symptom = pendingCopilotSymptom
                                ?: when (action) {
                                    CopilotAction.ExplainCurrentCodes -> "Explain current DTCs in plain English."
                                    CopilotAction.GenerateRepairStory,
                                    CopilotAction.BuildCustomerReport,
                                    -> "Build a customer-facing repair story from the last scan."
                                    else -> null
                                }
                            lifecycleScope.launch {
                                runStandaloneAgent(
                                    vin = engineState.vehicleVin,
                                    symptom = symptom,
                                    dtcs = engineState.dtcs,
                                )
                            }
                        }
                        is CopilotAction.SubmitSymptom -> {
                            pendingCopilotSymptom = action.text.trim().ifBlank { null }
                        }
                    }
                }

                Box(Modifier.fillMaxSize()) {
                    when (route) {
                        "main" -> when {
                            !setupLiveComplete -> SetupLiveWizardScreen(
                                settings = app.settings,
                                progress = setupProgress,
                                buildInfo = BuildConfig.BUILD_INFO,
                                onSettings = { route = "settings" },
                                onSetupComplete = {
                                    setupLiveComplete = true
                                }
                            )
                            setupLiveComplete -> SetupCompactHomeScreen(
                                buildInfo = BuildConfig.BUILD_INFO,
                                passedSteps = setupProgress.passedOrSkippedCount(),
                                totalSteps = setupProgress.steps.size,
                                tier4TrialActive = app.settings.tier4TrialActive,
                                tier4ProgrammingReady = app.settings.canAccessTier4Programming(),
                                vciConnected = vci.isConnected,
                                vin = engineState.vehicleVin,
                                linkDetail = vci.linkKind()?.name?.replace('_', ' '),
                                connectError = engineState.errorBanner,
                                connectBusy = connectBusy,
                                usbDeviceCount = usbCount,
                                lastWorkingConnectAtMs = app.settings.lastWorkingConnectAtMs,
                                onConnect = { requestConnect() },
                                onDisconnect = { vci.disconnect() },
                                onNewSession = {
                                    newSessionId = sessionRepo.newSessionId()
                                    route = SessionRoutes.WIZARD
                                },
                                onSettings = { route = "settings" },
                                onOpenConnectionDiagnostics = { route = "vci_diagnostics" },
                                onRerunSetup = {
                                    setupProgress.resetAll()
                                    setupLiveComplete = false
                                },
                                onOpenTier4Programming = { route = "planb_programming" },
                                onOpenTier4Gate = { route = "tier4_trial_gate" },
                            )
                        }
                        "classic_home" -> if (homeMode == SettingsRepo.HOME_AI_COPILOT) {
                            AiCopilotHomeScreen(
                                vciConnected = vci.isConnected,
                                vin = engineState.vehicleVin,
                                engineBusy = engineState.busy,
                                engineState = engineState,
                                buildInfo = BuildConfig.BUILD_INFO,
                                onCopilotAction = { handleCopilotAction(it) },
                                onCheckUpdate = { checkForAppUpdate() },
                            )
                        } else MainScreen(
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
                            onSecurity = { route = "security" },
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
                            onOpenObdScan = { route = "obd_scan" },
                            onNewSession = {
                                newSessionId = sessionRepo.newSessionId()
                                route = SessionRoutes.WIZARD
                            },
                            onOpenDataExport = { route = "export_data" },
                        )
                        SessionRoutes.WIZARD -> {
                            BackHandler { route = "main" }
                            val sid = newSessionId ?: sessionRepo.newSessionId().also { newSessionId = it }
                            SessionWizardNav(
                                sessionId = sid,
                                onComplete = { completed ->
                                    activeCustomerSession = completed
                                    route = SessionRoutes.CHAT
                                },
                            )
                        }
                        SessionRoutes.CHAT -> {
                            BackHandler { route = "main" }
                            val active = activeCustomerSession
                            if (active == null) {
                                RouteGuardRedirect { route = "main" }
                            } else {
                                SessionChatScreen(
                                    session = active,
                                    settings = app.settings,
                                    onBack = { route = "main" },
                                )
                            }
                        }
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
                        "security" -> SecurityFunctionsScreen(
                            vin = engineState.vehicleVin,
                            batteryVoltage = null,
                            onBack = { route = "main" },
                            onOpenOemApp = {
                                if (!openOemDiagnosticApp()) {
                                    toast("OEM diagnostic app not found on this tablet")
                                }
                            },
                            onOpenDiagnostics = { route = "vci_diagnostics" },
                        )
                        "settings" -> SettingsScreen(
                            settings = app.settings,
                            vehicleVin = engineState.vehicleVin,
                            onBack = {
                                homeMode = app.settings.homeMode
                                setupLiveComplete = app.settings.setupLiveComplete
                                route = "main"
                            },
                            onOpenClassicHome = { route = "classic_home" },
                            onRerunSetupLive = {
                                setupProgress.resetAll()
                                setupLiveComplete = false
                                route = "main"
                            },
                            onOpenDataExport = { route = "export_data" },
                            onOpenDirectVciProbe = { route = "direct_vci" },
                            onOpenObdScan = { route = "obd_scan" },
                            onOpenPlanbBody = { route = "planb_body" },
                            onOpenPlanbCoding = { route = "planb_coding" },
                            onOpenPlanbImmo = { route = "planb_immo" },
                            onOpenPlanbProgramming = { route = "planb_programming" },
                            onOpenCapabilitiesBrowse = { route = "capabilities_browse" },
                            onOpenVciDiagnostics = { route = "vci_diagnostics" },
                            onOpenRecallsTsbStub = { route = "recalls" },
                            onCheckUpdate = { checkForAppUpdate() },
                            onSyncPlanBData = {
                                planBSyncInFlight = true
                                syncPlanBData(
                                    onSuccess = { planBSyncTick++ },
                                    onFinished = { planBSyncInFlight = false },
                                )
                            },
                            planBSyncTick = planBSyncTick,
                            planBSyncInFlight = planBSyncInFlight,
                        )
                        "capabilities_browse" -> CapabilitiesBrowseScreen(
                            settings = app.settings,
                            vehicleVin = engineState.vehicleVin,
                            onBack = { route = "settings" },
                            onNavigateImmo = { route = "planb_immo" },
                            onNavigateProgramming = { route = "planb_programming" },
                        )
                        "vci_diagnostics" -> com.caseforge.scanner.ui.diag.VciDiagnosticsScreen(
                            onBack = { route = if (setupLiveComplete) "main" else "settings" },
                        )
                        "export_data" -> com.caseforge.scanner.ui.transfer.ExportDataScreen(
                            settings = app.settings,
                            vinHint = engineState.vehicleVin,
                            onBack = { route = "main" },
                            onOpenTransferLog = { route = "transfer_log" },
                        )
                        "transfer_log" -> com.caseforge.scanner.ui.transfer.TransferLogScreen(
                            onBack = { route = "export_data" },
                        )
                        "direct_vci" -> com.caseforge.scanner.ui.obd.DirectVciProbeScreen(
                            onBack = { route = "settings" },
                        )
                        "obd_scan" -> ObdScanScreen(
                            settings = app.settings,
                            onBack = { route = "main" },
                        )
                        "planb_body" ->
                            if (app.settings.isPlanBTierEffective(1)) {
                                SubScreenScaffold(
                                    title = "Plan B · Body read",
                                    onBack = { route = "settings" },
                                ) {
                                    BodyReadScreen(
                                        vehicleVin = engineState.vehicleVin,
                                        lastRecordedVin = app.settings.fastWorkflowState.lastVin,
                                        gatewayReplayEnabled = app.settings.planbGatewayReplay,
                                        gatewaySessionReuseEnabled = app.settings.planbGatewaySessionReuse,
                                    )
                                }
                            } else {
                                RouteGuardRedirect { route = "settings" }
                            }
                        "planb_coding" ->
                            if (app.settings.isPlanBTierEffective(2)) {
                                SubScreenScaffold(
                                    title = "Plan B · Coding checklist",
                                    onBack = { route = "settings" },
                                ) {
                                    CodingChecklistScreen(
                                        vehicleVin = engineState.vehicleVin,
                                        lastRecordedVin = app.settings.fastWorkflowState.lastVin,
                                    )
                                }
                            } else {
                                RouteGuardRedirect { route = "settings" }
                            }
                        "planb_immo" -> {
                            val immoVinHint =
                                engineState.vehicleVin ?: app.settings.fastWorkflowState.lastVin
                            val immoVinMarque = PlanbMarque.fromVin(immoVinHint)
                            val stellantisImmoPath =
                                immoVinMarque == null || SkreemModule.isStellantisMarque(immoVinMarque)
                            when {
                                stellantisImmoPath && !app.settings.canAccessSkreemImmoInfo() ->
                                    RouteGuardRedirect { route = "skreem_trial_gate" }
                                !stellantisImmoPath && !app.settings.isPlanBTierEffective(3) ->
                                    RouteGuardRedirect { route = "settings" }
                                else ->
                                    SubScreenScaffold(
                                        title = "Plan B · Immobilizer info",
                                        onBack = { route = "settings" },
                                    ) {
                                        ImmoInfoScreen(
                                            vehicleVin = engineState.vehicleVin,
                                            lastRecordedVin = app.settings.fastWorkflowState.lastVin,
                                        )
                                    }
                            }
                        }
                        "skreem_trial_gate" ->
                            SubScreenScaffold(
                                title = getString(R.string.skreem_trial_gate_title),
                                onBack = { route = "settings" },
                            ) {
                                SkreemTrialGateScreen(
                                    settings = app.settings,
                                    vinHint = engineState.vehicleVin ?: app.settings.fastWorkflowState.lastVin,
                                    onImmoEnabled = { route = "planb_immo" },
                                    modifier = Modifier.padding(16.dp),
                                )
                            }
                        "planb_programming" ->
                            if (app.settings.canAccessTier4Programming()) {
                                SubScreenScaffold(
                                    title = "Plan B · Programming reference",
                                    onBack = { route = if (setupLiveComplete) "main" else "settings" },
                                ) {
                                    ProgrammingScreen(
                                        vehicleVin = engineState.vehicleVin,
                                        lastRecordedVin = app.settings.fastWorkflowState.lastVin,
                                    )
                                }
                            } else {
                                RouteGuardRedirect { route = "tier4_trial_gate" }
                            }
                        "tier4_trial_gate" ->
                            SubScreenScaffold(
                                title = getString(R.string.tier4_trial_gate_title),
                                onBack = { route = if (setupLiveComplete) "main" else "settings" },
                            ) {
                                Tier4TrialGateScreen(
                                    settings = app.settings,
                                    vinHint = engineState.vehicleVin ?: app.settings.fastWorkflowState.lastVin,
                                    onChecklistsEnabled = { route = "planb_programming" },
                                    modifier = Modifier.padding(16.dp),
                                )
                            }
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

    override fun onStart() {
        super.onStart()
        val usbFilter = IntentFilter().apply {
            addAction(OemUsbVciClient.ACTION_USB_PERMISSION)
            addAction(UsbSerialTransport.ACTION_USB_PERMISSION)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(usbPermissionReceiver, usbFilter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(usbPermissionReceiver, usbFilter)
        }
        lifecycleScope.launch(Dispatchers.IO) {
            val summary = HarvestUploadCoordinator.retryQueuedUploads(applicationContext, app.settings)
            if (summary.succeeded > 0) {
                withContext(Dispatchers.Main) {
                    toast(getString(R.string.shop_desk_queued_uploads_sent, summary.succeeded))
                }
            }
        }
    }

    override fun onStop() {
        runCatching { unregisterReceiver(usbPermissionReceiver) }
        super.onStop()
    }

    override fun onDestroy() {
        super.onDestroy()
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

    private fun syncPlanBData(
        onSuccess: (() -> Unit)? = null,
        onFinished: (() -> Unit)? = null,
    ) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                AgentStatus.setActivity("Syncing Plan B data…")
                val result = LiveUpdateCoordinator.syncPlanBAssets(applicationContext)
                val msg = "Synced ${result.filesUpdated} files (rev ${result.revision})"
                AgentStatus.setActivity(msg)
                lifecycleScope.launch(Dispatchers.Main) {
                    toast(msg)
                    onSuccess?.invoke()
                    onFinished?.invoke()
                }
            } catch (t: Throwable) {
                val msg = t.message?.take(200) ?: t.javaClass.simpleName
                AgentStatus.setActivity("Sync failed: $msg")
                lifecycleScope.launch(Dispatchers.Main) {
                    toast("Sync failed: $msg")
                    onFinished?.invoke()
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

    private fun openOemDiagnosticApp(): Boolean {
        val intent = ScannerAccessibilityService.OEM_DIAG_PACKAGES
            .asSequence()
            .mapNotNull { packageManager.getLaunchIntentForPackage(it) }
            .firstOrNull()
            ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
        return true
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
private fun RouteGuardRedirect(onNavigate: () -> Unit) {
    LaunchedEffect(Unit) { onNavigate() }
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
