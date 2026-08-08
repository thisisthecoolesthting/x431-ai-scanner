package com.caseforge.scanner.agent.accessibility

import kotlinx.serialization.Serializable

/** Config-driven Launch/X431 tap macros for approved bidirectional tests. */
@Serializable
data class AccessibilityBidiConfig(
    val schemaVersion: Int = 1,
    val assetId: String = "accessibility-bidi-v1",
    val defaultInterStepDelayMs: Long = 900L,
    val tests: Map<String, AccessibilityBidiTest> = emptyMap(),
    val aliases: Map<String, String> = emptyMap(),
)

@Serializable
data class AccessibilityBidiTest(
    val label: String = "",
    val description: String = "",
    val steps: List<AccessibilityBidiStep> = emptyList(),
    val doneWhen: String? = null,
    val timeoutSec: Int = 120,
    val interStepDelayMs: Long? = null,
)

/**
 * One accessibility action. Supported [action] values:
 * bring_oem_front, tap, tap_at, type, scroll, back, wait, wait_for.
 */
@Serializable
data class AccessibilityBidiStep(
    val action: String,
    val text: String? = null,
    val exact: Boolean = false,
    val x: Int? = null,
    val y: Int? = null,
    val direction: String? = null,
    val timeoutMs: Long? = null,
    val delayMs: Long? = null,
    val target: String? = null,
    val value: String? = null,
)
