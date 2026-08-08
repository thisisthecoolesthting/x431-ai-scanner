package com.caseforge.scanner.data

import com.caseforge.scanner.engine.CapabilityMap
import com.caseforge.scanner.engine.DiagnosticSequence

/**
 * Registry of the five built-in multi-step diagnostic sequences shown in the Sequences tab.
 */
object SequenceDefinitions {
    val ALL: List<DiagnosticSequence> = com.caseforge.scanner.engine.SequenceDefinitions.ALL

    fun asCapabilities(): List<CapabilityMap.Capability> = ALL
}
