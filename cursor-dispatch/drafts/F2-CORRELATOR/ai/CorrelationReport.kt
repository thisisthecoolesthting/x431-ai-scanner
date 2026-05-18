package com.caseforge.scanner.ai

import com.caseforge.scanner.engine.Dtc

// ---------------------------------------------------------------------------
// F2 — Cross-Module Fault Correlation: data model
// ---------------------------------------------------------------------------

/**
 * Top-level result returned by [DtcCorrelator.correlate].
 *
 * @param groups      Ranked list of root-cause groups (highest confidence first).
 * @param generatedAtMs  Wall-clock timestamp (System.currentTimeMillis()) when Claude
 *                       responded. Useful for cache invalidation and UI "as of" labels.
 */
data class CorrelationReport(
    val groups: List<RootCauseGroup>,
    val generatedAtMs: Long,
) {
    /** Convenience: all DTCs that appear in at least one group. */
    val coveredDtcs: List<Dtc>
        get() = groups.flatMap { it.supportingDtcs }.distinctBy { it.code + it.module }

    /** True when Claude found nothing actionable — no groups, or all groups are low-confidence. */
    val isEmpty: Boolean
        get() = groups.isEmpty()
}

/**
 * A single root-cause cluster identified by Claude.
 *
 * @param rootCause          One-sentence root cause. E.g. "Faulty engine coolant
 *                           thermostat causing CAN-bus ghost codes".
 * @param supportingDtcs     All DTCs — from any module — that belong to this cause.
 *                           Ordered by diagnostic relevance (primary fault first).
 * @param confidence         Model's self-reported confidence in [0.0, 1.0].
 * @param recommendedAction  Next diagnostic step in plain technician language.
 * @param capabilityHint     Optional capability ID (from CapabilityRegistry) that the
 *                           technician should run to confirm or clear this group.
 *                           Null when no specific capability applies.
 */
data class RootCauseGroup(
    val rootCause: String,
    val supportingDtcs: List<Dtc>,
    val confidence: Float,
    val recommendedAction: String,
    val capabilityHint: String?,
) {
    init {
        require(confidence in 0f..1f) {
            "confidence must be in [0.0, 1.0], got $confidence"
        }
    }

    /** Human-readable confidence tier for UI badges. */
    val confidenceTier: ConfidenceTier
        get() = when {
            confidence >= 0.75f -> ConfidenceTier.High
            confidence >= 0.45f -> ConfidenceTier.Medium
            else               -> ConfidenceTier.Low
        }
}

enum class ConfidenceTier { High, Medium, Low }
