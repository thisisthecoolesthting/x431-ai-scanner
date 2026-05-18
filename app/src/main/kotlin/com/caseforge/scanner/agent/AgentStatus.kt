package com.caseforge.scanner.agent

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-wide running commentary of what the agent is doing right now.
 * AgentRunner publishes; OverlayService and any UI can observe.
 *
 * Status is a single short string ("Step 5: tap 'Engine'", "Claude thinking…", "Idle").
 * Step is the current loop iteration (0 when idle).
 */
object AgentStatus {
    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    private val _step = MutableStateFlow(0)
    val step: StateFlow<Int> = _step.asStateFlow()

    private val _activity = MutableStateFlow("Idle")
    val activity: StateFlow<String> = _activity.asStateFlow()

    fun begin() {
        _running.value = true
        _step.value = 0
        _activity.value = "Starting…"
    }

    fun setStep(n: Int) { _step.value = n }

    fun setActivity(text: String) { _activity.value = text }

    fun end(reason: String) {
        _running.value = false
        _activity.value = "Done: ${reason.take(120)}"
    }
}
