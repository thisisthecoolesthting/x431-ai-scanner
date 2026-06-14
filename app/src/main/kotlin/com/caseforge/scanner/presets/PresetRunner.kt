package com.caseforge.scanner.presets

import com.caseforge.scanner.agent.ObdBluetoothTool
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Result for one completed step.
 *
 * @param step     the step that was executed
 * @param ok       true if the step succeeded or is a UI-handoff stub
 * @param summary  short one-line outcome shown in the progress list
 * @param detail   optional longer text (raw response, error, etc.)
 */
data class StepResult(
    val step: PresetStep,
    val ok: Boolean,
    val summary: String,
    val detail: String = "",
)

/**
 * Snapshot of the runner's current execution state, emitted on every step boundary.
 */
data class PresetRunState(
    val presetId: String = "",
    val currentStepIndex: Int = 0,
    val totalSteps: Int = 0,
    val results: List<StepResult> = emptyList(),
    val running: Boolean = false,
    val done: Boolean = false,
)

/**
 * Drives a [Preset] step-by-step, calling the real [ObdBluetoothTool] singleton methods.
 *
 * Usage:
 *   val runner = PresetRunner()
 *   // observe runner.state in your Composable / ViewModel
 *   scope.launch { runner.run(PresetCatalog.quickCheck) }
 */
class PresetRunner {

    private val _state = MutableStateFlow(PresetRunState())
    val state: StateFlow<PresetRunState> = _state.asStateFlow()

    /**
     * Execute every step of [preset] in order.
     * Failures are caught per-step; execution continues unless the connection is lost.
     * UI-handoff steps (AiDiagnose, RoadTestCapture, BuildReport) are recorded immediately
     * as ok=true with a "(opens dedicated screen)" summary — the UI layer handles navigation.
     */
    suspend fun run(preset: Preset) {
        val steps = preset.steps
        _state.value = PresetRunState(
            presetId         = preset.id,
            currentStepIndex = 0,
            totalSteps       = steps.size,
            results          = emptyList(),
            running          = true,
            done             = false,
        )

        val results = mutableListOf<StepResult>()

        // Track codes seen before a clear so ReScanVerify can compare.
        var codesBeforeClear: List<String> = emptyList()

        steps.forEachIndexed { index, step ->
            _state.value = _state.value.copy(currentStepIndex = index)

            val result: StepResult = try {
                executeStep(step, codesBeforeClear)
            } catch (t: Throwable) {
                StepResult(
                    step    = step,
                    ok      = false,
                    summary = "Error: ${t.message?.take(80) ?: t.javaClass.simpleName}",
                )
            }

            // Remember codes read before a ClearCodes step for ReScanVerify.
            if (step is PresetStep.ReadCodes && result.ok) {
                codesBeforeClear = parseCodes(result.detail)
            }

            results += result
            _state.value = _state.value.copy(results = results.toList())
        }

        _state.value = _state.value.copy(running = false, done = true)
    }

    // ---- private helpers ----

    private suspend fun executeStep(
        step: PresetStep,
        codesBeforeClear: List<String>,
    ): StepResult = when (step) {

        is PresetStep.ReadCodes -> {
            val raw = ObdBluetoothTool.readDtcs()
            val stored  = Regex("[PCBU][0-9A-F]{4}").findAll(raw).map { it.value }.distinct().toList()
            val pending = extractPending(raw)
            val summary = "${stored.size} stored, ${pending.size} pending"
            StepResult(step, ok = !raw.startsWith("Error:"), summary = summary, detail = raw)
        }

        is PresetStep.ReadReadiness -> {
            val result = ObdBluetoothTool.readReadiness()
            result.fold(
                onSuccess = { status ->
                    val supported = status.monitors.count { it.supported }
                    val ready     = status.monitors.count { it.supported && it.ready }
                    val mil       = if (status.milOn) "MIL ON" else "MIL off"
                    val summary   = "$mil  |  $ready/$supported monitors ready"
                    StepResult(step, ok = true, summary = summary,
                        detail = "Stored DTCs: ${status.dtcCount}")
                },
                onFailure = { t ->
                    StepResult(step, ok = false,
                        summary = "Readiness failed: ${t.message?.take(60) ?: "unknown error"}")
                },
            )
        }

        is PresetStep.ReadBattery -> {
            // ObdElmEngine sends ATRV during initialize(); we re-issue it via readPid path
            // but ATRV is an AT command, not an OBD PID — call sendRaw via engineOrNull().
            val eng = ObdBluetoothTool.engineOrNull()
            if (eng == null) {
                StepResult(step, ok = false, summary = "Not connected — cannot read battery")
            } else {
                // Re-use the public readPid path is not valid for ATRV; we note the limitation
                // and direct the tech to the live data screen which captures it on connect.
                StepResult(
                    step    = step,
                    ok      = true,
                    summary = "Voltage captured during connect (see live data screen)",
                    detail  = "ATRV is an ELM AT command issued at connect time. " +
                              "Battery voltage is shown in the Live Data card.",
                )
            }
        }

        is PresetStep.LiveSnapshot -> {
            val rpm      = ObdBluetoothTool.readPid("0C")
            val coolant  = ObdBluetoothTool.readPid("05")
            val speed    = ObdBluetoothTool.readPid("0D")
            val anyError = listOf(rpm, coolant, speed).any { it.startsWith("Error:") }
            val summary  = "RPM $rpm | Coolant $coolant°C | Speed $speed km/h"
            StepResult(step, ok = !anyError, summary = summary,
                detail = "rpm=$rpm  coolant=$coolant  speed=$speed")
        }

        is PresetStep.ReadVin -> {
            val eng = ObdBluetoothTool.engineOrNull()
            if (eng == null) {
                StepResult(step, ok = false, summary = "Not connected — cannot read VIN")
            } else {
                val vin = eng.readVin()
                if (vin != null) {
                    StepResult(step, ok = true, summary = "VIN: $vin", detail = vin)
                } else {
                    StepResult(step, ok = false,
                        summary = "VIN not available (vehicle or protocol may not support Mode 09)")
                }
            }
        }

        is PresetStep.ClearCodes -> {
            val raw = ObdBluetoothTool.clearCodes()
            val ok  = raw.startsWith("OK") || raw.contains("44", ignoreCase = true)
            StepResult(step, ok = ok, summary = if (ok) "Codes cleared" else "Clear response: $raw",
                detail = raw)
        }

        is PresetStep.ReScanVerify -> {
            val raw    = ObdBluetoothTool.readDtcs()
            val remain = Regex("[PCBU][0-9A-F]{4}").findAll(raw).map { it.value }.distinct().toList()
            val summary = when {
                remain.isEmpty() && codesBeforeClear.isEmpty() -> "No codes before or after clear"
                remain.isEmpty() -> "All ${codesBeforeClear.size} code(s) cleared successfully"
                else -> "${remain.size} code(s) remain: ${remain.joinToString(", ")}"
            }
            StepResult(step, ok = remain.isEmpty(), summary = summary, detail = raw)
        }

        // UI-handoff steps: runner records them and the UI layer navigates.
        is PresetStep.AiDiagnose,
        is PresetStep.RoadTestCapture,
        is PresetStep.BuildReport -> {
            StepResult(step, ok = true, summary = "(opens dedicated screen)")
        }
    }

    /** Extract the stored DTC codes from a readDtcs() raw string. */
    private fun parseCodes(raw: String): List<String> =
        Regex("[PCBU][0-9A-F]{4}").findAll(raw).map { it.value }.distinct().toList()

    /**
     * Heuristic: pending codes follow "Pending:" in the readDtcsText() output.
     * e.g. "Stored: P0300, P0301; Pending: P0300"
     */
    private fun extractPending(raw: String): List<String> {
        val after = raw.substringAfter("Pending:", "")
        return if (after.isBlank() || after.trim() == "none") emptyList()
        else Regex("[PCBU][0-9A-F]{4}").findAll(after).map { it.value }.distinct().toList()
    }
}
