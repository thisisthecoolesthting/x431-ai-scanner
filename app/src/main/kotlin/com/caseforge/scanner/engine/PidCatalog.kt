package com.caseforge.scanner.engine

import java.util.Locale

/**
 * Known OBD-II live PIDs for display labels, units, and the default "fast" poll set.
 *
 * Keys in [EngineState.liveData] may be hex ids ("0C"), short names ("RPM"), or scraped
 * OEM labels ("Engine RPM") — [resolve] normalizes them for UI formatting.
 */
data class PidDefinition(
    val id: String,
    val label: String,
    val unit: String,
    val aliases: Set<String> = emptySet(),
    /** Decimal places when formatting the live sample (integers use 0). */
    val decimals: Int = 1,
) {
    val pollId: String get() = id.uppercase(Locale.US)
}

object PidCatalog {

    /** Smooth-refresh default: six high-value PIDs most techs reach for first. */
    val FAST_DEFAULT: List<PidDefinition> = listOf(
        PidDefinition(
            id = "0C",
            label = "Engine RPM",
            unit = "rpm",
            aliases = setOf("RPM", "010C"),
            decimals = 0,
        ),
        PidDefinition(
            id = "05",
            label = "Coolant temp",
            unit = "°C",
            aliases = setOf("Coolant Temp", "ECT", "Engine Coolant Temperature"),
            decimals = 0,
        ),
        PidDefinition(
            id = "0D",
            label = "Vehicle speed",
            unit = "km/h",
            aliases = setOf("Vehicle Speed", "Speed", "VSS"),
            decimals = 0,
        ),
        PidDefinition(
            id = "06",
            label = "STFT B1",
            unit = "%",
            aliases = setOf(
                "Short Term Fuel Trim",
                "Short-term fuel trim",
                "STFT",
                "STFT B1",
            ),
            decimals = 1,
        ),
        PidDefinition(
            id = "07",
            label = "LTFT B1",
            unit = "%",
            aliases = setOf(
                "Long Term Fuel Trim",
                "Long-term fuel trim",
                "LTFT",
                "LTFT B1",
            ),
            decimals = 1,
        ),
        PidDefinition(
            id = "42",
            label = "Battery voltage",
            unit = "V",
            aliases = setOf(
                "Battery Voltage",
                "Control Module Voltage",
                "Module voltage",
            ),
            decimals = 2,
        ),
    )

    val FAST_DEFAULT_POLL_IDS: List<String> = FAST_DEFAULT.map { it.pollId }

    val PERFORMANCE_HINT: String =
        "For smooth refresh, poll only the PIDs you are watching. Extra parameters slow the stream."

    fun resolve(key: String): PidDefinition? {
        val normalized = normalizeKey(key)
        return FAST_DEFAULT.firstOrNull { def ->
            normalizeKey(def.id) == normalized ||
                normalizeKey(def.label) == normalized ||
                def.aliases.any { normalizeKey(it) == normalized }
        }
    }

    fun canonicalLabel(key: String): String = resolve(key)?.label ?: stripEmbeddedUnit(key)

    fun formatValue(key: String, value: Double): String {
        val def = resolve(key)
        val decimals = def?.decimals ?: if (value == value.toLong().toDouble()) 0 else 1
        return if (decimals == 0) {
            value.toLong().toString()
        } else {
            String.format(Locale.US, "%.${decimals}f", value)
        }
    }

    fun unitFor(key: String): String {
        resolve(key)?.unit?.let { return it }
        return embeddedUnit(key) ?: ""
    }

    fun matches(key: String, def: PidDefinition): Boolean = resolve(key)?.id == def.id

    fun orderedLiveEntries(liveData: Map<String, Double>): List<Pair<String, Double>> {
        val consumed = mutableSetOf<String>()
        val ordered = mutableListOf<Pair<String, Double>>()
        for (def in FAST_DEFAULT) {
            val entry = liveData.entries.firstOrNull { (k, _) ->
                matches(k, def) && k !in consumed
            }
            if (entry != null) {
                consumed += entry.key
                ordered += entry.key to entry.value
            }
        }
        liveData.entries
            .filter { it.key !in consumed }
            .sortedBy { it.key.lowercase(Locale.US) }
            .forEach { ordered += it.key to it.value }
        return ordered
    }

    private fun normalizeKey(key: String): String =
        key.trim()
            .removePrefix("0x")
            .removePrefix("0X")
            .replace(Regex("\\s*\\([^)]*\\)\\s*$"), "")
            .lowercase(Locale.US)

    private fun stripEmbeddedUnit(key: String): String =
        key.replace(Regex("\\s*\\([^)]*\\)\\s*$"), "").trim()

    private fun embeddedUnit(key: String): String? {
        val match = Regex("\\(([^)]+)\\)\\s*$").find(key.trim()) ?: return null
        return match.groupValues[1].trim().takeIf { it.isNotEmpty() }
    }
}
