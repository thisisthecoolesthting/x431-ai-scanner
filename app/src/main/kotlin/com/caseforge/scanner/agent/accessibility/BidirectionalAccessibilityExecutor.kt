package com.caseforge.scanner.agent.accessibility

import android.content.Context
import com.caseforge.scanner.agent.AgentActionLog
import com.caseforge.scanner.agent.ScannerAccessibilityService
import com.caseforge.scanner.agent.session.LaunchAccessibilityGoldenPath
import kotlinx.coroutines.delay

/**
 * After [com.caseforge.scanner.agent.PendingActionQueue] approval, walks config-driven
 * accessibility steps through Launch/X431 for a named bidirectional test.
 */
object BidirectionalAccessibilityExecutor {

    data class ExecuteResult(
        val testId: String,
        val success: Boolean,
        val stepsCompleted: Int,
        val totalSteps: Int,
        val log: List<String>,
        val dtcAfter: LaunchAccessibilityGoldenPath.Snapshot?,
        val error: String? = null,
    ) {
        fun toAgentText(): String = buildString {
            append(if (success) "executed" else "failed")
            append(": test=$testId steps=$stepsCompleted/$totalSteps")
            dtcAfter?.let { append("\ndtc_golden: ${LaunchAccessibilityGoldenPath.formatSnapshot(it)}") }
            error?.let { append("\nerror: $it") }
            if (log.isNotEmpty()) {
                append("\n---\n")
                append(log.joinToString("\n"))
            }
        }.trim()
    }

    suspend fun execute(
        context: Context,
        a11y: ScannerAccessibilityService,
        testId: String,
        log: AgentActionLog,
    ): ExecuteResult {
        val config = AccessibilityBidiLoader.load(context)
            ?: return fail(testId, 0, 0, emptyList(), null, "accessibility-bidi config missing")

        val resolvedId = AccessibilityBidiLoader.resolveTestId(config, testId)
        val test = config.tests[resolvedId]
            ?: return fail(
                testId,
                0,
                0,
                emptyList(),
                null,
                "unknown test '$testId' (resolved='$resolvedId') — add to ${
                    AccessibilityBidiLoader.ASSET_PATH
                }",
            )

        val steps = test.steps
        if (steps.isEmpty()) {
            return fail(resolvedId, 0, 0, emptyList(), null, "test has no steps")
        }

        val stepLog = mutableListOf<String>()
        val interStepMs = test.interStepDelayMs ?: config.defaultInterStepDelayMs

        log.event("bidi_a11y.start", "test=$resolvedId steps=${steps.size}")

        var completed = 0
        for ((idx, step) in steps.withIndex()) {
            val ok = runStep(a11y, step, log, resolvedId, idx, stepLog)
            if (!ok) {
                log.event("bidi_a11y.step_fail", "test=$resolvedId step=$idx action=${step.action}")
                return fail(
                    resolvedId,
                    completed,
                    steps.size,
                    stepLog,
                    LaunchAccessibilityGoldenPath.readLatestDtcSnapshotOrNull(context),
                    "step $idx (${step.action}) failed",
                )
            }
            completed++
            if (idx < steps.lastIndex) {
                delay(interStepMs)
            }
        }

        val doneMarker = test.doneWhen?.trim().orEmpty()
        if (doneMarker.isNotEmpty()) {
            val timeoutMs = test.timeoutSec.coerceAtLeast(1) * 1_000L
            val found = a11y.waitFor(doneMarker, timeoutMs)
            stepLog.add("done_when '$doneMarker': ${if (found) "found" else "timeout"}")
            if (!found) {
                log.event("bidi_a11y.done_timeout", "test=$resolvedId marker=$doneMarker")
                return fail(
                    resolvedId,
                    completed,
                    steps.size,
                    stepLog,
                    LaunchAccessibilityGoldenPath.readLatestDtcSnapshotOrNull(context),
                    "done_when timeout: $doneMarker",
                )
            }
        }

        val dtc = LaunchAccessibilityGoldenPath.readLatestDtcSnapshotOrNull(context)
        log.event("bidi_a11y.ok", "test=$resolvedId steps=$completed")
        return ExecuteResult(
            testId = resolvedId,
            success = true,
            stepsCompleted = completed,
            totalSteps = steps.size,
            log = stepLog,
            dtcAfter = dtc,
            error = null,
        )
    }

    private suspend fun runStep(
        a11y: ScannerAccessibilityService,
        step: AccessibilityBidiStep,
        log: AgentActionLog,
        testId: String,
        stepIdx: Int,
        stepLog: MutableList<String>,
    ): Boolean {
        val action = step.action.trim().lowercase()
        val ok = when (action) {
            "bring_oem_front" -> {
                val pkg = a11y.bringOemDiagToFront()
                stepLog.add("bring_oem_front -> ${pkg ?: "none"}")
                pkg != null
            }
            "tap" -> {
                val text = step.text?.trim().orEmpty()
                if (text.isEmpty()) {
                    stepLog.add("tap: missing text")
                    false
                } else {
                    val tapped = a11y.tapByText(text, step.exact)
                    stepLog.add("tap '$text' -> $tapped")
                    tapped
                }
            }
            "tap_at" -> {
                val x = step.x
                val y = step.y
                if (x == null || y == null) {
                    stepLog.add("tap_at: missing x/y")
                    false
                } else {
                    val tapped = a11y.tapAt(x, y)
                    stepLog.add("tap_at ($x,$y) -> $tapped")
                    tapped
                }
            }
            "type" -> {
                val value = step.value.orEmpty()
                val typed = a11y.typeInto(step.target, value)
                stepLog.add("type -> $typed")
                typed
            }
            "scroll" -> {
                val dir = step.direction ?: "down"
                val scrolled = a11y.scroll(dir)
                stepLog.add("scroll $dir -> $scrolled")
                scrolled
            }
            "back" -> {
                val backed = a11y.back()
                stepLog.add("back -> $backed")
                backed
            }
            "wait" -> {
                val ms = step.delayMs ?: step.timeoutMs ?: 1_000L
                delay(ms)
                stepLog.add("wait ${ms}ms")
                true
            }
            "wait_for" -> {
                val text = step.text?.trim().orEmpty()
                if (text.isEmpty()) {
                    stepLog.add("wait_for: missing text")
                    false
                } else {
                    val timeout = step.timeoutMs ?: 8_000L
                    val found = a11y.waitFor(text, timeout)
                    stepLog.add("wait_for '$text' -> ${if (found) "found" else "timeout"}")
                    found
                }
            }
            else -> {
                stepLog.add("unknown action: $action")
                false
            }
        }
        log.event(
            "bidi_a11y.step",
            "test=$testId step=$stepIdx action=$action ok=$ok",
        )
        return ok
    }

    private fun fail(
        testId: String,
        completed: Int,
        total: Int,
        log: List<String>,
        dtc: LaunchAccessibilityGoldenPath.Snapshot?,
        error: String,
    ) = ExecuteResult(
        testId = testId,
        success = false,
        stepsCompleted = completed,
        totalSteps = total,
        log = log,
        dtcAfter = dtc,
        error = error,
    )
}
