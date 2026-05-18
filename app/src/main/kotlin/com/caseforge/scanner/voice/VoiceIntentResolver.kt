package com.caseforge.scanner.voice

import com.caseforge.scanner.engine.CapabilityMap
import com.caseforge.scanner.engine.CapabilityMap.Capability
import kotlin.math.min

/**
 * Maps a raw utterance to a typed [VoiceIntent] using keyword and fuzzy matching only.
 */
object VoiceIntentResolver {

    enum class IntentClass {
        ReadCodes, ClearCodes, GraphPid, RunCapability, Dismiss, Peek, Help, Unknown
    }

    data class VoiceIntent(
        val cls: IntentClass,
        val capabilityId: String? = null,
        val extra: String? = null,
        val confidence: Float = 1.0f,
    )

    fun resolve(utterance: String, catalog: List<Capability>): VoiceIntent {
        val normalized = utterance.lowercase().trim()

        systemIntentOrNull(normalized)?.let { return it }
        graphIntentOrNull(normalized)?.let { return it }
        phraseMatchOrNull(normalized)?.let { return it }
        fuzzyMatchOrNull(normalized, catalog)?.let { return it }
        categoryFallbackOrNull(normalized, catalog)?.let { return it }

        return VoiceIntent(IntentClass.Unknown, confidence = 0f)
    }

    private val DISMISS_PHRASES = listOf("dismiss", "close", "cancel", "never mind", "nevermind", "stop listening", "go away", "hide")
    private val PEEK_PHRASES    = listOf("peek", "what's on screen", "what is on screen", "show me", "describe screen", "what do you see")
    private val HELP_PHRASES    = listOf("help", "what can you do", "commands", "what commands", "list commands", "show commands")
    private val READ_PHRASES    = listOf(
        "read codes", "read fault codes", "read dtcs", "read the codes",
        "what codes", "any codes", "check codes", "show codes", "scan codes",
        "read errors", "read faults", "show faults", "show dtcs",
    )
    private val CLEAR_PHRASES   = listOf(
        "clear codes", "clear fault codes", "clear dtcs", "erase codes",
        "delete codes", "reset codes", "clear faults", "erase faults",
    )

    private fun systemIntentOrNull(s: String): VoiceIntent? = when {
        DISMISS_PHRASES.any { s.contains(it) } -> VoiceIntent(IntentClass.Dismiss)
        PEEK_PHRASES.any    { s.contains(it) } -> VoiceIntent(IntentClass.Peek)
        HELP_PHRASES.any    { s.contains(it) } -> VoiceIntent(IntentClass.Help)
        READ_PHRASES.any    { s.contains(it) } -> VoiceIntent(IntentClass.ReadCodes, capabilityId = "read_dtcs")
        CLEAR_PHRASES.any   { s.contains(it) } -> VoiceIntent(IntentClass.ClearCodes, capabilityId = "clear_dtcs")
        else -> null
    }

    private val PID_PHRASE_MAP = mapOf(
        "short term fuel trim bank one"     to Pair("STFT B1", "live_data_fuel_trim_b1"),
        "short term fuel trim bank 1"       to Pair("STFT B1", "live_data_fuel_trim_b1"),
        "stft bank one"                     to Pair("STFT B1", "live_data_fuel_trim_b1"),
        "stft b1"                           to Pair("STFT B1", "live_data_fuel_trim_b1"),
        "long term fuel trim bank one"      to Pair("LTFT B1", "live_data_fuel_trim_b1_lt"),
        "long term fuel trim bank 1"        to Pair("LTFT B1", "live_data_fuel_trim_b1_lt"),
        "ltft bank one"                     to Pair("LTFT B1", "live_data_fuel_trim_b1_lt"),
        "short term fuel trim bank two"     to Pair("STFT B2", "live_data_fuel_trim_b2"),
        "short term fuel trim bank 2"       to Pair("STFT B2", "live_data_fuel_trim_b2"),
        "rpm"                               to Pair("RPM",      "live_data_rpm"),
        "engine rpm"                        to Pair("RPM",      "live_data_rpm"),
        "engine speed"                      to Pair("RPM",      "live_data_rpm"),
        "coolant temp"                      to Pair("ECT",      "live_data_ect"),
        "coolant temperature"               to Pair("ECT",      "live_data_ect"),
        "engine coolant"                    to Pair("ECT",      "live_data_ect"),
        "map sensor"                        to Pair("MAP",      "live_data_map"),
        "manifold pressure"                 to Pair("MAP",      "live_data_map"),
        "maf"                               to Pair("MAF",      "live_data_maf"),
        "mass air flow"                     to Pair("MAF",      "live_data_maf"),
        "throttle position"                 to Pair("TPS",      "live_data_tps"),
        "tps"                               to Pair("TPS",      "live_data_tps"),
        "oxygen sensor bank one"            to Pair("O2 B1S1",  "live_data_o2_b1s1"),
        "o2 bank one"                       to Pair("O2 B1S1",  "live_data_o2_b1s1"),
        "vehicle speed"                     to Pair("VSS",      "live_data_vss"),
        "speed"                             to Pair("VSS",      "live_data_vss"),
        "battery voltage"                   to Pair("Batt V",   "live_data_battery_voltage"),
        "ignition timing"                   to Pair("IGN ADV",  "live_data_ign_advance"),
        "timing advance"                    to Pair("IGN ADV",  "live_data_ign_advance"),
    )

    private val GRAPH_TRIGGERS = listOf("graph", "chart", "plot", "show graph", "show chart", "trend", "live graph")

    private fun graphIntentOrNull(s: String): VoiceIntent? {
        val hasGraphTrigger = GRAPH_TRIGGERS.any { s.contains(it) }
        if (!hasGraphTrigger) return null

        val stripped = GRAPH_TRIGGERS.fold(s) { acc, t -> acc.replace(t, "").trim() }.trim()
        for ((phrase, pair) in PID_PHRASE_MAP) {
            if (s.contains(phrase) || stripped.contains(phrase)) {
                return VoiceIntent(IntentClass.GraphPid, capabilityId = pair.second, extra = pair.first, confidence = 0.95f)
            }
        }
        return VoiceIntent(IntentClass.GraphPid, extra = stripped, confidence = 0.5f)
    }

    private val PHRASE_TABLE: Map<String, String> = mapOf(
        "full scan"            to "full_scan",
        "auto scan"            to "full_scan",
        "run full scan"        to "full_scan",
        "scan all modules"     to "full_scan",
        "scan everything"      to "full_scan",
        "live data"            to "live_data",
        "data stream"          to "live_data",
        "read data stream"     to "live_data",
        "show live data"       to "live_data",
        "show data stream"     to "live_data",
        "freeze frame"         to "freeze_frame",
        "frozen data"          to "freeze_frame",
        "oil reset"            to "oil_reset",
        "oil service reset"    to "oil_reset",
        "reset oil light"      to "oil_reset",
        "epb"                  to "epb",
        "electronic parking brake" to "epb",
        "brake service"        to "epb",
        "retract caliper"      to "epb",
        "steering angle reset" to "sas",
        "sas reset"            to "sas",
        "tpms"                 to "tpms",
        "tpms relearn"         to "tpms",
        "tire pressure relearn" to "tpms",
        "battery registration" to "battery_register",
        "register battery"     to "battery_register",
        "throttle relearn"     to "throttle_relearn",
        "throttle body relearn" to "throttle_relearn",
        "dpf regen"            to "dpf_regen",
        "diesel regen"         to "dpf_regen",
        "injector coding"      to "injector_coding",
        "key programming"      to "key_program",
        "program key"          to "key_program",
        "abs bleed"            to "abs_bleed",
        "brake bleed"          to "abs_bleed",
        "gear learn"           to "gear_learn",
        "transmission learn"   to "gear_learn",
        "suspension calibration" to "suspension",
        "ecu coding"           to "ecu_coding",
        "module programming"   to "module_program",
        "actuation test"       to "actuation",
        "actuator test"        to "actuation",
        "bidirectional test"   to "actuation",
    )

    private fun phraseMatchOrNull(s: String): VoiceIntent? {
        for ((phrase, capId) in PHRASE_TABLE) {
            if (s.contains(phrase)) {
                return VoiceIntent(IntentClass.RunCapability, capabilityId = capId, confidence = 0.9f)
            }
        }
        return null
    }

    private const val FUZZY_THRESHOLD = 4

    fun levenshtein(a: String, b: String): Int {
        val m = a.length; val n = b.length
        val dp = Array(m + 1) { IntArray(n + 1) }
        for (i in 0..m) dp[i][0] = i
        for (j in 0..n) dp[0][j] = j
        for (i in 1..m) for (j in 1..n) {
            dp[i][j] = if (a[i - 1] == b[j - 1]) dp[i - 1][j - 1]
            else 1 + min(dp[i - 1][j], min(dp[i][j - 1], dp[i - 1][j - 1]))
        }
        return dp[m][n]
    }

    private fun fuzzyMatchOrNull(utterance: String, catalog: List<Capability>): VoiceIntent? {
        var bestCap: Capability? = null
        var bestDist = Int.MAX_VALUE
        for (cap in catalog) {
            val dist = levenshtein(utterance, cap.label.lowercase())
            if (dist < bestDist) { bestDist = dist; bestCap = cap }
        }
        if (bestCap != null && bestDist <= FUZZY_THRESHOLD) {
            val conf = 1f - (bestDist.toFloat() / (bestCap.label.length.coerceAtLeast(1)))
            return VoiceIntent(IntentClass.RunCapability, capabilityId = bestCap.id, confidence = conf)
        }
        return null
    }

    private fun categoryFallbackOrNull(s: String, catalog: List<Capability>): VoiceIntent? {
        val category: CapabilityMap.Category? = when {
            s.contains("code") || s.contains("dtc") || s.contains("fault") ->
                CapabilityMap.Category.Codes
            s.contains("live") || s.contains("data") || s.contains("stream") || s.contains("pid") ->
                CapabilityMap.Category.LiveData
            s.contains("service") || s.contains("reset") ->
                CapabilityMap.Category.Service
            s.contains("bidirectional") || s.contains("actuation") ->
                CapabilityMap.Category.Bidirectional
            s.contains("program") || s.contains("flash") ->
                CapabilityMap.Category.Programming
            s.contains("scan") ->
                CapabilityMap.Category.Scan
            else -> null
        }
        val cap = catalog.firstOrNull { it.category == category } ?: return null
        return VoiceIntent(IntentClass.RunCapability, capabilityId = cap.id, confidence = 0.4f)
    }
}
