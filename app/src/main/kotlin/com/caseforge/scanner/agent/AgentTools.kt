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

    /** ISO-15765 / Mode 09 VIN via native OBD stack (Plan B wedge); read-only. */
    val READ_OBD_VIN = ClaudeClient.Tool(
        name = "read_obd_vin",
        description = "Read the vehicle VIN from the ECU using the native OBD-II stack (ISO-TP / Mode 09). " +
                "Read-only. Use when ELM Bluetooth is not in use or you want the same path as Plan B native OBD. " +
                "Appends marqueHint (WMI heuristic) plus marque wedge card summary and enabled tiers from " +
                "marque-wedge-matrix.json when a platform card matches the VIN WMI and model-year window.",
        inputSchema = schema { put("type", "object"); putJsonObject("properties") {} },
    )

    /** Stored (Mode 03) and pending (Mode 07) DTCs via native OBD stack; read-only. */
    val READ_OBD_DTCS = ClaudeClient.Tool(
        name = "read_obd_dtcs",
        description = "Read stored and pending OBD-II DTCs via the native stack (Modes 03 and 07). Read-only. " +
                "Also performs a Mode 09 VIN read when possible to append marqueHint, wedge card summary when the " +
                "multi-marque wedge matrix matches WMI + model year, and enabled tiers.",
        inputSchema = schema { put("type", "object"); putJsonObject("properties") {} },
    )

    /** Small Mode 01 snapshot (RPM, coolant, speed) via native OBD stack; read-only. */
    val READ_OBD_LIVE_SNAPSHOT = ClaudeClient.Tool(
        name = "read_obd_live_snapshot",
        description = "Read a minimal live-data snapshot (engine RPM, coolant °C, vehicle speed km/h) via the native OBD Mode 01 stack. Read-only. " +
                "Also performs a Mode 09 VIN read when possible to append marqueHint, wedge card summary when the " +
                "multi-marque wedge matrix matches WMI + model year, and enabled tiers.",
        inputSchema = schema { put("type", "object"); putJsonObject("properties") {} },
    )

    /**
     * Plan B tier 4: read-only programming reference — echoes the blocked-in-app Tier 4 message plus a count of
     * bundled checklist rows for the detected marque (Ford / Dodge / Jeep from wedge matrix WMI+MY card, then WMI heuristic).
     */
    val READ_PROGRAMMING_STATUS = ClaudeClient.Tool(
        name = "read_programming_status",
        description =
            "Read-only Tier 4 programming status for Plan B with programming turned on in Settings. Does not communicate with " +
                "modules. Returns why in-app Tier 4 is blocked plus how many informational checklist rows are bundled for this " +
                "marque. Pass vin when known.",
        inputSchema = schema {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("vin") {
                    put("type", "string")
                    put("description", "Optional VIN — improves marque detection for checklist row count.")
                }
            }
        },
    )

    /** Read-only tablet USB/BT + permission scan for OBD adapter readiness (Windstar profile default). */
    val SCAN_CONNECTION_READINESS = ClaudeClient.Tool(
        name = "scan_connection_readiness",
        description =
            "Scan the tablet for USB serial OBD adapters (CH340/FTDI/PL2303/CP21xx), paired Bluetooth ELM327 " +
                "devices, and missing permissions. Read-only — does not install drivers. Returns link hints and " +
                "operator steps for the bundled vehicle profile (default ford-windstar-2000). Use before first connect " +
                "when the tech asks about drivers, OTG, or adapter setup.",
        inputSchema = schema {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("vehicle_profile_id") {
                    put("type", "string")
                    put("description", "Bundled profile id, e.g. ford-windstar-2000")
                }
            }
        },
    )

    /**
     * Read-only: lists [capabilities.json] rows merged by [CapabilityCatalogStore], filtered by the marque wedge
     * resolved from VIN + [MarqueWedgeConfig]. Does not run OEM diagnostics.
     */
    val LIST_CAPABILITIES_FOR_VIN = ClaudeClient.Tool(
        name = "list_capabilities_for_vin",
        description =
            "Return capability catalog rows (id, label, category, done_when, path, note) filtered for the current " +
                "marque wedge from a VIN. Display/reference only — never executes OEM menu paths. " +
                "Optional vin; when omitted, uses the fast-workflow last VIN if it is a valid 17-character VIN.",
        inputSchema = schema {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("vin") {
                    put("type", "string")
                    put("description", "Optional 17-character VIN")
                }
            }
        },
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

    val CHECK_FOR_UPDATES = ClaudeClient.Tool(
        name = "check_for_updates",
        description =
            "Polls the Together Car Works live-update channel for newer APK/overlay bundles and returns a short status summary.",
        inputSchema = schema { put("type", "object"); putJsonObject("properties") {} },
    )

    val SYNC_VEHICLE_PROFILES = ClaudeClient.Tool(
        name = "sync_vehicle_profiles",
        description =
            "Downloads or refreshes bundled Plan B JSON vehicle profile overlays/bundles from the Together Car Works update server.",
        inputSchema = schema { put("type", "object"); putJsonObject("properties") {} },
    )

    val ANALYZE_SESSION_PHOTOS = ClaudeClient.Tool(
        name = "analyze_session_photos",
        description =
            "Re-run Claude vision on the active New Session wizard photos (engine bay, door jamb, dashboard). " +
                "Returns advisory photo insights — visual estimates only; technician must verify on vehicle. " +
                "Requires an active session from the New Session flow.",
        inputSchema = schema {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("force_refresh") {
                    put("type", "boolean")
                    put("description", "When true, always call vision even if cached insights exist.")
                }
            }
        },
    )

    val APPEND_GOLDEN_EVENT = ClaudeClient.Tool(
        name = "append_golden_event",
        description =
            "Append one golden-log JSON line (CAN/UI correlate capture schema) to app-private storage. " +
                "The events file rides inside LAN uploads (`tcw-golden-capture/golden_events.jsonl`). dir must be TX or RX.",
        inputSchema = schema {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("ts") {
                    put("type", "string")
                    put(
                        "description",
                        "ISO-8601 timestamp; omit to default now UTC.",
                    )
                }
                putJsonObject("dir") { put("type", "string") }
                putJsonObject("canId") { put("type", "string") }
                putJsonObject("payload") { put("type", "string") }
                putJsonObject("uiContext") { put("type", "string") }
                putJsonObject("oemPackage") {
                    put("type", "string")
                    put("description", "Foreground OEM/Android diagnostics package name when known.")
                }
                putJsonObject("windowTitle") {
                    put("type", "string")
                    put("description", "Accessibility window root title when observable.")
                }
                putJsonObject("actionId") {
                    put("type", "string")
                    put("description", "Optional playbook step/action correlation key.")
                }
            }
        },
    )

    private val CORE_TOOLS = listOf(
        READ_SCREEN, TAP, TYPE, SCROLL, BACK, WAIT_FOR,
        CAPTURE_SCREENSHOT, LOOK_AT, LISTEN_TO_ENGINE, READ_OBD,
        CHECK_FOR_UPDATES, SYNC_VEHICLE_PROFILES, SCAN_CONNECTION_READINESS,
        LIST_CAPABILITIES_FOR_VIN,
        APPEND_GOLDEN_EVENT,
        ANALYZE_SESSION_PHOTOS,
        REPAIR_INFO_LOOKUP, VIN_LOOKUP, PROPOSE_ACTUATION, FINISH_SESSION,
    )

    private val NATIVE_OBD_TOOLS = listOf(
        READ_OBD_VIN, READ_OBD_DTCS, READ_OBD_LIVE_SNAPSHOT,
    )

    /** Tools exposed to Claude; native ISO-TP OBD readers when experimental is on; programming status when tier 4 reference is enabled. */
    fun toolList(nativeObdExperimental: Boolean, planbProgramming: Boolean = false): List<ClaudeClient.Tool> =
        CORE_TOOLS +
            (if (nativeObdExperimental) NATIVE_OBD_TOOLS else emptyList()) +
            (if (planbProgramming) listOf(READ_PROGRAMMING_STATUS) else emptyList())

    val ALL: List<ClaudeClient.Tool> get() = CORE_TOOLS

    private fun schema(builder: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit): JsonObject =
        buildJsonObject(builder)
}
