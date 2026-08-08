package com.caseforge.scanner.agent

import android.content.Context
import com.caseforge.scanner.agent.accessibility.AccessibilityBidiLoader
import com.caseforge.scanner.agent.accessibility.BidirectionalAccessibilityExecutor
import com.caseforge.scanner.engine.ActuationResult
import com.caseforge.scanner.engine.VciDiagnosticPort
import com.caseforge.scanner.transfer.SessionEventLogger

/**
 * Process-level hook so hosts with an active VCI link (overlay, diagnostics screen)
 * can register actuation without threading through [AgentRunner] construction.
 */
object PendingActionQueueBridge {
    @Volatile
    var actuation: VciActuationBridge? = null
}

/** Test seam for approved bidirectional tests routed to [VciCommunicator.actuate]. */
interface VciActuationBridge {
    fun isAvailable(): Boolean
    suspend fun actuate(testId: String): Result<ActuationResult>
}

class VciDiagnosticPortActuationBridge(
    private val port: VciDiagnosticPort,
) : VciActuationBridge {
    override fun isAvailable(): Boolean = true

    override suspend fun actuate(testId: String): Result<ActuationResult> = port.actuate(testId)
}

/**
 * Runs VCI actuation after [PendingActionQueue] (or autonomy) approves [propose_actuation].
 */
object PendingActionActuation {

    const val FALLBACK_UNSUPPORTED =
        "VCI actuation is not connected — use the OEM diagnostic app to run this bidirectional test manually."

    suspend fun executeAfterApproval(
        context: Context,
        bridge: VciActuationBridge?,
        testId: String,
        description: String,
        sessionId: String?,
        log: AgentActionLog,
    ): String {
        val effective = bridge?.takeIf { it.isAvailable() }
        if (effective == null) {
            tryAccessibilityFallback(context, testId, description, sessionId, log)?.let { return it }
            logSession(context, sessionId, "actuation_unavailable", description, testId)
            log.event("propose_actuation.unsupported", testId)
            return "approved — $FALLBACK_UNSUPPORTED"
        }

        return effective.actuate(testId).fold(
            onSuccess = { result ->
                if (!result.success) {
                    tryAccessibilityFallback(context, testId, description, sessionId, log)?.let { return it }
                }
                val detail = buildString {
                    append("test=$testId success=${result.success}")
                    if (result.log.isNotEmpty()) {
                        append(" log=${result.log.joinToString("; ").take(180)}")
                    }
                }
                logSession(
                    context,
                    sessionId,
                    if (result.success) "actuation_ok" else "actuation_failed",
                    detail,
                    testId,
                    mapOf("success" to result.success.toString()),
                )
                log.event("propose_actuation.executed", detail)
                when {
                    result.success ->
                        "approved and executed: $testId" +
                            (result.log.firstOrNull()?.let { " — $it" } ?: "")
                    else ->
                        "approved but actuation failed" +
                            (result.log.firstOrNull()?.let { ": $it" } ?: "") +
                            ". $FALLBACK_UNSUPPORTED"
                }
            },
            onFailure = { err ->
                tryAccessibilityFallback(context, testId, description, sessionId, log)?.let { return it }
                val msg = err.message?.take(120) ?: err.javaClass.simpleName
                logSession(context, sessionId, "actuation_error", msg, testId)
                log.event("propose_actuation.error", msg)
                "approved but actuation error: $msg. $FALLBACK_UNSUPPORTED"
            },
        )
    }

    /**
     * Plan A fallback: config-driven Launch/X431 taps when VCI actuation is unavailable or fails.
     */
    private suspend fun tryAccessibilityFallback(
        context: Context,
        testId: String,
        description: String,
        sessionId: String?,
        log: AgentActionLog,
    ): String? {
        val a11y = ScannerAccessibilityService.instance() ?: return null
        val config = AccessibilityBidiLoader.load(context) ?: return null
        val resolved = AccessibilityBidiLoader.resolveTestId(config, testId)
        if (resolved !in config.tests) return null

        val exec = BidirectionalAccessibilityExecutor.execute(
            context = context,
            a11y = a11y,
            testId = testId,
            log = log,
        )
        val detail = exec.toAgentText()
        logSession(
            context,
            sessionId,
            if (exec.success) "actuation_a11y_ok" else "actuation_a11y_failed",
            detail.take(300),
            testId,
            mapOf("success" to exec.success.toString(), "path" to "accessibility-bidi"),
        )
        log.event(
            if (exec.success) "propose_actuation.a11y_ok" else "propose_actuation.a11y_fail",
            "${description.take(80)} test=$resolved steps=${exec.stepsCompleted}/${exec.totalSteps}",
        )
        return if (exec.success) {
            "approved and executed via accessibility: $detail"
        } else {
            "approved but accessibility macro failed: $detail. $FALLBACK_UNSUPPORTED"
        }
    }

    private fun logSession(
        context: Context,
        sessionId: String?,
        kind: String,
        detail: String,
        testId: String,
        extra: Map<String, String> = emptyMap(),
    ) {
        val sid = sessionId ?: return
        SessionEventLogger.log(
            context,
            sid,
            kind,
            detail = detail.take(300),
            extra = extra + mapOf("testId" to testId),
        )
    }
}
