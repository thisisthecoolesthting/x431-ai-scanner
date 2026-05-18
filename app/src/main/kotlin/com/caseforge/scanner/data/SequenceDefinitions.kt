package com.caseforge.scanner.data

import com.caseforge.scanner.engine.CapabilityMap
import com.caseforge.scanner.engine.sequences.CommonSequences
import com.caseforge.scanner.engine.sequences.TestSequence

/** Registry of built-in multi-step diagnostic sequences (F7). */
object SequenceDefinitions {

    private val sequences: List<TestSequence> = listOf(
        CommonSequences.relativeCompression(),
        CommonSequences.evapSmokeTest(),
        CommonSequences.injectorKillSweep(),
        CommonSequences.vvtSolenoidSweep(),
        CommonSequences.parasiticDrawBisection(),
    )

    fun all(): List<TestSequence> = sequences

    fun byId(id: String): TestSequence? = sequences.firstOrNull { it.id == id }

    fun asCapabilities(): List<CapabilityMap.Capability> = sequences.map { seq ->
        CapabilityMap.Capability(
            id = seq.id,
            label = seq.label,
            category = CapabilityMap.Category.Sequences,
            path = emptyList(),
            doneWhen = null,
            timeoutSec = (seq.timeout / 1_000L).toInt().coerceIn(60, 3_600),
            note = seq.description,
        )
    }
}
