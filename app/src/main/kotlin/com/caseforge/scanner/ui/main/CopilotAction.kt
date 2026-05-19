package com.caseforge.scanner.ui.main

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
