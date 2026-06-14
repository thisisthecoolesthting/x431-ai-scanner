package com.caseforge.scanner.ai

import com.caseforge.scanner.engine.LiveSample
import com.caseforge.scanner.engine.VciDiagnosticPort
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull

/**
 * Runs the Claude tool-use loop for AI Diagnostic Mode against the standalone OBD link.
 * Independent from the OEM-app AgentRunner. Emits structured events so the UI can show a
 * live thought feed and gauges; suspends on ask_user via the [callbacks].
 */
class AiDiagnosticAgentRunner(
    private val claude: ClaudeClient,
    private val port: VciDiagnosticPort,
    private val intake: AiDiagnosticIntake,
    private val callbacks: Callbacks,
    private val maxSteps: Int = 24,
) {
    interface Callbacks {
        fun onThought(event: AiThoughtEvent)
        fun onGauges(readings: List<GaugeReading>)
        /** Suspend until the tech answers; returns their answer text. */
        suspend fun onAskUser(question: String, chips: List<String>, speak: Boolean): String
    }

    private fun now() = System.currentTimeMillis()
    private fun emit(kind: AiThoughtKind, title: String, detail: String? = null) =
        callbacks.onThought(AiThoughtEvent(now(), kind, title, detail))

    /** Returns the final result, or null if the loop ended without finishing. */
    suspend fun run(): AiDiagnosticResult? {
        val startedAt = now()
        val messages = mutableListOf<ClaudeClient.Message>(
            ClaudeClient.userText(AiDiagnosticPrompts.openingContext(intake)),
        )
        emit(AiThoughtKind.THINKING, "Starting diagnosis", "Reviewing symptoms and vehicle context")

        for (step in 1..maxSteps) {
            val resp = try {
                claude.sendMessages(
                    system = AiDiagnosticPrompts.SYSTEM,
                    messages = messages,
                    tools = AiDiagnosticTools.ALL,
                    maxTokens = 2048,
                    temperature = 0.2,
                    toolChoice = "any",
                )
            } catch (t: Throwable) {
                emit(AiThoughtKind.ERROR, "AI error", t.message ?: t.javaClass.simpleName)
                return null
            }

            // Surface any narration text from this turn.
            resp.firstText()?.takeIf { it.isNotBlank() }?.let {
                emit(AiThoughtKind.THINKING, it.take(200))
            }

            val cleanContent = resp.content.filter { block ->
                block is ClaudeClient.ContentBlock.ToolUse ||
                    block is ClaudeClient.ContentBlock.Text ||
                    block is ClaudeClient.ContentBlock.Image
            }
            messages.add(ClaudeClient.Message("assistant", cleanContent))

            val toolUses = resp.toolUses()
            if (toolUses.isEmpty()) {
                emit(AiThoughtKind.THINKING, "No action taken", resp.firstText()?.take(160))
                break
            }

            val results = mutableListOf<ClaudeClient.ContentBlock>()
            var finished: AiDiagnosticResult? = null
            for (use in toolUses) {
                val (block, result) = executeTool(use, startedAt)
                results.add(block)
                if (result != null) finished = result
            }
            messages.add(ClaudeClient.Message("user", results))
            if (finished != null) return finished
        }
        emit(AiThoughtKind.THINKING, "Diagnosis ended", "Reached step limit without a conclusion")
        return null
    }

    private suspend fun executeTool(
        use: ClaudeClient.ContentBlock.ToolUse,
        startedAt: Long,
    ): Pair<ClaudeClient.ContentBlock, AiDiagnosticResult?> {
        val args = use.input
        return when (use.name) {
            "read_codes" -> {
                val module = strArg(args, "module")
                emit(AiThoughtKind.TOOL, "Reading codes", module?.let { "module $it" })
                val res = port.readDtcs(module)
                val text = res.fold(
                    onSuccess = { list ->
                        if (list.isEmpty()) "No trouble codes found."
                        else list.joinToString("; ") { "${it.code} (${it.module}) ${it.description}" }
                    },
                    onFailure = { "read_codes failed: ${it.message}" },
                )
                res.getOrNull()?.takeIf { it.isNotEmpty() }?.let {
                    emit(AiThoughtKind.FINDING, "Codes: ${it.joinToString(", ") { d -> d.code }}")
                }
                toolText(use.id, text) to null
            }

            "full_scan" -> {
                emit(AiThoughtKind.TOOL, "Full module scan")
                val res = port.fullScan()
                val text = res.fold(
                    onSuccess = { scan ->
                        val all = scan.modules.flatMap { m -> m.dtcs.map { "${m.name}:${it.code} ${it.description}" } }
                        if (all.isEmpty()) "Full scan complete — no codes." else all.joinToString("; ")
                    },
                    onFailure = { "full_scan failed: ${it.message}" },
                )
                toolText(use.id, text) to null
            }

            "read_readiness" -> {
                emit(AiThoughtKind.TOOL, "Checking readiness monitors")
                // ELM path exposes readiness through runCapability("readiness"); fall back gracefully.
                val res = port.runCapability("readiness")
                val text = res.fold(
                    onSuccess = { it.toString() },
                    onFailure = { """{"supported":false,"reason":"${it.message}"}""" },
                )
                toolText(use.id, text) to null
            }

            "read_live_pids" -> {
                val pids = arrArg(args, "pids").ifEmpty { listOf("0C", "05") }.take(8)
                val samples = (intArg(args, "samples") ?: 3).coerceIn(1, 10)
                emit(AiThoughtKind.TOOL, "Measuring live data", pids.joinToString(", ") { AiPidInfo.info(it).label })
                val readings = measure(pids, samples)
                callbacks.onGauges(readings)
                val text = if (readings.isEmpty()) "No live data returned (unsupported or engine off)."
                else readings.joinToString("; ") { "${it.label}=${it.value}${it.unit}" }
                toolText(use.id, text) to null
            }

            "ask_user" -> {
                val q = strArg(args, "question") ?: "Can you check the vehicle?"
                val chips = arrArg(args, "chips")
                val speak = boolArg(args, "speak") ?: false
                emit(AiThoughtKind.QUESTION, q, if (chips.isNotEmpty()) chips.joinToString(" / ") else null)
                val answer = callbacks.onAskUser(q, chips, speak)
                emit(AiThoughtKind.ANSWER, "Tech: $answer")
                toolText(use.id, "Technician answered: $answer") to null
            }

            "finish_session" -> {
                val rootCause = strArg(args, "root_cause") ?: "Undetermined"
                val confidence = (dblArg(args, "confidence") ?: 0.0).toFloat().coerceIn(0f, 1f)
                val repair = strArg(args, "recommended_repair") ?: "Further inspection required."
                val summary = strArg(args, "summary")
                val evidence = arrArg(args, "supporting_evidence")
                emit(AiThoughtKind.RESULT, "Conclusion: $rootCause", "Confidence ${(confidence * 100).toInt()}%")
                val result = AiDiagnosticResult(
                    vin = intake.extracted.vin ?: intake.vinPhoto?.vin,
                    startedAt = startedAt,
                    endedAt = now(),
                    symptoms = intake.symptoms,
                    rootCause = rootCause,
                    confidence = confidence,
                    recommendedRepair = repair,
                    summary = summary,
                    supportingEvidence = evidence,
                    mileage = intake.extracted.mileage,
                    warningLights = intake.extracted.warningLights,
                )
                toolText(use.id, "Session finished.") to result
            }

            else -> toolText(use.id, "Unknown tool: ${use.name}") to null
        }
    }

    /** Collect a few samples per PID off the live flow and build gauge readings. */
    private suspend fun measure(pids: List<String>, samples: Int): List<GaugeReading> {
        val latest = HashMap<String, Double>()
        withTimeoutOrNull(8_000L) {
            val collected = runCatching {
                port.liveData(pids).take(samples * pids.size.coerceAtLeast(1)).toList()
            }.getOrDefault(emptyList<LiveSample>())
            collected.forEach { latest[it.pid.uppercase().padStart(2, '0')] = it.value }
        }
        return pids.map { pid ->
            val key = pid.uppercase().removePrefix("0X").padStart(2, '0')
            val info = AiPidInfo.info(key)
            val v = latest[key]
            GaugeReading(
                pid = key,
                label = info.label,
                value = v?.let { fmt(it) } ?: "—",
                unit = info.unit,
                fillFraction = v?.let { AiPidInfo.fill(key, it) } ?: 0f,
            )
        }
    }

    private fun fmt(v: Double): String = if (v == v.toLong().toDouble()) v.toLong().toString() else "%.1f".format(v)

    // --- arg helpers (defensive JSON reads) ---
    private fun strArg(o: JsonObject, k: String): String? =
        (o[k] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

    private fun intArg(o: JsonObject, k: String): Int? = (o[k] as? JsonPrimitive)?.intOrNull
    private fun dblArg(o: JsonObject, k: String): Double? = (o[k] as? JsonPrimitive)?.doubleOrNull
    private fun boolArg(o: JsonObject, k: String): Boolean? =
        (o[k] as? JsonPrimitive)?.contentOrNull?.toBooleanStrictOrNull()

    private fun arrArg(o: JsonObject, k: String): List<String> =
        (o[k] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull } ?: emptyList()

    private fun toolText(id: String, text: String) = ClaudeClient.ContentBlock.ToolResult(
        toolUseId = id,
        content = listOf(ClaudeClient.ContentBlock.Text(text = text)),
    )
}
