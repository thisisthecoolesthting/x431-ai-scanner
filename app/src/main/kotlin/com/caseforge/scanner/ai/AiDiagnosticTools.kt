package com.caseforge.scanner.ai

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * The tool set Claude can call during an AI Diagnostic session. These drive the standalone
 * OBD link (VciDiagnosticPort), NOT the OEM accessibility agent.
 */
object AiDiagnosticTools {

    private fun schema(builder: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit): JsonObject =
        buildJsonObject(builder)

    val READ_CODES = ClaudeClient.Tool(
        name = "read_codes",
        description = "Read diagnostic trouble codes (DTCs) from the vehicle. Optionally limit to one module.",
        inputSchema = schema {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("module") { put("type", "string"); put("description", "Optional module name, e.g. ECM, TCM, ABS.") }
            }
        },
    )

    val FULL_SCAN = ClaudeClient.Tool(
        name = "full_scan",
        description = "Scan every available module for trouble codes. Use once early to see the whole picture.",
        inputSchema = schema { put("type", "object"); putJsonObject("properties") {} },
    )

    val READ_READINESS = ClaudeClient.Tool(
        name = "read_readiness",
        description = "Read emissions readiness monitors and MIL (check-engine lamp) status.",
        inputSchema = schema { put("type", "object"); putJsonObject("properties") {} },
    )

    val READ_LIVE_PIDS = ClaudeClient.Tool(
        name = "read_live_pids",
        description = "Measure live sensor data. Provide PID hex codes (e.g. 0C=RPM, 05=coolant, 0D=speed, " +
            "11=throttle, 0B=MAP, 0F=intake temp, 06/07=fuel trim). Returns the latest sampled values. " +
            "Reading these also shows them as live gauges to the technician.",
        inputSchema = schema {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("pids") {
                    put("type", "array")
                    put("description", "PID hex codes to read, max 8.")
                    putJsonObject("items") { put("type", "string") }
                }
                putJsonObject("samples") { put("type", "integer"); put("description", "How many samples to average, 1-10.") }
            }
            putJsonArray("required") { add("pids") }
        },
    )

    val ASK_USER = ClaudeClient.Tool(
        name = "ask_user",
        description = "Ask the technician a question and wait for their answer. Use only when the answer " +
            "changes your diagnosis. Offer short answer chips when possible.",
        inputSchema = schema {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("question") { put("type", "string") }
                putJsonObject("chips") {
                    put("type", "array")
                    put("description", "Up to 5 quick-answer options.")
                    putJsonObject("items") { put("type", "string") }
                }
                putJsonObject("speak") { put("type", "boolean"); put("description", "Speak the question out loud.") }
            }
            putJsonArray("required") { add("question") }
        },
    )

    val FINISH_SESSION = ClaudeClient.Tool(
        name = "finish_session",
        description = "Conclude the diagnosis with the root cause, confidence (0-1), recommended repair, " +
            "and the evidence you relied on. This ends the session.",
        inputSchema = schema {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("root_cause") { put("type", "string") }
                putJsonObject("confidence") { put("type", "number") }
                putJsonObject("recommended_repair") { put("type", "string") }
                putJsonObject("summary") { put("type", "string") }
                putJsonObject("supporting_evidence") {
                    put("type", "array")
                    putJsonObject("items") { put("type", "string") }
                }
            }
            putJsonArray("required") { add("root_cause"); add("confidence"); add("recommended_repair") }
        },
    )

    val ALL = listOf(READ_CODES, FULL_SCAN, READ_READINESS, READ_LIVE_PIDS, ASK_USER, FINISH_SESSION)
}

/** Human-readable label + unit for the common PIDs (for gauges and tool results). */
object AiPidInfo {
    data class Info(val label: String, val unit: String, val min: Double, val max: Double)

    val MAP: Map<String, Info> = mapOf(
        "0C" to Info("RPM", "rpm", 0.0, 7000.0),
        "05" to Info("Coolant", "°C", -40.0, 130.0),
        "0D" to Info("Speed", "km/h", 0.0, 200.0),
        "11" to Info("Throttle", "%", 0.0, 100.0),
        "0B" to Info("MAP", "kPa", 0.0, 255.0),
        "0F" to Info("Intake", "°C", -40.0, 130.0),
        "04" to Info("Eng load", "%", 0.0, 100.0),
        "06" to Info("STFT B1", "%", -100.0, 100.0),
        "07" to Info("LTFT B1", "%", -100.0, 100.0),
        "10" to Info("MAF", "g/s", 0.0, 250.0),
        "42" to Info("Voltage", "V", 0.0, 16.0),
    )

    fun info(pid: String): Info {
        val key = pid.trim().uppercase().removePrefix("0X").padStart(2, '0')
        return MAP[key] ?: Info(key, "", 0.0, 100.0)
    }

    fun fill(pid: String, value: Double): Float {
        val i = info(pid)
        val span = (i.max - i.min)
        if (span <= 0.0) return 0f
        return (((value - i.min) / span).toFloat()).coerceIn(0f, 1f)
    }
}
