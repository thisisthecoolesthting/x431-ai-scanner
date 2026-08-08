@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class,
)

package com.caseforge.scanner.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.LaunchedEffect
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
import com.caseforge.scanner.App
import com.caseforge.scanner.BuildConfig
import com.caseforge.scanner.R
import com.caseforge.scanner.agent.X431InstalledProbe
import com.caseforge.scanner.agent.session.SessionTokenAccounting
import com.caseforge.scanner.data.FastWorkflowState
import com.caseforge.scanner.data.SettingsRepo
import com.caseforge.scanner.oem.OemDataIndex
import com.caseforge.scanner.oem.OemEngineFacade
import com.caseforge.scanner.planb.PlanbMarque
import com.caseforge.scanner.planb.gateway.replay.GoldenReplaySource
import com.caseforge.scanner.ui.components.LoadingState
import com.caseforge.scanner.update.LiveUpdateCoordinator
import androidx.compose.runtime.rememberCoroutineScope
import com.caseforge.scanner.transfer.ShopLinkSelfTestRunner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SettingsScreen(
    settings: SettingsRepo,
    onBack: () -> Unit,
    vehicleVin: String? = null,
    onOpenDataExport: (() -> Unit)? = null,
    onOpenDirectVciProbe: (() -> Unit)? = null,
    onOpenObdScan: (() -> Unit)? = null,
    onOpenPlanbBody: (() -> Unit)? = null,
    onOpenPlanbCoding: (() -> Unit)? = null,
    onOpenPlanbImmo: (() -> Unit)? = null,
    onOpenPlanbProgramming: (() -> Unit)? = null,
    /** Read-only [capabilities.json] filtered by VIN wedge (no OEM execution). */
    onOpenCapabilitiesBrowse: (() -> Unit)? = null,
    onOpenVciDiagnostics: (() -> Unit)? = null,
    onOpenRecallsTsbStub: (() -> Unit)? = null,
    onCheckUpdate: (() -> Unit)? = null,
    onSyncPlanBData: (() -> Unit)? = null,
    planBSyncTick: Int = 0,
    planBSyncInFlight: Boolean = false,
    onOpenClassicHome: (() -> Unit)? = null,
    onRerunSetupLive: (() -> Unit)? = null,
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
    var nativeObdWedge by remember { mutableStateOf(settings.nativeObdExperimental) }
    var nativeObdUseVci by remember { mutableStateOf(settings.nativeObdUseVci) }
    var launchPlanABridge by remember { mutableStateOf(settings.launchPlanABridgeEnabled) }
    var planbBodyRead by remember { mutableStateOf(settings.planbBodyRead) }
    var planbGatewayReplay by remember { mutableStateOf(settings.planbGatewayReplay) }
    var planbCoding by remember { mutableStateOf(settings.planbCoding) }
    var planbImmoInfo by remember { mutableStateOf(settings.planbImmoInfo) }
    var planbProgramming by remember { mutableStateOf(settings.planbProgramming) }
    var tier4FullLicense by remember { mutableStateOf(settings.tier4FullLicenseEnabled) }
    var showTier4LicenseSection by remember { mutableStateOf(false) }
    var showTier4LicenseDialog by remember { mutableStateOf(false) }
    var tier4LicenseCodeInput by remember { mutableStateOf("") }
    var tier4LicenseMarquesInput by remember { mutableStateOf("") }
    var tierSafetyFirstConnect by remember { mutableStateOf(settings.tierSafetyFirstConnect) }
    var deepseekBleElmStandalone by remember { mutableStateOf(settings.deepseekBleElmStandalone) }
    var deepseekGpsEnabled by remember { mutableStateOf(settings.deepseekGpsEnabled) }
    var deepseekOcrEnabled by remember { mutableStateOf(settings.deepseekOcrEnabled) }
    var deepseekGatewayPoolEnabled by remember { mutableStateOf(settings.deepseekGatewayPoolEnabled) }
    var deepseekStreamingEnabled by remember { mutableStateOf(settings.deepseekStreamingEnabled) }
    var oemFacadeStatusLines by remember { mutableStateOf<List<String>>(emptyList()) }
    var lastCrashTail by remember { mutableStateOf<String?>(null) }
    var homeMode by remember { mutableStateOf(settings.homeMode) }
    var fastWorkflow by remember { mutableStateOf(settings.fastWorkflowState) }
    var showReceiverAdvanced by remember { mutableStateOf(false) }
    var receiverHost by remember { mutableStateOf(settings.receiverPcHost) }
    var receiverPortText by remember { mutableStateOf(settings.receiverPcPort.toString()) }
    var multipartFallback by remember { mutableStateOf(settings.useMultipartFallback) }
    var harvestIncludeCoarseLoc by remember { mutableStateOf(settings.includeCoarseLocationInUpload) }
    var shopDeskIngestEnabled by remember { mutableStateOf(settings.shopDeskIngestEnabled) }
    var shopDeskUseProductionDesk by remember { mutableStateOf(settings.shopDeskUseProductionDesk) }
    var shopDeskIngestUrl by remember { mutableStateOf(settings.shopDeskIngestUrl) }
    var shopDeskLanReportingEnabled by remember { mutableStateOf(settings.shopDeskLanReportingEnabled) }
    var shopDeskLanBroadcastEnabled by remember { mutableStateOf(settings.shopDeskLanBroadcastEnabled) }
    var shopDeskLanBroadcastAtLabel by remember {
        mutableStateOf(formatBroadcastAt(settings.shopDeskLanBroadcastAtMs))
    }
    var selfTestReport by remember { mutableStateOf<ShopLinkSelfTestRunner.SelfTestReport?>(null) }
    var selfTestRunning by remember { mutableStateOf(false) }
    var selfTestShareText by remember { mutableStateOf("") }
    val selfTestScope = rememberCoroutineScope()
    val context = LocalContext.current
    val dateFmt = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US) }
    val launchInstalled by remember {
        mutableStateOf(
            X431InstalledProbe.installedFlags(context.packageManager).values.any { it },
        )
    }
    var lastSyncLabel by remember { mutableStateOf<String?>(null) }
    var updateChannelLabel by remember { mutableStateOf("") }
    var offlineVehicleLines by remember { mutableStateOf<List<String>>(emptyList()) }
    var initialLoading by remember { mutableStateOf(true) }
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

    suspend fun refreshLiveUpdateLabels(appCtx: android.content.Context) {
        updateChannelLabel = LiveUpdateCoordinator.channelSummary(appCtx)
        val lastMs = LiveUpdateCoordinator.lastBundleSyncMs(appCtx)
        lastSyncLabel = if (lastMs > 0L) formatScanTime(lastMs) else null
    }

    LaunchedEffect(Unit) {
        initialLoading = true
        val appCtx = context.applicationContext
        withContext(Dispatchers.IO) {
            OemDataIndex.scanWithBundle(appCtx)
            refreshLiveUpdateLabels(appCtx)
            val crashFile = File(context.cacheDir, "last_crash.txt")
            lastCrashTail = if (crashFile.isFile) {
                val text = crashFile.readText()
                val lines = text.lines()
                val tail = if (lines.size <= 24) text.trim() else lines.takeLast(24).joinToString("\n")
                tail.ifBlank { null }
            } else {
                null
            }
        }
        ShopLinkSelfTestRunner.loadReport(context.cacheDir)?.let { cachedSelfTest ->
            selfTestReport = cachedSelfTest
            selfTestShareText = ShopLinkSelfTestRunner.formatReportText(cachedSelfTest)
        }
        val sum = OemDataIndex.lastSummary
        val wedgeVinHint = vehicleVin?.takeIf { it.isNotBlank() }
            ?: settings.fastWorkflowState.lastVin?.takeIf { it.isNotBlank() }
        offlineVehicleLines = if (sum != null) {
            OemDataIndex.enrichedDisplayLines(sum, appCtx, wedgeVinHint = wedgeVinHint)
        } else {
            emptyList()
        }
        initialLoading = false
    }

    LaunchedEffect(planBSyncTick) {
        if (planBSyncTick <= 0) return@LaunchedEffect
        withContext(Dispatchers.IO) {
            refreshLiveUpdateLabels(context.applicationContext)
        }
    }

    LaunchedEffect(
        nativeObdWedge,
        nativeObdUseVci,
        planbBodyRead,
        planbCoding,
        planbImmoInfo,
        planbProgramming,
        tierSafetyFirstConnect,
    ) {
        val anyPlanB =
            nativeObdWedge || nativeObdUseVci || planbBodyRead || planbCoding || planbImmoInfo || planbProgramming
        val appCtx = context.applicationContext
        val facade = (appCtx as? App)?.oemEngineFacade() ?: OemEngineFacade(appCtx, settings)
        if (!anyPlanB) {
            withContext(Dispatchers.IO) {
                facade.refreshSuspend(preserveConnection = false)
            }
            oemFacadeStatusLines = emptyList()
            return@LaunchedEffect
        }
        withContext(Dispatchers.IO) {
            // Stay on the wire when only settings/plan flags change; full cycle when safety forces it is unnecessary.
            facade.refreshSuspendPreserveConnection()
        }
        oemFacadeStatusLines = facade.statusLines().take(8)
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
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.a11y_nav_back),
                    )
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
            if (onOpenClassicHome != null || onRerunSetupLive != null) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Home & setup", style = MaterialTheme.typography.titleSmall)
                        onOpenClassicHome?.let { openClassic ->
                            OutlinedButton(onClick = openClassic, modifier = Modifier.fillMaxWidth()) {
                                Text("Classic home (scanner grid)")
                            }
                        }
                        onRerunSetupLive?.let { rerun ->
                            TextButton(onClick = rerun) {
                                Text("Re-run setup & live checklist")
                            }
                        }
                    }
                }
            }
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(R.string.build_info_label, BuildConfig.BUILD_INFO),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.combinedClickable(
                            onClick = {},
                            onLongClick = {
                                showTier4LicenseSection = true
                                showTier4LicenseDialog = true
                            },
                        ),
                    )
                    Text(
                        stringResource(
                            R.string.live_update_channel,
                            updateChannelLabel.ifBlank { "—" },
                        ),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        if (lastSyncLabel != null) {
                            stringResource(R.string.live_update_last_sync, lastSyncLabel!!)
                        } else {
                            stringResource(R.string.live_update_last_sync_never)
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        stringResource(R.string.update_from_git_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (onSyncPlanBData != null) {
                        OutlinedButton(
                            onClick = onSyncPlanBData,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        ) {
                            Text(stringResource(R.string.sync_planb_data))
                        }
                    }
                    if (planBSyncInFlight) {
                        LoadingState(
                            message = "Syncing Plan B data",
                            animatedDots = true,
                            showLinearProgress = true,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
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
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "Vehicle data (device)",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    val lines = offlineVehicleLines
                    if (initialLoading) {
                        LoadingState(
                            message = "Loading vehicle summary",
                            animatedDots = true,
                            showLinearProgress = true,
                            modifier = Modifier.padding(vertical = 4.dp),
                        )
                    } else if (lines.isEmpty()) {
                        Text("Summary unavailable.", style = MaterialTheme.typography.bodySmall)
                    } else {
                        lines.take(4).forEach { line ->
                            Text(line, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("DeepSeek features", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Wishlist lane toggles for new hardware paths and entry stubs.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    ListItem(
                        headlineContent = { Text("Standalone BLE ELM327 diagnostics entry") },
                        supportingContent = { Text("Allow BLE-only diagnostics path without USB requirements.") },
                        trailingContent = {
                            Switch(
                                checked = deepseekBleElmStandalone,
                                onCheckedChange = { on ->
                                    deepseekBleElmStandalone = on
                                    settings.deepseekBleElmStandalone = on
                                },
                            )
                        },
                    )
                    if (deepseekBleElmStandalone && onOpenObdScan != null) {
                        OutlinedButton(
                            onClick = onOpenObdScan,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        ) {
                            Text("Open BLE diagnostics screen")
                        }
                    }
                    if (onOpenRecallsTsbStub != null) {
                        OutlinedButton(
                            onClick = onOpenRecallsTsbStub,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        ) {
                            Text("Open recall / TSB lookup stub")
                        }
                    }
                    ListItem(
                        headlineContent = { Text("GPS hardware features") },
                        trailingContent = {
                            Switch(
                                checked = deepseekGpsEnabled,
                                onCheckedChange = { on ->
                                    deepseekGpsEnabled = on
                                    settings.deepseekGpsEnabled = on
                                },
                            )
                        },
                    )
                    ListItem(
                        headlineContent = { Text("OCR hardware features") },
                        trailingContent = {
                            Switch(
                                checked = deepseekOcrEnabled,
                                onCheckedChange = { on ->
                                    deepseekOcrEnabled = on
                                    settings.deepseekOcrEnabled = on
                                },
                            )
                        },
                    )
                    ListItem(
                        headlineContent = { Text("Gateway pool") },
                        trailingContent = {
                            Switch(
                                checked = deepseekGatewayPoolEnabled,
                                onCheckedChange = { on ->
                                    deepseekGatewayPoolEnabled = on
                                    settings.deepseekGatewayPoolEnabled = on
                                    settings.planbGatewaySessionReuse = on
                                },
                            )
                        },
                    )
                    ListItem(
                        headlineContent = { Text("Streaming diagnostics") },
                        trailingContent = {
                            Switch(
                                checked = deepseekStreamingEnabled,
                                onCheckedChange = { on ->
                                    deepseekStreamingEnabled = on
                                    settings.deepseekStreamingEnabled = on
                                },
                            )
                        },
                    )
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
                    Text(
                        "Primary export: Settings → Transfer data → Zip files, then ES File Explorer + USB.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (showReceiverAdvanced) {
                        Text(
                            "Advanced only: LAN/HTTPS auto-upload settings.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
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
                        ListItem(
                            headlineContent = { Text(stringResource(R.string.settings_harvest_include_coarse_location)) },
                            trailingContent = {
                                Switch(
                                    checked = harvestIncludeCoarseLoc,
                                    onCheckedChange = {
                                        harvestIncludeCoarseLoc = it
                                        settings.includeCoarseLocationInUpload = it
                                    },
                                )
                            },
                        )
                        ListItem(
                            headlineContent = { Text("Broadcast on LAN (Phase 1 stub)") },
                            supportingContent = {
                                Text(
                                    buildString {
                                        append("Prepare tablet LAN auto-discovery hooks for Shop Desk pairing.")
                                        if (shopDeskLanBroadcastAtLabel != null) {
                                            append(" Last broadcast: ")
                                            append(shopDeskLanBroadcastAtLabel)
                                        }
                                    },
                                )
                            },
                            trailingContent = {
                                Switch(
                                    checked = shopDeskLanBroadcastEnabled,
                                    onCheckedChange = {
                                        shopDeskLanBroadcastEnabled = it
                                        settings.shopDeskLanBroadcastEnabled = it
                                    },
                                )
                            },
                        )
                        if (shopDeskLanBroadcastEnabled) {
                            OutlinedButton(
                                onClick = {
                                    com.caseforge.scanner.transfer.HarvestUploadCoordinator
                                        .broadcastLanDiscoveryNow(settings)
                                    shopDeskLanBroadcastAtLabel =
                                        formatBroadcastAt(settings.shopDeskLanBroadcastAtMs)
                                },
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                            ) {
                                Text("Broadcast now on LAN")
                            }
                        }
                        ListItem(
                            headlineContent = { Text("Use production desk") },
                            supportingContent = {
                                Text(
                                    "HTTPS ingest to ${SettingsRepo.DEFAULT_SHOP_DESK_INGEST_URL_PROD}. " +
                                        "Off = LAN/dev (${SettingsRepo.DEFAULT_SHOP_DESK_INGEST_URL}).",
                                )
                            },
                            trailingContent = {
                                Switch(
                                    checked = shopDeskUseProductionDesk,
                                    onCheckedChange = { on ->
                                        shopDeskUseProductionDesk = on
                                        settings.shopDeskUseProductionDesk = on
                                        shopDeskIngestUrl = settings.shopDeskIngestUrl
                                    },
                                )
                            },
                        )
                        ListItem(
                            headlineContent = { Text("LAN setup reporting") },
                            supportingContent = {
                                Text(
                                    "POST each setup wizard step to Shop Desk /api/ingest/setup-step on LAN. " +
                                        "Defaults on when desk URL is http:// (off for production HTTPS).",
                                )
                            },
                            trailingContent = {
                                Switch(
                                    checked = shopDeskLanReportingEnabled,
                                    onCheckedChange = {
                                        shopDeskLanReportingEnabled = it
                                        settings.shopDeskLanReportingEnabled = it
                                    },
                                )
                            },
                        )
                        ListItem(
                            headlineContent = { Text("Shop Desk ingest") },
                            supportingContent = {
                                Text("POST session/harvest to Shop Desk (HTTP or HTTPS).")
                            },
                            trailingContent = {
                                Switch(
                                    checked = shopDeskIngestEnabled,
                                    onCheckedChange = {
                                        shopDeskIngestEnabled = it
                                        settings.shopDeskIngestEnabled = it
                                    },
                                )
                            },
                        )
                        OutlinedTextField(
                            value = shopDeskIngestUrl,
                            onValueChange = {
                                shopDeskIngestUrl = it
                                settings.shopDeskIngestUrl = it
                                shopDeskUseProductionDesk = settings.shopDeskUseProductionDesk
                            },
                            label = { Text("Shop Desk ingest URL") },
                            placeholder = {
                                Text(
                                    if (shopDeskUseProductionDesk) {
                                        SettingsRepo.DEFAULT_SHOP_DESK_INGEST_URL_PROD
                                    } else {
                                        SettingsRepo.DEFAULT_SHOP_DESK_INGEST_URL
                                    },
                                )
                            },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (settings.shopDeskIngestUrlStored.isBlank() && !shopDeskUseProductionDesk) {
                            Text(
                                stringResource(
                                    R.string.settings_shop_desk_production_hint,
                                    SettingsRepo.DEFAULT_SHOP_DESK_INGEST_URL_PROD,
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Anthropic API key", style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = when {
                            settings.hasEmbeddedBuildApiKey ->
                                stringResource(R.string.api_key_source_embedded)
                            settings.hasUserStoredApiKey ->
                                stringResource(R.string.api_key_source_settings)
                            else -> stringResource(R.string.api_key_source_not_set)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
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
                    Text(
                        SessionTokenAccounting.formatSettingsPrimaryLine(settings),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        SessionTokenAccounting.formatSettingsSubtext(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    SessionTokenAccounting.formatSettingsRecentAvgLine(settings)?.let { avgLine ->
                        Text(
                            avgLine,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    SessionTokenAccounting.formatSettingsTodayLine(settings)?.let { todayLine ->
                        Text(
                            todayLine,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
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

            Text(
                "Experimental",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Plan B (experimental)", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Enabled tiers: snapshot at connect when tier safety is on; toggling tiers here keeps " +
                            "the VCI/OBD transport up (no forced disconnect).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    ListItem(
                        headlineContent = { Text("Tier safety (first connect)") },
                        supportingContent = {
                            Text(
                                "Tiers 1–4 need a successful OBD refresh (or toggle on after connect) before " +
                                    "tools use them. Tier 0 OBD follows its switch only.",
                            )
                        },
                        trailingContent = {
                            Switch(
                                checked = tierSafetyFirstConnect,
                                onCheckedChange = { on ->
                                    tierSafetyFirstConnect = on
                                    settings.tierSafetyFirstConnect = on
                                },
                            )
                        },
                    )
                    ListItem(
                        headlineContent = { Text("Tier 0 — Native OBD") },
                        supportingContent = {
                            Text("Plan B OEM engine path (stub transport until BT/USB wired).")
                        },
                        trailingContent = {
                            Switch(
                                checked = nativeObdWedge,
                                onCheckedChange = { on ->
                                    nativeObdWedge = on
                                    settings.tier0ObdEnabled = on
                                },
                            )
                        },
                    )
                    ListItem(
                        headlineContent = { Text("Native OBD via VCI") },
                        supportingContent = {
                            Text(
                                "Route Plan B native OBD through the VCI transport even when Direct VCI is off.",
                            )
                        },
                        trailingContent = {
                            Switch(
                                checked = nativeObdUseVci,
                                onCheckedChange = { on ->
                                    nativeObdUseVci = on
                                    settings.nativeObdUseVci = on
                                },
                            )
                        },
                    )
                    if (launchInstalled) {
                        ListItem(
                            headlineContent = { Text(stringResource(R.string.settings_launch_plan_a_bridge)) },
                            supportingContent = {
                                Text(
                                    "Fallback when DB15/OEM USB fails: open Launch diagnostics, then read DTCs from accessibility golden captures.",
                                )
                            },
                            trailingContent = {
                                Switch(
                                    checked = launchPlanABridge,
                                    onCheckedChange = { on ->
                                        launchPlanABridge = on
                                        settings.launchPlanABridgeEnabled = on
                                    },
                                )
                            },
                        )
                    }
                    if (nativeObdWedge && onOpenObdScan != null) {
                        OutlinedButton(
                            onClick = onOpenObdScan,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        ) {
                            Text("Open OBD scan screen")
                        }
                        OutlinedButton(
                            onClick = onOpenObdScan,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        ) {
                            Text("Find drivers & adapters (connection readiness)")
                        }
                    }
                    ListItem(
                        headlineContent = { Text("Tier 1 — Body / convenience read") },
                        supportingContent = { Text("Read-only body module access (scaffolding).") },
                        trailingContent = {
                            Switch(
                                checked = planbBodyRead,
                                onCheckedChange = { on ->
                                    planbBodyRead = on
                                    settings.tier1BodyEnabled = on
                                    if (!on) {
                                        planbGatewayReplay = false
                                        settings.planbGatewayReplay = false
                                    }
                                    if (on) settings.onUserEnabledPlanBTier(1) else settings.onUserDisabledPlanBTier(1)
                                },
                            )
                        },
                    )
                    if (planbBodyRead) {
                        ListItem(
                            headlineContent = { Text("Gateway replay (Ford bench)") },
                            supportingContent = {
                                Text(
                                    "When enabled with Tier 1 body read, Ford marque uses bundled golden JSONL so " +
                                        "GatewaySession.readDtcs returns scaffold codes (e.g. P0102). Off for production scans.",
                                )
                            },
                            trailingContent = {
                                Switch(
                                    checked = planbGatewayReplay,
                                    onCheckedChange = { on ->
                                        if (on && !GoldenReplaySource.hasBundledAsset(context, PlanbMarque.FORD)) {
                                            planbGatewayReplay = false
                                            settings.planbGatewayReplay = false
                                        } else {
                                            planbGatewayReplay = on
                                            settings.planbGatewayReplay = on
                                        }
                                    },
                                )
                            },
                        )
                    }
                    ListItem(
                        headlineContent = { Text("Tier 2 — Reversible coding") },
                        supportingContent = { Text("Coding operations (stub until G4).") },
                        trailingContent = {
                            Switch(
                                checked = planbCoding,
                                onCheckedChange = { on ->
                                    planbCoding = on
                                    settings.tier2CodingEnabled = on
                                    if (on) settings.onUserEnabledPlanBTier(2) else settings.onUserDisabledPlanBTier(2)
                                },
                            )
                        },
                    )
                    ListItem(
                        headlineContent = { Text("Tier 3 — Immobilizer info") },
                        supportingContent = { Text("Immo info-only (no key programming).") },
                        trailingContent = {
                            Switch(
                                checked = planbImmoInfo,
                                onCheckedChange = { on ->
                                    planbImmoInfo = on
                                    settings.tier3ImmoEnabled = on
                                    if (on) settings.onUserEnabledPlanBTier(3) else settings.onUserDisabledPlanBTier(3)
                                },
                            )
                        },
                    )
                    ListItem(
                        headlineContent = { Text("Tier 4 — Programming (partner gate)") },
                        supportingContent = {
                            Text(
                                when {
                                    tier4FullLicense ->
                                        stringResource(R.string.tier4_license_active)
                                    settings.tier4TrialActive ->
                                        "Reference checklist only — partner or manual tooling."
                                    else ->
                                        "Accept the Tier 4 trial or redeem an operator license to enable."
                                },
                            )
                        },
                        trailingContent = {
                            Switch(
                                checked = planbProgramming,
                                onCheckedChange = { on ->
                                    if (on && !settings.hasTier4ProgrammingEntitlement) {
                                        return@Switch
                                    }
                                    planbProgramming = on
                                    settings.tier4ProgrammingEnabled = on
                                    if (on) settings.onUserEnabledPlanBTier(4) else settings.onUserDisabledPlanBTier(4)
                                },
                                enabled = settings.hasTier4ProgrammingEntitlement,
                            )
                        },
                    )
                    if (planbBodyRead && settings.isPlanBTierEffective(1) && onOpenPlanbBody != null) {
                        TextButton(
                            onClick = onOpenPlanbBody,
                            modifier = Modifier.padding(start = 8.dp),
                        ) {
                            Text("Open body read", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    if (planbCoding && settings.isPlanBTierEffective(2) && onOpenPlanbCoding != null) {
                        TextButton(
                            onClick = onOpenPlanbCoding,
                            modifier = Modifier.padding(start = 8.dp),
                        ) {
                            Text("Open coding checklist", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    if (planbImmoInfo && settings.isPlanBTierEffective(3) && onOpenPlanbImmo != null) {
                        TextButton(
                            onClick = onOpenPlanbImmo,
                            modifier = Modifier.padding(start = 8.dp),
                        ) {
                            Text("Open immobilizer info", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    if (planbProgramming && settings.canAccessTier4Programming() && onOpenPlanbProgramming != null) {
                        TextButton(
                            onClick = onOpenPlanbProgramming,
                            modifier = Modifier.padding(start = 8.dp),
                        ) {
                            Text("Open programming reference", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    if (onOpenCapabilitiesBrowse != null) {
                        OutlinedButton(
                            onClick = onOpenCapabilitiesBrowse,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                .heightIn(min = 48.dp),
                        ) {
                            Text("Browse capabilities (wedge, read-only)")
                        }
                    }
                    val anyPlanB =
                        nativeObdWedge || nativeObdUseVci || planbBodyRead || planbCoding || planbImmoInfo || planbProgramming
                    if (anyPlanB) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                        ) {
                            Text(
                                "OEM engine facade status",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            val lines = oemFacadeStatusLines
                            if (lines.isEmpty()) {
                                Text("Refreshing…", style = MaterialTheme.typography.bodySmall)
                            } else {
                                lines.forEach { line ->
                                    Text(line, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Debug", style = MaterialTheme.typography.titleSmall)
                    if (showTier4LicenseSection || tier4FullLicense) {
                        Text(
                            stringResource(R.string.tier4_license_section_title),
                            style = MaterialTheme.typography.labelMedium,
                        )
                        Text(
                            if (tier4FullLicense) {
                                stringResource(R.string.tier4_license_active)
                            } else {
                                stringResource(R.string.tier4_license_path_hint)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (tier4FullLicense) {
                            settings.tier4LicenseMarques?.let { marques ->
                                Text(
                                    stringResource(R.string.tier4_license_marques_active, marques),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            OutlinedButton(
                                onClick = {
                                    settings.clearTier4FullLicense()
                                    tier4FullLicense = false
                                    planbProgramming = settings.planbProgramming
                                },
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                            ) {
                                Text(stringResource(R.string.tier4_license_clear))
                            }
                        } else {
                            OutlinedButton(
                                onClick = { showTier4LicenseDialog = true },
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                            ) {
                                Text(stringResource(R.string.tier4_license_redeem))
                            }
                        }
                    }
                    Text(
                        "Operator diagnostics — no adb required.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text("Last crash (cache/last_crash.txt)", style = MaterialTheme.typography.labelMedium)
                    Text(
                        lastCrashTail ?: "(none recorded)",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (lastCrashTail != null) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                    Text("Last OEM foreground block", style = MaterialTheme.typography.labelMedium)
                    val oemBlockLive = App.lastOemForegroundBlockReason
                    val oemBlockSaved = settings.lastOemDiagConnectBlockReason.takeIf { it.isNotBlank() }
                    val oemBlockAt = settings.lastOemDiagConnectBlockAtMs
                    val oemBlockText = oemBlockLive ?: oemBlockSaved
                    Text(
                        when {
                            oemBlockText != null -> {
                                val whenStr = if (oemBlockAt > 0L) {
                                    " @ ${dateFmt.format(Date(oemBlockAt))}"
                                } else {
                                    ""
                                }
                                "$oemBlockText$whenStr"
                            }
                            else -> "(none)"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (oemBlockText != null) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                    Text("Last connect attempt", style = MaterialTheme.typography.labelMedium)
                    val connectSummary = settings.lastConnectAttemptSummary.takeIf { it.isNotBlank() }
                    val connectAt = settings.lastConnectAttemptAtMs
                    Text(
                        when {
                            connectSummary != null -> {
                                val status = if (settings.lastConnectAttemptSuccess) "ok" else "fail"
                                val whenStr = if (connectAt > 0L) {
                                    " @ ${dateFmt.format(Date(connectAt))}"
                                } else {
                                    ""
                                }
                                "[$status]$whenStr $connectSummary"
                            }
                            else -> "(none logged yet — tap Connect on Scan vehicle)"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = when {
                            connectSummary == null -> MaterialTheme.colorScheme.onSurfaceVariant
                            settings.lastConnectAttemptSuccess -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.error
                        },
                    )
                }
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(R.string.settings_self_test_section_title),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        stringResource(R.string.settings_self_test_section_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    selfTestReport?.timestamp?.let { ts ->
                        Text(
                            stringResource(R.string.settings_self_test_last_run, ts),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                    selfTestReport?.results?.forEach { result ->
                        val statusLabel = stringResource(
                            if (result.passed) R.string.settings_self_test_pass
                            else R.string.settings_self_test_fail,
                        )
                        val latencySuffix = result.latencyMs?.let {
                            stringResource(R.string.settings_self_test_latency, it)
                        }.orEmpty()
                        Text(
                            stringResource(
                                R.string.settings_self_test_result_line,
                                result.name,
                                statusLabel,
                                latencySuffix,
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (result.passed) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.error
                            },
                        )
                        Text(
                            result.detail,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (selfTestRunning) {
                        LoadingState(
                            message = stringResource(R.string.settings_self_test_running),
                            animatedDots = true,
                            showLinearProgress = true,
                        )
                    }
                    Button(
                        onClick = {
                            selfTestScope.launch {
                                selfTestRunning = true
                                try {
                                    val report = ShopLinkSelfTestRunner.runAllAndPersist(context, settings)
                                    selfTestReport = report
                                    selfTestShareText = ShopLinkSelfTestRunner.formatReportText(report)
                                } finally {
                                    selfTestRunning = false
                                }
                            }
                        },
                        enabled = !selfTestRunning,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    ) {
                        Text(stringResource(R.string.settings_self_test_run_all))
                    }
                    if (selfTestShareText.isNotBlank()) {
                        OutlinedButton(
                            onClick = {
                                copySelfTestResults(context, selfTestShareText)
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.settings_self_test_copied),
                                    Toast.LENGTH_SHORT,
                                ).show()
                            },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        ) {
                            Text(stringResource(R.string.settings_self_test_copy))
                        }
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 160.dp)
                                .verticalScroll(rememberScrollState()),
                        ) {
                            Text(
                                selfTestShareText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

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
                            stringResource(R.string.transfer_card_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Button(
                            onClick = onOpenDataExport,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        ) {
                            Text(stringResource(R.string.transfer_screen_title))
                        }
                    }
                }
            }
        }
    }

    if (showTier4LicenseDialog) {
        AlertDialog(
            onDismissRequest = { showTier4LicenseDialog = false },
            title = { Text(stringResource(R.string.tier4_license_dialog_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = tier4LicenseCodeInput,
                        onValueChange = { tier4LicenseCodeInput = it },
                        label = { Text(stringResource(R.string.tier4_license_code_label)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = tier4LicenseMarquesInput,
                        onValueChange = { tier4LicenseMarquesInput = it },
                        label = { Text(stringResource(R.string.tier4_license_marques_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val marques = tier4LicenseMarquesInput.trim().takeIf { it.isNotBlank() }
                        val ok = settings.redeemTier4LicenseCode(tier4LicenseCodeInput, marques)
                        if (ok) {
                            tier4FullLicense = true
                            showTier4LicenseSection = true
                            tier4LicenseCodeInput = ""
                            showTier4LicenseDialog = false
                            Toast.makeText(
                                context,
                                context.getString(R.string.tier4_license_redeem_success),
                                Toast.LENGTH_SHORT,
                            ).show()
                        } else {
                            Toast.makeText(
                                context,
                                context.getString(R.string.tier4_license_redeem_failed),
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    },
                ) {
                    Text(stringResource(R.string.tier4_license_redeem))
                }
            },
            dismissButton = {
                TextButton(onClick = { showTier4LicenseDialog = false }) {
                    Text(stringResource(R.string.tier4_license_dialog_dismiss))
                }
            },
        )
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

private fun copySelfTestResults(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("cu1-self-test", text))
}

private fun formatBroadcastAt(epochMillis: Long): String? {
    if (epochMillis <= 0L) return null
    return formatScanTime(epochMillis)
}
