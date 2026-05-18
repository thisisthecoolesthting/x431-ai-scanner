package com.caseforge.scanner.agent

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Process-wide running commentary of what the agent is doing right now.
 * AgentRunner publishes; OverlayService and any UI can observe.
 */
object AgentStatus {
    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    private val _step = MutableStateFlow(0)
    val step: StateFlow<Int> = _step.asStateFlow()

    private val _activity = MutableStateFlow("Idle")
    val activity: StateFlow<String> = _activity.asStateFlow()

    /** Last action line from [AgentActionLog] tail (detail only, max 60 chars). */
    private val _lastAction = MutableStateFlow<String?>(null)
    val lastAction: StateFlow<String?> = _lastAction.asStateFlow()

    private var actionLogJob: Job? = null

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

    /**
     * Polls [AgentActionLog] tail — log stays append-only; we only read and re-emit.
     */
    fun startObservingActionLog(log: AgentActionLog, scope: CoroutineScope) {
        actionLogJob?.cancel()
        actionLogJob = scope.launch(Dispatchers.IO) {
            var prev = ""
            while (isActive) {
                val line = log.tail(1).lastOrNull().orEmpty()
                if (line.isNotBlank() && line != prev) {
                    prev = line
                    val parts = line.split('\t')
                    val detail = when {
                        parts.size >= 3 -> parts[2]
                        parts.size == 2 -> parts[1]
                        else -> line
                    }
                    _lastAction.value = detail.take(60)
                }
                delay(400)
            }
        }
    }
}
