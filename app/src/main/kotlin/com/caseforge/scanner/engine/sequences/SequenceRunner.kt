package com.caseforge.scanner.engine.sequences

import com.caseforge.scanner.engine.EngineDriver
import kotlinx.coroutines.delay
import java.time.Instant
import kotlin.math.abs

class SequenceRunner(
    private val driver: EngineDriver,
    private val onPrompt: suspend (Prompt) -> Unit = { driver.notifyUser(it.message) },
    private val onStepFinished: (Int, Int, StepResult) -> Unit = { _, _, _ -> },
) {
    suspend fun run(seq: TestSequence): SequenceResult {
        val t0 = Instant.now().toEpochMilli()
        val results = mutableListOf<StepResult>()
        val captured = mutableMapOf<String, String>()
        var ok = true
        var err: String? = null
        seq.steps.forEachIndexed { i, step ->
            val s = Instant.now().toEpochMilli()
            try {
                val r = go(step, captured)
                results += r
                onStepFinished(i, seq.steps.size, r)
                if (!r.passed) ok = false else captured.putAll(r.capturedValues)
            } catch (e: Exception) {
                ok = false
                err = e.message
                results += StepResult(step, false, e.message ?: "err", duration = Instant.now().toEpochMilli() - s)
                return@forEachIndexed
            }
        }
        return SequenceResult(seq.id, ok && err == null, results, Instant.now().toEpochMilli() - t0, err)
    }

    private suspend fun go(step: Step, cap: Map<String, String>): StepResult {
        val s = Instant.now().toEpochMilli()
        return when (step) {
            is RunCapability -> {
                val r = driver.runCapabilityForSequence(step.capabilityId, step.params)
                StepResult(step, r.success, r.output, mapOf(step.capabilityId to r.output), Instant.now().toEpochMilli() - s)
            }
            is Wait -> {
                delay((step.seconds * 1000).toLong())
                StepResult(step, true, "wait ${step.seconds}s", duration = Instant.now().toEpochMilli() - s)
            }
            is CapturePid -> {
                val v = driver.queryPid(step.pid)
                StepResult(step, v != null, v ?: "pid fail", if (v != null) mapOf(step.storageKey to v) else emptyMap(), Instant.now().toEpochMilli() - s)
            }
            is Prompt -> {
                onPrompt(step)
                StepResult(step, true, "ack", duration = Instant.now().toEpochMilli() - s)
            }
            is Branch -> {
                val b = if (eval(step.condition, cap)) step.ifTrue else step.ifFalse
                var pass = true
                val o = mutableListOf<String>()
                for (x in b) {
                    val r = go(x, cap)
                    if (!r.passed) pass = false
                    o += r.output
                }
                StepResult(step, pass, o.joinToString("|"), duration = Instant.now().toEpochMilli() - s)
            }
        }
    }

    private fun eval(c: String, v: Map<String, String>): Boolean {
        val p = c.trim().split(Regex("\\s+(>|<|>=|<=|==|!=)\\s+"))
        if (p.size < 3) return false
        val a = v[p[0]]?.toDoubleOrNull() ?: return false
        val b = p[2].toDoubleOrNull() ?: return false
        return when (p[1]) {
            ">" -> a > b; "<" -> a < b; ">=" -> a >= b; "<=" -> a <= b
            "==" -> abs(a - b) < 0.01; "!=" -> abs(a - b) >= 0.01
            else -> false
        }
    }
}
