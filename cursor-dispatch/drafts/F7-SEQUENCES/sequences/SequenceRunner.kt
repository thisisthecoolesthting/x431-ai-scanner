package com.caseforge.scanner.engine.sequences

import com.caseforge.scanner.engine.EngineDriver
import com.caseforge.scanner.engine.CapabilityResult
import kotlinx.coroutines.delay
import java.time.Instant
import kotlin.math.abs

/**
 * Executes a test sequence step-by-step with live data capture and branch evaluation.
 */
class SequenceRunner(private val driver: EngineDriver) {

    suspend fun run(seq: TestSequence): SequenceResult {
        val startTime = Instant.now().toEpochMilli()
        val stepResults = mutableListOf<StepResult>()
        val capturedValues = mutableMapOf<String, String>()
        var passed = true
        var errorMsg: String? = null

        try {
            for (step in seq.steps) {
                val stepStart = Instant.now().toEpochMilli()
                
                try {
                    val result = executeStep(step, capturedValues)
                    stepResults.add(result)
                    
                    if (!result.passed) {
                        passed = false
                        // Continue execution to capture all diagnostics
                    } else {
                        capturedValues.putAll(result.capturedValues)
                    }
                } catch (e: Exception) {
                    passed = false
                    errorMsg = "Step '${step.label}' failed: ${e.message}"
                    stepResults.add(
                        StepResult(
                            step = step,
                            passed = false,
                            output = e.message ?: "Unknown error",
                            duration = Instant.now().toEpochMilli() - stepStart
                        )
                    )
                    break  // Stop on exception
                }
            }
        } catch (e: Exception) {
            passed = false
            errorMsg = "Sequence execution interrupted: ${e.message}"
        }

        val totalDuration = Instant.now().toEpochMilli() - startTime

        return SequenceResult(
            sequenceId = seq.id,
            passed = passed && errorMsg == null,
            stepResults = stepResults,
            totalDuration = totalDuration,
            errorMessage = errorMsg
        )
    }

    private suspend fun executeStep(
        step: Step,
        capturedValues: Map<String, String>
    ): StepResult {
        val stepStart = Instant.now().toEpochMilli()

        return when (step) {
            is RunCapability -> executeRunCapability(step, stepStart)
            is Wait -> executeWait(step, stepStart)
            is CapturePid -> executeCapturePid(step, stepStart)
            is Prompt -> executePrompt(step, stepStart)
            is Branch -> executeBranch(step, capturedValues, stepStart)
        }
    }

    private suspend fun executeRunCapability(
        step: RunCapability,
        stepStart: Long
    ): StepResult {
        val result = driver.runCapability(step.capabilityId, step.params)
        
        return StepResult(
            step = step,
            passed = result.success,
            output = result.output,
            capturedValues = mapOf(step.capabilityId to result.output),
            duration = Instant.now().toEpochMilli() - stepStart
        )
    }

    private suspend fun executeWait(
        step: Wait,
        stepStart: Long
    ): StepResult {
        delay((step.seconds * 1000).toLong())
        
        return StepResult(
            step = step,
            passed = true,
            output = "Waited ${step.seconds}s",
            duration = Instant.now().toEpochMilli() - stepStart
        )
    }

    private suspend fun executeCapturePid(
        step: CapturePid,
        stepStart: Long
    ): StepResult {
        val pidValue = driver.queryPid(step.pid)
        
        return StepResult(
            step = step,
            passed = pidValue != null,
            output = pidValue ?: "PID read failed",
            capturedValues = if (pidValue != null) {
                mapOf(step.storageKey to pidValue)
            } else emptyMap(),
            duration = Instant.now().toEpochMilli() - stepStart
        )
    }

    private suspend fun executePrompt(
        step: Prompt,
        stepStart: Long
    ): StepResult {
        // In real implementation, emit UI event; here we simulate acknowledgment
        driver.notifyUser(step.message)
        
        return StepResult(
            step = step,
            passed = true,
            output = "User acknowledged: ${step.message}",
            duration = Instant.now().toEpochMilli() - stepStart
        )
    }

    private suspend fun executeBranch(
        step: Branch,
        capturedValues: Map<String, String>,
        stepStart: Long
    ): StepResult {
        val conditionMet = evaluateCondition(step.condition, capturedValues)
        val stepsToRun = if (conditionMet) step.ifTrue else step.ifFalse

        var allPassed = true
        val outputs = mutableListOf<String>()

        for (substep in stepsToRun) {
            val subResult = executeStep(substep, capturedValues)
            if (!subResult.passed) allPassed = false
            outputs.add("${substep.label}: ${subResult.output}")
        }

        return StepResult(
            step = step,
            passed = allPassed,
            output = "Branch ${if (conditionMet) "TRUE" else "FALSE"}: ${outputs.joinToString(" | ")}",
            duration = Instant.now().toEpochMilli() - stepStart
        )
    }

    private fun evaluateCondition(condition: String, values: Map<String, String>): Boolean {
        // Simple expression evaluator: "rpm_delta_1 > 150", "evap_pressure < 2.0", etc.
        val parts = condition.trim().split(Regex("\\s+(>|<|>=|<=|==|!=)\\s+"))
        
        if (parts.size < 3) return false

        val varName = parts[0]
        val operator = parts[1]
        val threshold = parts[2]

        val varValue = values[varName]?.toDoubleOrNull() ?: return false
        val thresholdValue = threshold.toDoubleOrNull() ?: return false

        return when (operator) {
            ">" -> varValue > thresholdValue
            "<" -> varValue < thresholdValue
            ">=" -> varValue >= thresholdValue
            "<=" -> varValue <= thresholdValue
            "==" -> abs(varValue - thresholdValue) < 0.01
            "!=" -> abs(varValue - thresholdValue) >= 0.01
            else -> false
        }
    }
}
