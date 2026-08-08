package com.caseforge.scanner.engine.sequences

import kotlinx.serialization.Serializable

/**
 * Automated multi-step test sequence definition.
 * Enables reproducible diagnostic workflows: relative compression, EVAP, parasitic-draw bisection, etc.
 */
@Serializable
data class TestSequence(
    val id: String,
    val label: String,
    val description: String,
    val steps: List<Step>,
    val timeout: Long = 300_000L  // 5 min default
) {
    init {
        require(id.isNotBlank()) { "id must not be blank" }
        require(steps.isNotEmpty()) { "steps must not be empty" }
    }
}

/**
 * Base sealed class for sequence steps.
 */
@Serializable
sealed class Step {
    abstract val label: String
}

/**
 * Run a capability by ID and capture its result.
 * @param capabilityId e.g., "read_rpm", "enable_injector_cutout"
 * @param params optional key-value arguments
 */
@Serializable
data class RunCapability(
    override val label: String,
    val capabilityId: String,
    val params: Map<String, String> = emptyMap()
) : Step()

/**
 * Wait for a specified duration (seconds).
 */
@Serializable
data class Wait(
    override val label: String,
    val seconds: Double
) : Step()

/**
 * Capture a parameter ID (e.g., PID 0x010C for RPM) and store result.
 */
@Serializable
data class CapturePid(
    override val label: String,
    val pid: String,
    val storageKey: String  // reference in later branch conditions
) : Step()

/**
 * Display a prompt to the user and wait for acknowledgment.
 */
@Serializable
data class Prompt(
    override val label: String,
    val message: String,
    val imageUrl: String? = null
) : Step()

/**
 * Conditional branching based on a captured value.
 * Example: "rpm > 1500 ? runCapability(...) : prompt(...)"
 */
@Serializable
data class Branch(
    override val label: String,
    val condition: String,  // e.g., "rpm_delta_1 > 150", "evap_pressure < 2.0"
    val ifTrue: List<Step>,
    val ifFalse: List<Step>
) : Step()

/**
 * Step execution result.
 */
@Serializable
data class StepResult(
    val step: Step,
    val passed: Boolean,
    val output: String,
    val capturedValues: Map<String, String> = emptyMap(),
    val duration: Long  // milliseconds
)

/**
 * Overall sequence execution result.
 */
@Serializable
data class SequenceResult(
    val sequenceId: String,
    val passed: Boolean,
    val stepResults: List<StepResult>,
    val totalDuration: Long,
    val errorMessage: String? = null
) {
    val summary: String
        get() = when {
            passed -> "PASS: All steps executed successfully"
            errorMessage != null -> "FAIL: $errorMessage"
            else -> "FAIL: One or more steps failed"
        }
}
