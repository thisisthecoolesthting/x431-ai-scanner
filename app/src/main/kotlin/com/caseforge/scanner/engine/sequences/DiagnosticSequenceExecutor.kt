package com.caseforge.scanner.engine.sequences

import com.caseforge.scanner.engine.DiagnosticSequence
import com.caseforge.scanner.engine.SequenceAction
import kotlinx.coroutines.delay

/**
 * Executes [DiagnosticSequence] steps for the overlay UI, suspending on technician prompts.
 */
class DiagnosticSequenceExecutor(
    private val runCapability: suspend (String) -> Boolean,
    private val actuate: suspend (String) -> Boolean,
    private val readLiveData: suspend (List<String>) -> Boolean,
    private val notify: (String) -> Unit,
    private val onProgress: (stepIndex: Int, totalSteps: Int, title: String, awaitingPrompt: Boolean) -> Unit,
    private val awaitUser: suspend () -> Unit,
    private val logStep: (String) -> Unit,
) {
    suspend fun run(sequence: DiagnosticSequence): Boolean {
        val steps = sequence.steps
        if (steps.isEmpty()) return true

        steps.forEachIndexed { index, step ->
            onProgress(index, steps.size, step.title, false)
            logStep("${index + 1}/${steps.size} ${step.title}")

            when (val action = step.action) {
                is SequenceAction.Prompt -> {
                    notify(step.instruction)
                    onProgress(index, steps.size, step.title, true)
                    awaitUser()
                }
                is SequenceAction.Wait -> delay(action.seconds * 1000L)
                is SequenceAction.RunCapability -> {
                    if (!runCapability(action.capabilityId)) return false
                }
                is SequenceAction.Actuate -> {
                    if (!actuate(action.testId)) return false
                }
                is SequenceAction.ReadLiveData -> {
                    if (!readLiveData(action.pids)) return false
                }
            }
        }
        return true
    }
}
