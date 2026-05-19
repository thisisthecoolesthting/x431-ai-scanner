package com.caseforge.scanner.diagnostics

/**
 * Static guided-test plans for common bay workflows. No UI or network — consumed by overlay/report lanes.
 */
enum class GuidedTransportRequirement {
    /** Generic OBD-II (ELM327 USB/BT) is sufficient. */
    ELM327,

    /** OEM VCI / full OEM diagnostic path required for actuation or module depth. */
    OEM,

    /** Plan runs on ELM; OEM improves actuation-heavy steps but is not mandatory. */
    ANY,
}

enum class IgnitionRequirement {
    /** Key off; doors latched if testing draw. */
    KEY_OFF,

    /** Key on, engine not running. */
    KEY_ON_ENGINE_OFF,

    /** Engine at idle or held test RPM. */
    ENGINE_RUNNING,

    /** Key on first, then engine running for later steps. */
    KEY_ON_THEN_ENGINE_RUNNING,
}

data class GuidedTestPreconditions(
    val ignition: IgnitionRequirement,
    val notes: List<String> = emptyList(),
)

data class GuidedTestStep(
    val title: String,
    val instruction: String,
    val expectedObservation: String,
)

/**
 * Shop-facing guided plan: symptom aliases, preconditions, transport, steps, and report phrasing.
 */
data class GuidedTest(
    val id: String,
    val title: String,
    val symptomAliases: List<String>,
    val preconditions: GuidedTestPreconditions,
    val requiredTransport: GuidedTransportRequirement,
    val steps: List<GuidedTestStep>,
    val relatedPids: List<String>,
    val relatedDtcPrefixes: List<String>,
    /** Wording inserted into session/repair reports when this plan is selected. */
    val reportWording: String,
)

/** Ranked planner output for UI or agent consumption. */
data class GuidedTestMatch(
    val test: GuidedTest,
    val score: Int,
    val matchedAliases: List<String>,
    val matchedDtcPrefixes: List<String>,
)
