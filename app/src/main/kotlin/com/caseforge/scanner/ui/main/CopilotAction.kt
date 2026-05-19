package com.caseforge.scanner.ui.main

import com.caseforge.scanner.vci.DiagnosticConnector

/**
 * User- or copilot-initiated intents from [AiCopilotHomeScreen].
 * [com.caseforge.scanner.MainActivity] maps these to existing navigation and VCI flows.
 */
sealed interface CopilotAction {
    /** Suggested chip: read all modules / DTCs. */
    data object ScanVehicle : CopilotAction

    /** Suggested chip: explain stored codes (AI or offline explainer upstream). */
    data object ExplainCurrentCodes : CopilotAction

    /** Suggested chip: open live PID stream. */
    data object StartLiveData : CopilotAction

    /** Suggested chip: NHTSA recalls for current VIN. */
    data object CheckRecalls : CopilotAction

    /** Suggested chip: repair-story / customer PDF workflow. */
    data object BuildCustomerReport : CopilotAction

    /** Suggested chip: LAN vehicle-database export to office PC. */
    data object SendDataToPc : CopilotAction

    /** Capability card: immobilizer / key workflow (no PIN extraction). */
    data object OpenSecurityAndKeys : CopilotAction

    /** Capability card: camera OCR for 17-character VIN. */
    data object ScanVinCamera : CopilotAction

    /** Capability card: bundled step-by-step bay workflows. */
    data object OpenGuidedTests : CopilotAction

    /** Capability card: offline bundled DTC dictionary. */
    data object OpenOfflineDtcLookup : CopilotAction

    /** Capability card: on-tablet OEM vehicle data inventory. */
    data object OpenOemDataSummary : CopilotAction

    /** Capability card: CSV / plain-text shop export from last scan. */
    data object OpenShopExport : CopilotAction

    /** Action card: open connection drawer / start connect. */
    data object ConnectUsbObd : CopilotAction

    /** Action card: same as [ScanVehicle]. */
    data object RunObdScan : CopilotAction

    /** Action card: clear stored codes (confirmation handled upstream). */
    data object ClearCodes : CopilotAction

    /** Action card: same as [StartLiveData]. */
    data object OpenLiveData : CopilotAction

    /** Action card: generate AI repair story from last scan. */
    data object GenerateRepairStory : CopilotAction

    /** Action card: share last report. */
    data object ShareReport : CopilotAction

    /** Bottom nav: switch to scanner console home. */
    data object OpenScannerConsole : CopilotAction

    data object OpenHistory : CopilotAction

    data object OpenSettings : CopilotAction

    data object OpenDiagnostics : CopilotAction

    /** Chat IME send — symptom text for the next scan/session. */
    data class SubmitSymptom(val text: String) : CopilotAction
}

/** Labels and gating for a copilot action card or chip. */
data class CopilotActionPresentation(
    val action: CopilotAction,
    val title: String,
    val subtitle: String,
    val enabled: Boolean,
    val disabledReason: String? = null,
)

/**
 * Deterministic availability for AI Copilot capability cards (no network / no AI).
 */
object CopilotActionAvailability {
    private fun oemLinkActive(linkKind: DiagnosticConnector.LinkKind?): Boolean =
        linkKind == DiagnosticConnector.LinkKind.OEM_USB ||
            linkKind == DiagnosticConnector.LinkKind.OEM_BT

    fun capabilityCards(
        vciConnected: Boolean,
        vin: String?,
        dtcCount: Int,
        linkKind: DiagnosticConnector.LinkKind?,
        oemStoreReady: Boolean,
    ): List<CopilotActionPresentation> = listOf(
        securityAndKeys(vin),
        cameraVin(),
        guidedTests(vciConnected, dtcCount),
        offlineDtcLookup(vin, dtcCount),
        oemDataSummary(linkKind, oemStoreReady),
        shopExport(vin, dtcCount),
    )

    private fun securityAndKeys(vin: String?) = CopilotActionPresentation(
        action = CopilotAction.OpenSecurityAndKeys,
        title = "Security & Keys",
        subtitle = if (vin.isNullOrBlank()) {
            "Authorized workflow — VIN optional until you scan"
        } else {
            "Authorized immobilizer workflow — no PIN capture"
        },
        enabled = true,
    )

    private fun cameraVin() = CopilotActionPresentation(
        action = CopilotAction.ScanVinCamera,
        title = "Camera VIN",
        subtitle = "Snap the door-jamb or windshield label",
        enabled = true,
    )

    private fun guidedTests(vciConnected: Boolean, dtcCount: Int): CopilotActionPresentation {
        val enabled = vciConnected || dtcCount > 0
        return CopilotActionPresentation(
            action = CopilotAction.OpenGuidedTests,
            title = "Guided tests",
            subtitle = "Step-by-step misfire, no-start, charging, and more",
            enabled = enabled,
            disabledReason = if (!enabled) "Connect OBD or run a scan for codes first" else null,
        )
    }

    private fun offlineDtcLookup(vin: String?, dtcCount: Int): CopilotActionPresentation {
        val enabled = dtcCount > 0 || !vin.isNullOrBlank()
        return CopilotActionPresentation(
            action = CopilotAction.OpenOfflineDtcLookup,
            title = "Offline DTC lookup",
            subtitle = "Bundled code definitions — no network",
            enabled = enabled,
            disabledReason = when {
                enabled -> null
                else -> "No VIN or DTCs — scan or capture a VIN first"
            },
        )
    }

    private fun oemDataSummary(
        linkKind: DiagnosticConnector.LinkKind?,
        oemStoreReady: Boolean,
    ): CopilotActionPresentation {
        val enabled = oemLinkActive(linkKind) || oemStoreReady
        return CopilotActionPresentation(
            action = CopilotAction.OpenOemDataSummary,
            title = "OEM data summary",
            subtitle = "Vehicle database inventory on this tablet",
            enabled = enabled,
            disabledReason = if (!enabled) "OEM link or on-tablet vehicle data required" else null,
        )
    }

    private fun shopExport(vin: String?, dtcCount: Int): CopilotActionPresentation {
        val reason = when {
            vin.isNullOrBlank() && dtcCount == 0 -> "No VIN and no DTCs — run a scan first"
            vin.isNullOrBlank() -> "No VIN on file"
            dtcCount == 0 -> "No DTCs — run a scan first"
            else -> null
        }
        return CopilotActionPresentation(
            action = CopilotAction.OpenShopExport,
            title = "Shop export",
            subtitle = "CSV or plain text for your shop system",
            enabled = reason == null,
            disabledReason = reason,
        )
    }
}
