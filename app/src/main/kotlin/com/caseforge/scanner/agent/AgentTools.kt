package com.caseforge.scanner.agent

import com.caseforge.scanner.ai.ClaudeClient
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/**
 * Declarative tool definitions the agent is allowed to call, in the format the Anthropic
 * Messages API expects. Keep names + descriptions tight so the model picks the right one.
 */
object AgentTools {

    val READ_SCREEN = ClaudeClient.Tool(
        name = "read_screen",
        description = "Returns a structured snapshot of the OEM diagnostic app's current screen — all visible " +
                "interactive nodes with their text, clickability, and bounds. ALWAYS call this " +
                "before deciding the next action. Returns JSON.",
        inputSchema = schema { put("type", "object"); putJsonObject("properties") {}; put("additionalProperties", false) }
    )

    val TAP = ClaudeClient.Tool(
        name = "tap",
        description = "Taps a UI element. Prefer matching by visible text. " +
                "Provide EITHER 'text' (substring match on a clickable node) OR 'x'+'y' screen coords.",
        inputSchema = schema {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("text") { put("type", "string") }
                putJsonObject("exact") { put("type", "boolean") }
                putJsonObject("x") { put("type", "integer") }
                putJsonObject("y") { put("type", "integer") }
            }
        }
    )

    val TYPE = ClaudeClient.Tool(
        name = "type",
        description = "Types text into a focused editable field. Optionally pass 'target' to pick a " +
                "specific field by its current text/hint.",
        inputSchema = schema {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("target") { put("type", "string") }
                putJsonObject("value") { put("type", "string") }
            }

        }
    )

    val SCROLL = ClaudeClient.Tool(
        name = "scroll",
        description = "Scrolls the first scrollable container. direction: 'down' or 'up'.",
        inputSchema = schema {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("direction") { put("type", "string") }
            }
        }
    )

    val BACK = ClaudeClient.Tool(
        name = "back",
        description = "Presses the system back button.",
        inputSchema = schema { put("type", "object"); putJsonObject("properties") {} }
    )

    val WAIT_FOR = ClaudeClient.Tool(
        name = "wait_for",
        description = "Block up to timeout_ms (default 8000) until the given text appears on screen. " +
                "Use after starting a scan to wait for the result.",
        inputSchema = schema {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("text") { put("type", "string") }
                putJsonObject("timeout_ms") { put("type", "integer") }
            }
        }
    )

    val CAPTURE_SCREENSHOT = ClaudeClient.Tool(
        name = "capture_screenshot",
        description = "Captures the current screen as an image and returns it. Use this when the " +
                "accessibility text alone is ambiguous (e.g., gauges, graphs, or graphical-only " +
                "live-data screens).",
        inputSchema = schema { put("type", "object"); putJsonObject("properties") {} }
    )

    val FINISH_SESSION = ClaudeClient.Tool(
        name = "finish_session",
        description = "Call once the diagnostic goal is complete. Provide the final structured " +
                "triage report. After this, the loop stops.",
        inputSchema = schema {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("vehicle_summary") { put("type", "string") }
                putJsonObject("dtcs_found") {
                    put("type", "array")
                    putJsonObject("items") {
                        put("type", "object")
                        putJsonObject("properties") {
                            putJsonObject("code") { put("type", "string") }
                            putJsonObject("module") { put("type", "string") }
                            putJsonObject("description") { put("type", "string") }
                            putJsonObject("status") { put("type", "string") }
                        }
                    }
                }
                putJsonObject("root_cause") { put("type", "string") }
                putJsonObject("recommended_repair") { put("type", "string") }
                putJsonObject("tests_performed") {
                    put("type", "array")
                    putJsonObject("items") { put("type", "string") }
                }
            }
        }
    )

    val REPAIR_INFO_LOOKUP = ClaudeClient.Tool(
        name = "repair_info_lookup",
        description = "Call this whenever you encounter an unfamiliar DTC, before recommending " +
                "repairs. Returns common causes, tests, TSBs, and a wiring hint for the given " +
                "code on the given vehicle. Cheap to call — call it freely.",
        inputSchema = schema {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("dtc_code") { put("type", "string") }
                putJsonObject("vehicle") {
                    put("type", "string")
                    put("description", "e.g. '2019 Chevrolet Silverado 5.3L'")
                }
                putJsonObject("module") { put("type", "string") }
            }

        }
    )

    val VIN_LOOKUP = ClaudeClient.Tool(
        name = "vin_lookup",
        description = "Decode a VIN to year/make/model/engine/trim using NHTSA's free database AND " +
                "fetch any open recalls for that vehicle. Free, fast, no key needed. " +
                "ALWAYS call this when you first see a VIN on screen — it tells you what you're " +
                "working on without asking the tech.",
        inputSchema = schema {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("vin") {
                    put("type", "string")
                    put("description", "17-character VIN")
                }
            }
        }
    )

    val LOOK_AT = ClaudeClient.Tool(
        name = "look_at",
        description = "Ask the tech to point the tablet camera at the engine bay (or wherever) " +
                "and capture a photo for you to see. Use when on-screen OEM diagnostic app data is ambiguous " +
                "and you need physical evidence: connector seated, leak visible, label/part number, " +
                "wiring tap, aftermarket mod. Returns a JPEG image you can analyze.",
        inputSchema = schema { put("type", "object"); putJsonObject("properties") {} }
    )

    val LISTEN_TO_ENGINE = ClaudeClient.Tool(
        name = "listen_to_engine",
        description = "Record ~6s of audio from the tablet mic and return dominant frequencies + " +
                "RMS + spectral centroid + transient peaks as text. Use to corroborate misfires, " +
                "knock, injector tick, lifter noise, belt squeal, exhaust leaks.",
        inputSchema = schema {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("duration_ms") { put("type", "integer") }
            }
        }
    )

    val READ_OBD = ClaudeClient.Tool(
        name = "read_obd",
        description = "Read OBD-II live data or DTCs via a paired Bluetooth ELM327 dongle. " +
                "Faster than driving OEM diagnostic UI for plain PIDs. subcommand: connect | pid | dtcs | disconnect. " +
                "When subcommand=pid, supply pid_hex (e.g. '0C' for RPM, '05' for coolant). " +
                "Prefer OEM diagnostic app for module-specific (ABS/SRS/TCM) and bidirectional tests.",
        inputSchema = schema {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("subcommand") { put("type", "string") }
                putJsonObject("pid_hex") { put("type", "string") }
            }
        }
    )

    val PROPOSE_ACTUATION = ClaudeClient.Tool(
        name = "propose_actuation",
        description = "ASK FOR HUMAN APPROVAL before running a bidirectional test or any write to a " +
                "module (actuation, adaptation, programming, key fob match, etc.). Provide a " +
                "one-line plain-English description and the specific test name. Returns 'approved' " +
                "or 'denied'. If denied, choose another path. " +
                "ONLY required when the autonomous flag is disabled; the loop wraps this for you.",
        inputSchema = schema {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("test_name") { put("type", "string") }
                putJsonObject("reason") { put("type", "string") }
            }
        }
    )

    val ALL = listOf(
        READ_SCREEN, TAP, TYPE, SCROLL, BACK, WAIT_FOR,
        CAPTURE_SCREENSHOT, LOOK_AT, LISTEN_TO_ENGINE, READ_OBD, REPAIR_INFO_LOOKUP, VIN_LOOKUP, PROPOSE_ACTUATION, FINISH_SESSION,
    )

    private fun schema(builder: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit): JsonObject =
        buildJsonObject(builder)
}
