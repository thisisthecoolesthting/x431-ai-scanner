package com.caseforge.scanner.agent

import com.caseforge.scanner.BuildConfig
import com.caseforge.scanner.ai.ClaudeClient
import com.caseforge.scanner.ai.Prompts
import com.caseforge.scanner.ai.RepairInfoLookup
import com.caseforge.scanner.data.SettingsRepo
import com.caseforge.scanner.engine.CapabilityCatalogStore
import com.caseforge.scanner.obd.ObdEngine
import com.caseforge.scanner.obd.ObdSession
import com.caseforge.scanner.obd.StubObdTransport
import com.caseforge.scanner.planb.CapabilityWedgeFilter
import com.caseforge.scanner.planb.MarqueWedgeConfig
import com.caseforge.scanner.planb.detectPlanbMarque
import com.caseforge.scanner.planb.effectiveObdOnly
import com.caseforge.scanner.planb.effectiveTiers
import com.caseforge.scanner.planb.golden.GoldenLogLine
import com.caseforge.scanner.planb.programming.ProgrammingChecklistLoader
import com.caseforge.scanner.planb.programming.ProgrammingGate
import com.caseforge.scanner.vin.DodgeVinDetector
import com.caseforge.scanner.vin.FordVinDetector
import com.caseforge.scanner.vin.GmVinDetector
import com.caseforge.scanner.vin.HondaVinDetector
import com.caseforge.scanner.vin.HyundaiVinDetector
import com.caseforge.scanner.vin.JeepVinDetector
import com.caseforge.scanner.vin.NissanVinDetector
import com.caseforge.scanner.vin.ToyotaVinDetector
import com.caseforge.scanner.vin.VinNormalizer
import com.caseforge.scanner.agent.session.SessionCopilotRegistry
import com.caseforge.scanner.agent.session.SessionDiagnosticVision
import com.caseforge.scanner.data.session.CustomerSessionRepository
import com.caseforge.scanner.transfer.GoldenCaptureStorage
import com.caseforge.scanner.transfer.SessionEventLogger
import java.time.Instant
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.OkHttpClient

/**
 * Orchestrates a Claude tool-use loop that drives the OEM diagnostic app through
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
    private val actuationBridge: VciActuationBridge? = null,
) {

    /** Settings read once at first tool-list build — used for Tier 4 tool inclusion and guards. */
    private val settings by lazy { SettingsRepo(context.applicationContext) }
    private val tools by lazy {
        AgentTools.toolList(
            settings.isPlanBTierEffective(0),
            settings.isPlanBTierEffective(4),
        )
    }

    data class ImagePayload(val mediaType: String, val base64: String)

    data class Outcome(
        val finished: Boolean,
        val summary: JsonObject?,
        val transcript: List<ClaudeClient.Message>,
        val stoppedReason: String,
    )

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

    /** Lazy so we don't construct it unless the agent actually calls repair_info_lookup. */
        /** Prefer debug [BuildConfig.ANTHROPIC_API_KEY] over the injected client (Settings/local.properties). */
    private fun anthropicClientForSession(): ClaudeClient {
        val embedded = BuildConfig.ANTHROPIC_API_KEY.trim()
        if (embedded.isNotBlank()) {
            return ClaudeClient(apiKey = embedded, model = settings.model)
        }
        return claude
    }

    private fun repairInfoLookup(): RepairInfoLookup = RepairInfoLookup(anthropicClientForSession())
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
                val resp = anthropicClientForSession().sendMessages(
                    system = combinedSystem,
                    messages = messages,
                    tools = tools,
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
            "read_obd" -> {
                val sub = (args["subcommand"] as? JsonPrimitive)?.contentOrNullSafe2().orEmpty()
                val pid = (args["pid_hex"] as? JsonPrimitive)?.contentOrNullSafe2().orEmpty()
                "read_obd $sub${if (pid.isNotBlank()) " pid=$pid" else ""}"
            }
            "read_obd_vin" -> "read native OBD VIN"
            "read_obd_dtcs" -> "read native OBD DTCs"
            "read_obd_live_snapshot" -> "read native OBD live snapshot"
            "read_programming_status" -> "read Plan B Tier 4 programming status"
            "check_for_updates" -> "check live update status"
            "sync_vehicle_profiles" -> "sync bundled vehicle profiles"
            "list_capabilities_for_vin" -> "list capabilities.json for VIN wedge (display-only)"
            "finish_session" -> "finishing session"
            "append_golden_event" -> {
                val hint = (args["canId"] as? JsonPrimitive)?.contentOrNullSafe2()?.take(24)?.let { " $it" }.orEmpty()
                "append golden_event$hint"
            }
            "analyze_session_photos" -> "analyze session wizard photos (vision)"
            else -> name
        }
    }

    /** Run a single tool and return (tool_result_block, finish_payload_or_null). */
    private suspend fun executeTool(
        use: ClaudeClient.ContentBlock.ToolUse,
    ): Pair<ClaudeClient.ContentBlock, JsonObject?> {
        val name = use.name
        val args = use.input
        log.event("tool.$name", json.encodeToString(JsonObject.serializer(), args).take(400))

        if (name in EXECUTE_BRIDGE_TOOL_NAMES) {
            return executeNativeObdTool(use)
        }

        if (name == "scan_connection_readiness") {
            return executeConnectionReadinessTool(use)
        }

        if (name == "check_for_updates" || name == "sync_vehicle_profiles") {
            return executeLiveUpdateTool(use)
        }

        if (name == "append_golden_event") {
            return executeAppendGoldenEvent(use)
        }

        if (name == "analyze_session_photos") {
            return executeAnalyzeSessionPhotos(use)
        }

        if (name == "list_capabilities_for_vin") {
            return executeCapabilitiesListTool(use)
        }

        val svc = ScannerAccessibilityService.instance()
        if (svc == null) {
            return toolError(use.id, "Accessibility service is not running. Ask the user to enable it.") to null
        }

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
                            val info = repairInfoLookup().lookup(dtc, vehicle, module)
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
                    if (!approved) {
                        log.event("propose_actuation.denied", description)
                        toolText(use.id, "denied") to null
                    } else {
                        log.event("propose_actuation.approved", description)
                        val bridge = actuationBridge ?: PendingActionQueueBridge.actuation
                        val outcome = PendingActionActuation.executeAfterApproval(
                            context = context,
                            bridge = bridge,
                            testId = testName.ifBlank { "unknown_test" },
                            description = description,
                            sessionId = SessionWorkflowEngine.activeSessionIdForActuationLog(),
                            log = log,
                        )
                        toolText(use.id, outcome) to null
                    }
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

    private suspend fun executeNativeObdTool(
        use: ClaudeClient.ContentBlock.ToolUse,
    ): Pair<ClaudeClient.ContentBlock, JsonObject?> {
        if (use.name == "read_programming_status") {
            return executeProgrammingStatusTool(use)
        }
        if (!settings.isPlanBTierEffective(0)) {
            return toolError(use.id, "Native OBD tools are disabled. Turn on Native OBD (experimental) in Settings.") to null
        }
        val session = ObdSession(StubObdTransport())
        session.connect()
        return try {
            val engine = ObdEngine(session)
            when (use.name) {
                "read_obd_vin" -> {
                    val vin = engine.readVin()
                    val primary = vin ?: "(no VIN — empty ECU response or stub transport)"
                    toolText(use.id, primary + nativeObdMarqueContext(vin)) to null
                }
                "read_obd_dtcs" -> {
                    val stored = engine.readStoredDtcs()
                    val pending = engine.readPendingDtcs()
                    val text = buildString {
                        appendLine("stored:")
                        if (stored.isEmpty()) {
                            appendLine("  (none)")
                        } else {
                            for (d in stored) {
                                val desc = d.description?.let { " — $it" }.orEmpty()
                                appendLine("  ${d.code}$desc")
                            }
                        }
                        appendLine("pending:")
                        if (pending.isEmpty()) {
                            appendLine("  (none)")
                        } else {
                            for (d in pending) {
                                val desc = d.description?.let { " — $it" }.orEmpty()
                                appendLine("  ${d.code}$desc")
                            }
                        }
                    }
                    val vinForContext = runCatching { engine.readVin() }.getOrNull()
                    toolText(use.id, text.trim() + nativeObdMarqueContext(vinForContext)) to null
                }
                "read_obd_live_snapshot" -> {
                    val snap = engine.readLiveSnapshot()
                    val vinForContext = runCatching { engine.readVin() }.getOrNull()
                    val line =
                        "rpm=${snap.rpm?.toString() ?: "null"}, coolantCelsius=${snap.coolantCelsius?.toString() ?: "null"}, speedKmh=${snap.speedKmh?.toString() ?: "null"}"
                    toolText(use.id, line + nativeObdMarqueContext(vinForContext)) to null
                }
                else -> toolError(use.id, "Unknown native OBD tool: ${use.name}") to null
            }
        } catch (t: Throwable) {
            log.event("tool.${use.name}.error", t.message.orEmpty())
            toolError(use.id, "Native OBD error: ${t.message}") to null
        } finally {
            session.disconnect()
        }
    }

    private suspend fun executeProgrammingStatusTool(
        use: ClaudeClient.ContentBlock.ToolUse,
    ): Pair<ClaudeClient.ContentBlock, JsonObject?> {
        if (!settings.isPlanBTierEffective(4)) {
            return toolError(
                use.id,
                "Programming reference tools are disabled. Enable Plan B programming (tier 4 reference) in Settings, " +
                    "complete a native OBD session when tier safety is on, or toggle tier 4 on after connect.",
            ) to null
        }
        val vinArg =
            use.input.findJsonString("vin").trim().uppercase().takeIf { VinNormalizer.hasValidCharset(it) }

        suspend fun vinFromNativeObd(): String? {
            val session = ObdSession(StubObdTransport())
            session.connect()
            return try {
                val engine = ObdEngine(session)
                engine.readVin()?.trim()?.uppercase()?.takeIf { VinNormalizer.hasValidCharset(it) }
            } catch (_: Throwable) {
                null
            } finally {
                session.disconnect()
            }
        }

        val vin = vinArg ?: if (settings.isPlanBTierEffective(0)) vinFromNativeObd() else null
        val blocked = ProgrammingGate.TIER4_BLOCKED.message ?: "Tier 4 programming is blocked in-app."

        val marque = detectPlanbMarque(context.applicationContext, vin)
        val count = marque?.let { m -> ProgrammingChecklistLoader.load(context.applicationContext, m)?.entries?.size }
            ?: 0
        val details = marque?.let { "Detected marque: ${it.name}. Bundled Tier 4 checklist rows: $count (read-only reference)." }
            ?: "Marque unknown (supply a valid 17-character VIN in tool input, or enable native OBD read for VIN). Bundled Tier 4 checklist rows: 0."

        val text = buildString {
            appendLine(blocked)
            appendLine(details)
        }.trim()

        return toolText(use.id, text) to null
    }

    private suspend fun executeAppendGoldenEvent(
        use: ClaudeClient.ContentBlock.ToolUse,
    ): Pair<ClaudeClient.ContentBlock, JsonObject?> = withContext(Dispatchers.IO) {
        val args = use.input
        val dirRaw = args.findJsonString("dir").trim().uppercase()
        if (dirRaw != "TX" && dirRaw != "RX") {
            return@withContext toolError(use.id, "append_golden_event: dir must be TX or RX") to null
        }
        val canId = args.findJsonString("canId").trim()
        val payload = args.findJsonString("payload").trim()
        if (canId.isEmpty() || payload.isEmpty()) {
            return@withContext toolError(use.id, "append_golden_event requires canId and payload") to null
        }
        val ts = args.findJsonString("ts").trim().ifEmpty { Instant.now().toString() }
        val line = GoldenLogLine(
            ts = ts,
            dir = dirRaw,
            canId = canId,
            payload = payload,
            uiContext = args.findJsonString("uiContext"),
            oemPackage = args.findJsonString("oemPackage").takeIf { it.isNotBlank() },
            windowTitle = args.findJsonString("windowTitle").takeIf { it.isNotBlank() },
            actionId = args.findJsonString("actionId").takeIf { it.isNotBlank() },
        )
        val file = GoldenCaptureStorage.eventsFile(context)
        file.parentFile?.mkdirs()
        val encoded = json.encodeToString(GoldenLogLine.serializer(), line)
        file.appendText(encoded + "\n")
        log.event("append_golden_event.ok", "len=${file.length()} path=${file.name}")
        toolText(
            use.id,
            "Appended line to ${GoldenCaptureStorage.ZIP_ENTRY} source file (${file.length()} bytes on disk).",
        ) to null
    }

    private suspend fun executeAnalyzeSessionPhotos(
        use: ClaudeClient.ContentBlock.ToolUse,
    ): Pair<ClaudeClient.ContentBlock, JsonObject?> = withContext(Dispatchers.IO) {
        val active = SessionCopilotRegistry.active
        if (active == null) {
            return@withContext toolError(
                use.id,
                "No active New Session — open session chat after the photo wizard first.",
            ) to null
        }
        val paths = active.photoPaths()
        if (paths.isEmpty()) {
            return@withContext toolError(use.id, "No wizard photos on disk for this session.") to null
        }

        val vision = SessionDiagnosticVision(context, settings)
        val result = vision.analyzeWizardPhotos(active.sessionId, active, active.vin)
        SessionEventLogger.log(
            context,
            active.sessionId,
            "analyze_session_photos",
            detail = result.insights?.confidence.orEmpty().ifBlank { result.error.orEmpty() },
            extra = mapOf(
                "model" to settings.model,
                "imageCount" to result.imagesSent.toString(),
            ),
        )

        result.insights?.let { insights ->
            active.vin?.let { v ->
                CustomerSessionRepository(context).persistPhotoDiagnostics(v, active.sessionId, insights)
            }
            val text = vision.formatInsightsForChat(insights)
            log.event("analyze_session_photos.ok", "findings=${insights.findings.size}")
            return@withContext toolText(use.id, text) to null
        }

        log.event("analyze_session_photos.fail", result.error.orEmpty())
        toolError(use.id, "Photo vision failed: ${result.error ?: "unknown"}") to null
    }

    private suspend fun executeLiveUpdateTool(
        use: ClaudeClient.ContentBlock.ToolUse,
    ): Pair<ClaudeClient.ContentBlock, JsonObject?> = try {
        val text = withContext(Dispatchers.IO) {
            when (use.name) {
                "check_for_updates" ->
                    com.caseforge.scanner.update.LiveUpdateCoordinator.checkForUpdates(context)
                "sync_vehicle_profiles" ->
                    com.caseforge.scanner.update.LiveUpdateCoordinator.syncVehicleProfiles(context)
                else -> "Unknown update tool"
            }
        }
        log.event("live_update.${use.name}.ok", text.lines().firstOrNull().orEmpty())
        toolText(use.id, text) to null
    } catch (t: Throwable) {
        log.event("live_update.${use.name}.error", t.message.orEmpty())
        toolError(use.id, "Live update failed: ${t.message}") to null
    }

    private suspend fun executeConnectionReadinessTool(
        use: ClaudeClient.ContentBlock.ToolUse,
    ): Pair<ClaudeClient.ContentBlock, JsonObject?> {
        val profileId =
            use.input.findJsonString("vehicle_profile_id").trim().ifBlank {
                com.caseforge.scanner.agent.discovery.VehicleProfileLoader.DEFAULT_WINDSTAR_ID
            }
        return try {
            val agent = com.caseforge.scanner.agent.discovery.TabletHardwareDiscoveryAgent(context)
            val report = withContext(Dispatchers.IO) { agent.scan(profileId) }
            val text = agent.formatForAgent(report)
            log.event("scan_connection_readiness.ok", "profile=$profileId devices=${report.devices.size}")
            toolText(use.id, text) to null
        } catch (t: Throwable) {
            log.event("scan_connection_readiness.error", t.message.orEmpty())
            toolError(use.id, "Connection scan failed: ${t.message}") to null
        }
    }

    private suspend fun executeCapabilitiesListTool(
        use: ClaudeClient.ContentBlock.ToolUse,
    ): Pair<ClaudeClient.ContentBlock, JsonObject?> = try {
        val appCtx = context.applicationContext
        val vinArg = use.input.findJsonString("vin").trim().uppercase()
            .takeIf { it.length == VinNormalizer.VIN_LENGTH && VinNormalizer.hasValidCharset(it) }

        val vinEffective = vinArg ?: settings.fastWorkflowState.lastVin
            ?.trim()
            ?.uppercase()
            ?.takeIf { v ->
                v.length == VinNormalizer.VIN_LENGTH && VinNormalizer.hasValidCharset(v)
            }

        val catalog = withContext(Dispatchers.IO) {
            CapabilityCatalogStore(appCtx, appCtx.cacheDir, OkHttpClient()).load()
        }
        val matrix = MarqueWedgeConfig.load(appCtx)
        val card = vinEffective?.let { v -> matrix?.let { MarqueWedgeConfig.findCardForVin(v, it) } }
        val filtered = catalog.capabilities
            .filter { CapabilityWedgeFilter.matchesWedge(card, it) }
            .sortedWith(compareBy({ it.category }, { it.label }))

        val text = buildString {
            appendLine("display_only: true (no OEM execution)")
            appendLine("vin_used: ${vinEffective ?: "(none — global-scope rows only or no valid VIN)"}")
            if (card != null && matrix != null) {
                val tierPart = card.effectiveTiers(matrix).sorted().joinToString(", ").ifBlank { "—" }
                appendLine(
                    "wedge_card: ${card.marque} ${card.platformCode} ${card.model} " +
                        "${card.modelYearStart}-${card.modelYearEnd} · tiers $tierPart",
                )
            } else {
                appendLine("wedge_card: (none — unmatched VIN or matrix missing)")
            }
            appendLine("row_count: ${filtered.size}")
            appendLine("---")
            for (e in filtered) {
                appendLine("[${e.category}] ${e.id} — ${e.label}")
                appendLine("  done_when: ${e.doneWhen.ifBlank { "—" }}")
                appendLine("  path: ${e.path.joinToString(" → ").ifBlank { "—" }}")
                val skreemNote =
                    if (CapabilityWedgeFilter.isSkreemImmobilizerRow(e)) {
                        " (SKREEM/immo row — Plan B Immo + Programming screens for checklists)"
                    } else {
                        ""
                    }
                appendLine("  note: ${e.note.ifBlank { "—" }}$skreemNote")
                appendLine()
            }
        }.trim()

        log.event("list_capabilities_for_vin.ok", "rows=${filtered.size}")
        toolText(use.id, text) to null
    } catch (t: Throwable) {
        log.event("list_capabilities_for_vin.error", t.message.orEmpty())
        toolError(use.id, "list_capabilities_for_vin failed: ${t.message}") to null
    }

    /**
     * Appends WMI hint ([VinNormalizer]), then [MarqueWedgeConfig.findCardForVin]-based wedge card summary (Ford /
     * Dodge / Jeep rows via bundled multi-marque matrix), and tiers from the matching card when present (matrix defaults apply).
     */
    private fun nativeObdMarqueContext(vin: String?): String {
        val normalized = vin?.trim()?.uppercase()?.takeIf { it.isNotBlank() }
        val hint = normalized?.let { VinNormalizer.marqueHint(it) }
        val matrix = MarqueWedgeConfig.load(context)
        val card = normalized?.let { n -> matrix?.let { MarqueWedgeConfig.findCardForVin(n, it) } }
        val wedgeSummary = when {
            normalized == null ->
                "(no VIN — cannot classify marque / wedge)"
            matrix == null ->
                "(wedge matrix unavailable — asset missing or invalid)"
            card == null && normalized.length == VinNormalizer.VIN_LENGTH -> when {
                FordVinDetector.isLikelyFordVin(normalized) ->
                    "(no wedge card matched — WMI suggests Ford)"
                JeepVinDetector.isLikelyJeepVin(normalized) ->
                    "(no wedge card matched — WMI suggests Jeep)"
                DodgeVinDetector.isLikelyDodgeVin(normalized) ->
                    "(no wedge card matched — WMI suggests Dodge)"
                GmVinDetector.isLikelyGmVin(normalized) ->
                    "(no wedge card matched — WMI suggests Chevrolet/GM)"
                ToyotaVinDetector.isLikelyToyotaVin(normalized) ->
                    "(no wedge card matched — WMI suggests Toyota/Lexus)"
                HondaVinDetector.isLikelyHondaVin(normalized) ->
                    "(no wedge card matched — WMI suggests Honda/Acura)"
                NissanVinDetector.isLikelyNissanVin(normalized) ->
                    "(no wedge card matched — WMI suggests Nissan/Infiniti)"
                HyundaiVinDetector.isLikelyHyundaiVin(normalized) ->
                    "(no wedge card matched — WMI suggests Hyundai/Kia)"
                else ->
                    "(no wedge card for this WMI / model-year band)"
            }
            card == null ->
                "(no wedge card for this WMI / model-year band)"
            else -> {
                val tierPart = card.effectiveTiers(matrix).sorted().joinToString(", ").ifBlank { "—" }
                val mode = if (card.effectiveObdOnly(matrix)) "OBD-only default" else "full diag default"
                "${card.marque} ${card.platformCode} ${card.model} ${card.modelYearStart}-${card.modelYearEnd} · tiers $tierPart · $mode"
            }
        }
        val tiers = when {
            normalized == null -> "(unknown — no VIN)"
            matrix == null -> "(unknown)"
            card == null -> "(n/a — no matching wedge card)"
            else -> card.effectiveTiers(matrix).sorted().joinToString(", ").ifBlank { "—" }
        }
        return buildString {
            appendLine()
            appendLine("--- native OBD marque / wedge ---")
            appendLine("marqueHint: ${hint ?: "(none)"}")
            appendLine("wedgeCardSummary: $wedgeSummary")
            appendLine("enabledTiers: $tiers")
        }.trimEnd()
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

private val EXECUTE_BRIDGE_TOOL_NAMES = setOf(
    "read_obd_vin",
    "read_obd_dtcs",
    "read_obd_live_snapshot",
    "read_programming_status",
)

private fun JsonObject.findJsonString(vararg keys: String): String {
    for (k in keys) {
        val v = get(k)
        val s =
            try {
                (v as? JsonPrimitive)?.content
            } catch (_: Throwable) {
                null
            }
            ?: continue
        if (s.isNotBlank()) return s
    }
    return ""
}

private fun JsonPrimitive.contentOrNullSafe(): String? = try { content } catch (_: Throwable) { null }

private fun JsonPrimitive.contentOrNullSafe2(): String? = try { content } catch (_: Throwable) { null }
