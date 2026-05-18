package com.caseforge.scanner.agent

import com.caseforge.scanner.ai.ClaudeClient
import com.caseforge.scanner.ai.Prompts
import com.caseforge.scanner.ai.RepairInfoLookup
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 * Orchestrates a Claude tool-use loop that drives the X431 app through
 * [ScannerAccessibilityService]. Each iteration:
 *   1. Send the running conversation to Claude (with our tool list).
 *   2. If Claude returns tool_use blocks, execute them on the accessibility service.
 *   3. Append tool_result blocks back into the conversation.
 *   4. Repeat until Claude calls finish_session or we hit the step cap.
 *
 * The runner is cancellable via the supplied [Job] (kill-switch).
 */
class AgentRunner(
    private val context: android.content.Context,
    private val claude: ClaudeClient,
    private val log: AgentActionLog,
    private val screenshot: suspend () -> ImagePayload? = { null },
    private val requireApproval: Boolean = false,
    private val maxSteps: Int = 40,
    private val agentNotes: String = "",
) {

    data class ImagePayload(val mediaType: String, val base64: String)

    data class Outcome(
        val finished: Boolean,
        val summary: JsonObject?,
        val transcript: List<ClaudeClient.Message>,
        val stoppedReason: String,
    )

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

    /** Lazy so we don't construct it unless the agent actually calls repair_info_lookup. */
    private val repairInfo: RepairInfoLookup by lazy { RepairInfoLookup(claude) }
    private val nhtsa: com.caseforge.scanner.ai.NhtsaLookup by lazy { com.caseforge.scanner.ai.NhtsaLookup() }

    suspend fun run(vin: String?, symptom: String?, parentJob: Job? = null): Outcome =
        withContext(Dispatchers.IO) {
            AgentStatus.begin()
            com.caseforge.scanner.util.KeepAwakeManager.acquire(context)
            CostTracker.beginSession()
            val oemPack = com.caseforge.scanner.ai.OemPlaybooks.forVin(vin)
            val goalText = if (oemPack != null)
                Prompts.agentGoal(vin, symptom) + "\n\n--- OEM-SPECIFIC PLAYBOOK ---\n" + oemPack
            else Prompts.agentGoal(vin, symptom)
            val messages = mutableListOf<ClaudeClient.Message>(
                ClaudeClient.userText(goalText)
            )
            var stop = "max_steps"
            var summary: JsonObject? = null

            for (step in 1..maxSteps) {
                if (parentJob?.isActive == false) { stop = "cancelled"; break }
                AgentStatus.setStep(step)
                AgentStatus.setActivity("Step $step: thinking…")
                log.event("step.${step}.send", "messages=${messages.size}")

                val combinedSystem = if (agentNotes.isNotBlank())
                    "${Prompts.AGENT_SYSTEM}\n\n--- USER NOTES ---\n$agentNotes"
                else Prompts.AGENT_SYSTEM
                val resp = claude.sendMessages(
                    system = combinedSystem,
                    messages = messages,
                    tools = AgentTools.ALL,
                    maxTokens = 4096,
                    // Force a tool call on step 1 so the agent doesn't bail with a text-only reply.
                    toolChoice = "any",  // force a tool call every step; finish_session is the escape hatch
                )

                // Track token cost
                resp.usage?.let { CostTracker.record(it.inputTokens, it.outputTokens) }
                // Capture assistant turn — but filter out any content blocks we manufactured
                // from unknown types (our deserializer falls back to Text for unknown blocks).
                // If we echo those back to Anthropic, the API can reject the conversation as
                // malformed. Keep only blocks Anthropic itself originally sent in a known shape.
                val cleanContent = resp.content.filter { block ->
                    block is ClaudeClient.ContentBlock.ToolUse ||
                    block is ClaudeClient.ContentBlock.Text ||
                    block is ClaudeClient.ContentBlock.Image
                }
                messages.add(ClaudeClient.Message("assistant", cleanContent))

                // Conversation trimming disabled — naively dropping mid-pair breaks
                // Anthropic's requirement that every tool_result has its matching tool_use.
                // For 40-step sessions the context stays well under 200k tokens, so we're fine.

                val toolUses = resp.toolUses()
                if (toolUses.isEmpty()) {
                    // No tool call → bubble Claude's actual reply up so the user can read it.
                    val saidText = resp.firstText()?.trim().orEmpty()
                    log.event("step.${step}.no_tools", saidText.take(400))
                    val sr = resp.stopReason ?: "?"
                    stop = "no_tool_use[$sr]: ${saidText.take(160).ifBlank { "(no text)" }}"
                    break
                }

                val results = mutableListOf<ClaudeClient.ContentBlock>()
                var finished = false
                for (use in toolUses) {
                    AgentStatus.setActivity("Step ${step}: ${describeToolCall(use)}")
                    val (resultBlock, finishPayload) = executeTool(use)
                    results.add(resultBlock)
                    if (finishPayload != null) {
                        summary = finishPayload
                        finished = true
                    }
                }
                messages.add(ClaudeClient.Message("user", results))
                if (finished) { stop = "finish_session"; break }
                // Cheap throttle so we don't pin the UI thread of the target app.
                delay(800)
            }
            CostTracker.endSession(context)
            com.caseforge.scanner.util.KeepAwakeManager.release()
            AgentStatus.end(stop)
            Outcome(
                finished = summary != null,
                summary = summary,
                transcript = messages,
                stoppedReason = stop,
            )
        }

    /** Human-readable preview of a tool call for the ticker. */
    private fun describeToolCall(use: ClaudeClient.ContentBlock.ToolUse): String {
        val name = use.name
        val args = use.input
        return when (name) {
            "tap" -> {
                val t = (args["text"] as? JsonPrimitive)?.contentOrNullSafe2()
                if (t != null) "tap '${t.take(40)}'"
                else {
                    val x = (args["x"] as? JsonPrimitive)?.contentOrNullSafe2()
                    val y = (args["y"] as? JsonPrimitive)?.contentOrNullSafe2()
                    "tap ($x,$y)"
                }
            }
            "type" -> {
                val v = (args["value"] as? JsonPrimitive)?.contentOrNullSafe2().orEmpty()
                "type '${v.take(30)}'"
            }
            "scroll" -> "scroll ${(args["direction"] as? JsonPrimitive)?.contentOrNullSafe2() ?: "down"}"
            "wait_for" -> "wait for '${(args["text"] as? JsonPrimitive)?.contentOrNullSafe2()?.take(40) ?: ""}'"
            "repair_info_lookup" -> {
                val c = (args["dtc_code"] as? JsonPrimitive)?.contentOrNullSafe2() ?: "?"
                "lookup repair info for $c"
            }
            "finish_session" -> "finishing session"
            else -> name
        }
    }

    /** Run a single tool and return (tool_result_block, finish_payload_or_null). */
    private suspend fun executeTool(
        use: ClaudeClient.ContentBlock.ToolUse,
    ): Pair<ClaudeClient.ContentBlock, JsonObject?> {
        val svc = ScannerAccessibilityService.instance()
        if (svc == null) {
            return toolError(use.id, "Accessibility service is not running. Ask the user to enable it.") to null
        }

        val name = use.name
        val args = use.input
        log.event("tool.$name", json.encodeToString(JsonObject.serializer(), args).take(400))

        return try {
            when (name) {
                "read_screen" -> {
                    val snap = svc.readScreen()
                    val payload = json.encodeToString(ScreenSnapshot.serializer(), snap)
                    toolText(use.id, payload) to null
                }
                "tap" -> {
                    val text = (args["text"] as? JsonPrimitive)?.contentOrNullSafe()
                    val exact = (args["exact"] as? JsonPrimitive)?.contentOrNullSafe()?.toBooleanStrictOrNull() ?: false
                    val x = (args["x"] as? JsonPrimitive)?.contentOrNullSafe()?.toIntOrNull()
                    val y = (args["y"] as? JsonPrimitive)?.contentOrNullSafe()?.toIntOrNull()
                    val ok = when {
                        text != null -> svc.tapByText(text, exact)
                        x != null && y != null -> svc.tapAt(x, y)
                        else -> false
                    }
                    toolText(use.id, if (ok) "ok" else "no matching element") to null
                }
                "type" -> {
                    val target = (args["target"] as? JsonPrimitive)?.contentOrNullSafe()
                    val value = (args["value"] as? JsonPrimitive)?.contentOrNullSafe().orEmpty()
                    val ok = svc.typeInto(target, value)
                    toolText(use.id, if (ok) "ok" else "no editable field") to null
                }
                "scroll" -> {
                    val dir = (args["direction"] as? JsonPrimitive)?.contentOrNullSafe() ?: "down"
                    val ok = svc.scroll(dir)
                    toolText(use.id, if (ok) "ok" else "nothing scrollable") to null
                }
                "back" -> {
                    svc.back()
                    toolText(use.id, "ok") to null
                }
                "wait_for" -> {
                    val text = (args["text"] as? JsonPrimitive)?.contentOrNullSafe().orEmpty()
                    val to = (args["timeout_ms"] as? JsonPrimitive)?.contentOrNullSafe()?.toLongOrNull() ?: 8000L
                    val found = svc.waitFor(text, to)
                    toolText(use.id, if (found) "found" else "timeout") to null
                }
                "capture_screenshot" -> {
                    val shot = screenshot()
                    if (shot == null) {
                        toolError(use.id, "Screenshot unavailable (capture not granted).") to null
                    } else {
                        val img = ClaudeClient.ContentBlock.Image(
                            source = ClaudeClient.ContentBlock.ImageSource(
                                mediaType = shot.mediaType,
                                data = shot.base64,
                            )
                        )
                        ClaudeClient.ContentBlock.ToolResult(
                            toolUseId = use.id,
                            content = listOf(img),
                        ) to null
                    }
                }
                "repair_info_lookup" -> {
                    val dtc = (args["dtc_code"] as? JsonPrimitive)?.contentOrNullSafe()
                    val vehicle = (args["vehicle"] as? JsonPrimitive)?.contentOrNullSafe()
                    val module = (args["module"] as? JsonPrimitive)?.contentOrNullSafe()
                    log.event("repair_info_lookup.request", "dtc=$dtc vehicle=$vehicle module=${module ?: "-"}")
                    if (dtc.isNullOrBlank() || vehicle.isNullOrBlank()) {
                        toolError(use.id, "repair_info_lookup requires both 'dtc_code' and 'vehicle'.") to null
                    } else {
                        try {
                            val info = repairInfo.lookup(dtc, vehicle, module)
                            log.event("repair_info_lookup.ok", "dtc=$dtc chars=${info.length}")
                            toolText(use.id, info) to null
                        } catch (t: Throwable) {
                            log.event("repair_info_lookup.error", t.message.orEmpty())
                            toolError(use.id, "Lookup failed: ${t.message}. Proceed without it.") to null
                        }
                    }
                }
                "vin_lookup" -> {
                    val vin = (args["vin"] as? JsonPrimitive)?.contentOrNullSafe().orEmpty()
                    if (vin.length !in 11..17) {
                        toolError(use.id, "vin_lookup requires a 17-char VIN; got '${vin.take(20)}'") to null
                    } else {
                        try {
                            val info = withContext(Dispatchers.IO) { nhtsa.decodeAndRecalls(vin) }
                            log.event("vin_lookup.ok", "vin=$vin chars=${info.length}")
                            toolText(use.id, info) to null
                        } catch (t: Throwable) {
                            log.event("vin_lookup.error", t.message.orEmpty())
                            toolError(use.id, "NHTSA lookup failed: ${t.message}") to null
                        }
                    }
                }
                "look_at" -> {
                    try {
                        val img = com.caseforge.scanner.agent.CameraTool.capturePhoto(context)
                        if (img != null) {
                            val block = ClaudeClient.ContentBlock.Image(
                                source = ClaudeClient.ContentBlock.ImageSource(mediaType = "image/jpeg", data = img)
                            )
                            ClaudeClient.ContentBlock.ToolResult(toolUseId = use.id, content = listOf(block)) to null
                        } else {
                            toolError(use.id, "Camera capture cancelled or denied.") to null
                        }
                    } catch (t: Throwable) {
                        toolError(use.id, "look_at error: ${t.message}") to null
                    }
                }
                "listen_to_engine" -> {
                    val dur = (args["duration_ms"] as? JsonPrimitive)?.contentOrNullSafe()?.toIntOrNull() ?: 6000
                    try {
                        val report = com.caseforge.scanner.agent.AcousticTool.record(dur)
                        toolText(use.id, report) to null
                    } catch (t: Throwable) {
                        toolError(use.id, "listen_to_engine error: ${t.message}") to null
                    }
                }
                "read_obd" -> {
                    val sub = (args["subcommand"] as? JsonPrimitive)?.contentOrNullSafe().orEmpty()
                    val pid = (args["pid_hex"] as? JsonPrimitive)?.contentOrNullSafe().orEmpty()
                    try {
                        val out = when (sub) {
                            "connect" -> com.caseforge.scanner.agent.ObdBluetoothTool.scanAndConnect()
                            "pid" -> com.caseforge.scanner.agent.ObdBluetoothTool.readPid(pid)
                            "dtcs" -> com.caseforge.scanner.agent.ObdBluetoothTool.readDtcs()
                            "disconnect" -> { com.caseforge.scanner.agent.ObdBluetoothTool.disconnect(); "Disconnected" }
                            else -> "Unknown subcommand: $sub"
                        }
                        toolText(use.id, out) to null
                    } catch (t: Throwable) {
                        toolError(use.id, "read_obd error: ${t.message}") to null
                    }
                }
                "propose_actuation" -> {
                    val testName = (args["test_name"] as? JsonPrimitive)?.contentOrNullSafe().orEmpty()
                    val reason = (args["reason"] as? JsonPrimitive)?.contentOrNullSafe().orEmpty()
                    val description = "Run bidirectional test: $testName — $reason"
                    val approved = if (!requireApproval) true
                    else PendingActionQueue.request(
                        tool = "propose_actuation",
                        args = json.encodeToString(JsonObject.serializer(), args),
                        description = description,
                    )
                    log.event("propose_actuation.${if (approved) "approved" else "denied"}", description)
                    toolText(use.id, if (approved) "approved" else "denied") to null
                }
                "finish_session" -> {
                    toolText(use.id, "session ended") to args
                }
                else -> toolError(use.id, "Unknown tool: $name") to null
            }
        } catch (t: Throwable) {
            log.event("tool.$name.error", t.message.orEmpty())
            toolError(use.id, "Exception: ${t.message}") to null
        }
    }

    private fun toolText(id: String, text: String) = ClaudeClient.ContentBlock.ToolResult(
        toolUseId = id,
        content = listOf(ClaudeClient.ContentBlock.Text(text = text)),
    )

    private fun toolError(id: String, text: String) = ClaudeClient.ContentBlock.ToolResult(
        toolUseId = id,
        content = listOf(ClaudeClient.ContentBlock.Text(text = text)),
        isError = true,
    )
}

private fun JsonPrimitive.contentOrNullSafe(): String? = try { content } catch (_: Throwable) { null }


private fun JsonPrimitive.contentOrNullSafe2(): String? = try { content } catch (_: Throwable) { null }
