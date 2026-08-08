package com.caseforge.scanner.ui.setup

import android.content.Context
import com.caseforge.scanner.agent.ObdUsbTool
import com.caseforge.scanner.agent.discovery.VehicleProfileLoader
import com.caseforge.scanner.data.SettingsRepo
import com.caseforge.scanner.transfer.HarvestBatchManifest
import com.caseforge.scanner.transfer.ShopLinkSelfTestRunner
import com.caseforge.scanner.transfer.TabletDataHarvester
import com.caseforge.scanner.ui.session.SessionWizardLinkProbe
import com.caseforge.scanner.vci.DiagnosticConnector
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Executes individual setup/live wizard checks off the UI thread.
 */
object SetupStepRunner {

    const val SETUP_PROBE_SESSION_ID = "setup_live_probe"

    suspend fun run(
        context: Context,
        settings: SettingsRepo,
        progress: SetupProgressStore,
        step: SetupLiveStep,
        cameraTestFile: File? = null,
    ): SetupStepState = withContext(Dispatchers.IO) {
        when (step) {
            SetupLiveStep.APP_HEALTH -> runAppHealth(context, settings)
            SetupLiveStep.SHOP_DESK -> runShopDesk(settings)
            SetupLiveStep.LINK_TRANSPORT -> runLinkTransport(context, settings)
            SetupLiveStep.CAMERA -> runCamera(cameraTestFile)
            SetupLiveStep.SESSION_BOOTSTRAP -> runSessionBootstrap(context)
            SetupLiveStep.HARVEST_PATH -> runHarvestPath(context, settings)
            SetupLiveStep.TIER4_TRIAL -> runTier4Trial(progress)
            SetupLiveStep.MARK_COMPLETE -> runMarkComplete(progress, settings)
        }
    }

    private fun runAppHealth(context: Context, settings: SettingsRepo): SetupStepState {
        val crashFile = File(context.cacheDir, "last_crash.txt")
        if (crashFile.isFile) {
            val tail = crashFile.readText().lineSequence().take(3).joinToString(" · ")
            return SetupStepState(
                SetupStepStatus.FAILED,
                "Last crash recorded: ${tail.take(200)}",
            )
        }
        return runCatching {
            val hadKey = settings.hasUserStoredApiKey || settings.hasEmbeddedBuildApiKey
            if (!hadKey && settings.claudeApiKey.isBlank()) {
                return SetupStepState(
                    SetupStepStatus.FAILED,
                    "No API key available (encrypted prefs readable but key empty)",
                )
            }
            SetupStepState(
                SetupStepStatus.PASSED,
                "Encrypted prefs OK · no last_crash.txt · API key ${if (settings.claudeApiKey.isNotBlank()) "present" else "missing"}",
            )
        }.getOrElse { err ->
            SetupStepState(SetupStepStatus.FAILED, "Prefs health check failed: ${err.message}")
        }
    }

    private suspend fun runShopDesk(settings: SettingsRepo): SetupStepState {
        val result = ShopLinkSelfTestRunner.pingShopDesk(settings)
        return if (result.passed) {
            SetupStepState(SetupStepStatus.PASSED, result.detail)
        } else {
            SetupStepState(SetupStepStatus.FAILED, result.detail)
        }
    }

    private suspend fun runLinkTransport(context: Context, settings: SettingsRepo): SetupStepState {
        val usb = ObdUsbTool(context)
        val devices = usb.listDevices()
        if (devices.isEmpty()) {
            val mode = settings.linkTransport
            return SetupStepState(
                SetupStepStatus.PASSED,
                "No USB OBD device attached (transport=$mode). Plug cable and re-run to probe.",
            )
        }
        val permitted = devices.filter { usb.hasPermission(it) }
        val target = permitted.firstOrNull() ?: devices.first()
        if (!usb.hasPermission(target)) {
            return SetupStepState(
                SetupStepStatus.FAILED,
                "${devices.size} USB device(s); grant USB permission then re-run.",
            )
        }
        val kind = runCatching { DiagnosticConnector.detectUsbKind(context, target) }.getOrNull()
        val label = kind?.name?.replace('_', ' ') ?: "unknown"
        return SetupStepState(
            SetupStepStatus.PASSED,
            "Probe OK on ${target.deviceName}: $label (non-destructive detectUsbKind)",
        )
    }

    private fun runCamera(cameraTestFile: File?): SetupStepState {
        val file = cameraTestFile
        if (file == null) {
            return SetupStepState(
                SetupStepStatus.FAILED,
                "Tap Run step to open the camera test capture.",
            )
        }
        return if (file.isFile && file.length() > 0L) {
            SetupStepState(
                SetupStepStatus.PASSED,
                "Capture saved (${file.length()} bytes) at ${file.name}",
            )
        } else {
            SetupStepState(
                SetupStepStatus.FAILED,
                "No capture yet — complete the camera test or skip with a reason.",
            )
        }
    }

    private suspend fun runSessionBootstrap(context: Context): SetupStepState {
        val banner = SessionWizardLinkProbe.probe(context, SETUP_PROBE_SESSION_ID, vinHint = null)
        return if (banner == null) {
            SetupStepState(
                SetupStepStatus.PASSED,
                "Session bootstrap probe OK (link ready or Plan B path inactive).",
            )
        } else {
            SetupStepState(
                SetupStepStatus.FAILED,
                banner,
            )
        }
    }

    private fun runHarvestPath(context: Context, settings: SettingsRepo): SetupStepState {
        return runCatching {
            val batch = TabletDataHarvester.build(
                context = context,
                vehicleProfileId = VehicleProfileLoader.DEFAULT_WINDSTAR_ID,
                settings = settings,
            )
            val manifest = batch.manifest
            require(manifest.schemaVersion == HarvestBatchManifest.SCHEMA_VERSION) {
                "Unexpected schema ${manifest.schemaVersion}"
            }
            require(manifest.versionCode > 0 && manifest.vehicleProfileId.isNotBlank()) {
                "Invalid harvest manifest"
            }
            val jsonBytes = HarvestBatchManifest.toJsonBytes(manifest)
            SetupStepState(
                SetupStepStatus.PASSED,
                "Dry-run manifest OK (${jsonBytes.size} bytes, ${manifest.discoveryReport.devices.size} device row(s))",
            )
        }.getOrElse { err ->
            SetupStepState(SetupStepStatus.FAILED, "Harvest dry-run failed: ${err.message}")
        }
    }

    private fun runTier4Trial(progress: SetupProgressStore): SetupStepState {
        return if (progress.tier4TrialAccepted) {
            SetupStepState(
                SetupStepStatus.PASSED,
                "Tier 4 trial terms accepted — enable checklists when ready (Settings or compact home).",
            )
        } else {
            SetupStepState(
                SetupStepStatus.FAILED,
                "Read the trial terms, pick a marque, and tap Accept Tier 4 trial terms.",
            )
        }
    }

    private fun runMarkComplete(progress: SetupProgressStore, settings: SettingsRepo): SetupStepState {
        val prior = SetupLiveStep.entries.filter { it != SetupLiveStep.MARK_COMPLETE }
        val priorOk = prior.all {
            progress.stepState(it).status in setOf(SetupStepStatus.PASSED, SetupStepStatus.SKIPPED)
        }
        if (!priorOk) {
            return SetupStepState(
                SetupStepStatus.FAILED,
                "Resolve all prior steps (pass or skip) before marking complete.",
            )
        }
        progress.setupLiveComplete = true
        settings.wizardComplete = true
        return SetupStepState(
            SetupStepStatus.PASSED,
            "Setup complete — compact home unlocked.",
        )
    }
}
