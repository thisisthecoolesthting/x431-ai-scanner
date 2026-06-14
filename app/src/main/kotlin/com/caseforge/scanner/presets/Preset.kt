package com.caseforge.scanner.presets

/**
 * A single step in a preset diagnostic job.
 * Each variant carries a user-visible [displayName] and an optional [description].
 */
sealed class PresetStep(val displayName: String, val description: String = "") {
    object ReadCodes       : PresetStep("Read Codes",         "Read stored + pending DTCs")
    object ReadReadiness   : PresetStep("I/M Readiness",      "Check monitor readiness + MIL status")
    object ReadBattery     : PresetStep("Battery Voltage",    "Read battery voltage via ATRV")
    object LiveSnapshot    : PresetStep("Live Snapshot",      "Capture RPM, coolant, speed once")
    object ReadVin         : PresetStep("Read VIN",           "Decode the vehicle VIN from Mode 09")
    object ClearCodes      : PresetStep("Clear Codes",        "Erase stored fault codes (Mode 04)")
    object ReScanVerify    : PresetStep("Re-Scan & Verify",   "Re-read codes after clear to confirm")
    object AiDiagnose      : PresetStep("AI Diagnosis",       "Hand off to AI analysis screen")
    object RoadTestCapture : PresetStep("Road-Test Capture",  "Open road-test live-data screen")
    object BuildReport     : PresetStep("Build Report",       "Open report builder screen")
}

/**
 * A named, ordered bundle of [PresetStep]s that the user can run with one tap.
 */
data class Preset(
    val id: String,
    val title: String,
    val subtitle: String,
    val steps: List<PresetStep>,
)

/**
 * Built-in preset catalog.  Add or reorder entries here; IDs are stable references.
 */
object PresetCatalog {

    val quickCheck: Preset = Preset(
        id       = "quick_check",
        title    = "Quick Check",
        subtitle = "Codes + readiness + battery in ~60 s",
        steps    = listOf(PresetStep.ReadCodes, PresetStep.ReadReadiness, PresetStep.ReadBattery),
    )

    val fullDiagnosis: Preset = Preset(
        id       = "full_diagnosis",
        title    = "Full Diagnosis",
        subtitle = "Complete scan with AI analysis and report",
        steps    = listOf(
            PresetStep.ReadCodes,
            PresetStep.ReadReadiness,
            PresetStep.LiveSnapshot,
            PresetStep.AiDiagnose,
            PresetStep.BuildReport,
        ),
    )

    val prePurchase: Preset = Preset(
        id       = "pre_purchase",
        title    = "Pre-Purchase Inspection",
        subtitle = "VIN + codes + readiness + live snapshot + report",
        steps    = listOf(
            PresetStep.ReadVin,
            PresetStep.ReadCodes,
            PresetStep.ReadReadiness,
            PresetStep.LiveSnapshot,
            PresetStep.BuildReport,
        ),
    )

    val clearVerify: Preset = Preset(
        id       = "clear_verify",
        title    = "Clear & Verify",
        subtitle = "Erase codes then re-scan to confirm they are gone",
        steps    = listOf(PresetStep.ClearCodes, PresetStep.ReScanVerify),
    )

    val roadTest: Preset = Preset(
        id       = "road_test",
        title    = "Road-Test / Live Capture",
        subtitle = "Open live-data capture for a drive cycle",
        steps    = listOf(PresetStep.RoadTestCapture),
    )

    /** All presets in display order. */
    val all: List<Preset> = listOf(
        quickCheck,
        fullDiagnosis,
        prePurchase,
        clearVerify,
        roadTest,
    )

    /** Look up a preset by its stable ID. Returns null if not found. */
    fun byId(id: String): Preset? = all.firstOrNull { it.id == id }
}
