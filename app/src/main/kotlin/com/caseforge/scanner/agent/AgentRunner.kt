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
    private val claude: ClaudeClient,
    private val log: AgentActionLog,
    private val screenshot: suspend () -> ImagePayload? = { null },
    private val requireApproval: Boolean = false,
    private val maxSteps: Int = 40,
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

    suspend fun run(vin: String?, symptom: String?, parentJob: Job? = null): Outcome =
        withContext(Dispatchers.IO) {
            val messages = mutableListOf<ClaudeClient.Message>(
                ClaudeClient.userText(Prompts.agentGoal(vin, symptom))
            )
            var stop = "max_steps"
            var summary: JsonObject? = null

            for (step in 1..maxSteps) {
                if (parentJob?.isActive == false) { stop = "cancelled"; break }
                log.event("step.${step}.send", "messages=${messages.size}")

                val resp = claude.sendMessages(
                    system = Prompts.AGENT_SYSTEM,
                    messages = messages,
                    tools = AgentTools.ALL,
                    maxTokens = 2048,
                )

                // Capture assistant turn verbatim into the transcript.
                messages.add(ClaudeClient.Message("assistant", resp.content))

                val toolUses = resp.toolUses()
                if (toolUses.isEmpty()) {
                    // No tool call → model is talking to itself or done thinking. Stop.
                    log.event("step.${step}.no_tools", resp.firstText().orEmpty().take(400))
                    stop = "no_tool_use"
                    break
                }

                val results = mutableListOf<ClaudeClient.ContentBlock>()
                var finished = false
                for (use in toolUses) {
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
                delay(150)
            }
            Outcome(
                finished = summary != null,
                summary = summary,
                transcript = messages,
                stoppedReason = stop,
            )
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
