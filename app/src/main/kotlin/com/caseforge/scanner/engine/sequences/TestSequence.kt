package com.caseforge.scanner.engine.sequences

import kotlinx.serialization.SerialName
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
    val timeout: Long = 300_000L,
) {
    init {
        require(id.isNotBlank()) { "id must not be blank" }
        require(steps.isNotEmpty()) { "steps must not be empty" }
    }
}

/** Base sealed class for sequence steps. */
@Serializable
sealed class Step {
    abstract val label: String
}

@Serializable
@SerialName("run_capability")
data class RunCapability(
    override val label: String,
    val capabilityId: String,
    val params: Map<String, String> = emptyMap(),
) : Step()

@Serializable
@SerialName("wait")
data class Wait(
    override val label: String,
    val seconds: Double,
) : Step()

@Serializable
@SerialName("capture_pid")
data class CapturePid(
    override val label: String,
    val pid: String,
    val storageKey: String,
) : Step()

@Serializable
@SerialName("prompt")
data class Prompt(
    override val label: String,
    val message: String,
    val imageUrl: String? = null,
) : Step()

@Serializable
@SerialName("branch")
data class Branch(
    override val label: String,
    val condition: String,
    val ifTrue: List<Step>,
    val ifFalse: List<Step>,
) : Step()

@Serializable
data class StepResult(
    val step: Step,
    val passed: Boolean,
    val output: String,
    val capturedValues: Map<String, String> = emptyMap(),
    val duration: Long,
)

@Serializable
data class SequenceResult(
    val sequenceId: String,
    val passed: Boolean,
    val stepResults: List<StepResult>,
    val totalDuration: Long,
    val errorMessage: String? = null,
) {
    val summary: String
        get() = when {
            passed -> "PASS: All steps executed successfully"
            errorMessage != null -> "FAIL: $errorMessage"
            else -> "FAIL: One or more steps failed"
        }
}
