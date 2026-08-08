package com.caseforge.scanner.agent

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

/**
 * Lets the agent pause for human approval before running a bidirectional / write action.
 *
 * Flow:
 *  - AgentRunner calls [request] with a human-readable description.
 *  - request() registers a [PendingAction] in the queue and SUSPENDS until either:
 *      * the user taps Approve/Deny on the Pending Approvals screen (or the notification), or
 *      * the timeout elapses (default 60s → auto-deny).
 *  - request() returns true (approved) or false (denied/timeout).
 *
 * The queue is a process-level singleton so the UI and the agent share state without DI plumbing.
 */
object PendingActionQueue {

    data class PendingAction(
        val id: String,
        val description: String,
        val tool: String,
        val args: String,
    )

    private val _items = MutableStateFlow<List<PendingAction>>(emptyList())
    val items: StateFlow<List<PendingAction>> = _items.asStateFlow()

    private val waiters = mutableMapOf<String, CompletableDeferred<Boolean>>()

    suspend fun request(
        tool: String,
        args: String,
        description: String,
        timeoutMs: Long = 60_000L,
    ): Boolean {
        val id = UUID.randomUUID().toString()
        val deferred = CompletableDeferred<Boolean>()
        synchronized(this) {
            waiters[id] = deferred
            _items.value = _items.value + PendingAction(id, description, tool, args)
        }
        val ok = withTimeoutOrNull(timeoutMs) { deferred.await() } ?: false
        cleanup(id)
        return ok
    }

    fun approve(id: String) {
        synchronized(this) {
            waiters.remove(id)?.complete(true)
            _items.value = _items.value.filterNot { it.id == id }
        }
    }

    fun deny(id: String) {
        synchronized(this) {
            waiters.remove(id)?.complete(false)
            _items.value = _items.value.filterNot { it.id == id }
        }
    }

    private fun cleanup(id: String) {
        synchronized(this) {
            waiters.remove(id)
            _items.value = _items.value.filterNot { it.id == id }
        }
    }
}
