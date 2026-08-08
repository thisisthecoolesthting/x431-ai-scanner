package com.caseforge.scanner.ai

import com.caseforge.scanner.engine.DtcCorrelator
import com.caseforge.scanner.engine.EngineState

/**
 * Zero-network diagnostic hint when Claude API key is unavailable.
 * Reuses static correlator output as the offline "next step" narrative.
 */
object OfflineDiagFallback {
    fun suggest(state: EngineState): String? {
        val hypothesis = DtcCorrelator.correlate(state.dtcs) ?: return null
        return buildString {
            append("Offline mode: ")
            append(hypothesis.cause)
            if (hypothesis.evidencePoints.isNotEmpty()) {
                append(" — ")
                append(hypothesis.evidencePoints.first())
            }
        }
    }
}
