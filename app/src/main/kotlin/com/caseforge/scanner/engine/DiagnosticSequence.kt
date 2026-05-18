package com.caseforge.scanner.engine

data class DiagnosticSequence(
    val name: String,
    val description: String,
    val steps: List<SequenceStep>,
    val totalDurationMinutes: Int,
    val sequenceId: String,
    val sequenceCategory: CapabilityMap.Category = CapabilityMap.Category.Sequences,
    val sequencePath: List<String> = emptyList(),
    val sequenceDoneWhen: String? = null,
    val sequenceTimeoutSec: Int = totalDurationMinutes * 60,
    val sequenceOemScope: Set<String> = emptySet(),
) : CapabilityMap.Capability(
    id = sequenceId,
    label = name,
    category = sequenceCategory,
    path = sequencePath,
    doneWhen = sequenceDoneWhen,
    timeoutSec = sequenceTimeoutSec,
    oemScope = sequenceOemScope,
    note = description,
)

data class SequenceStep(
    val title: String,
    val instruction: String,
    val action: SequenceAction,
    val durationSeconds: Int = 0,
    val resultKey: String = title.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_'),
)

sealed class SequenceAction {
    data class RunCapability(val capabilityId: String) : SequenceAction()
    data class Actuate(val testId: String) : SequenceAction()
    data class ReadLiveData(val pids: List<String>) : SequenceAction()
    data class Wait(val seconds: Int) : SequenceAction()
    data class Prompt(val expectedResult: String) : SequenceAction()
}
