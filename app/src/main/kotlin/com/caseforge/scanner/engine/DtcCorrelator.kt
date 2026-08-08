package com.caseforge.scanner.engine

import kotlinx.serialization.Serializable

/**
 * Stateless cross-module DTC correlator using workshop heuristics (no API calls).
 */
object DtcCorrelator {

    @Serializable
    data class RootCauseHypothesis(
        val cause: String,
        val affectedModules: List<String>,
        val confidence: Float,
        val evidencePoints: List<String>,
        val relatedCodes: List<String>,
        val capabilityHint: String? = null,
    )

    fun correlate(dtcs: List<ScrapedDtc>): RootCauseHypothesis? {
        if (dtcs.isEmpty()) return null
        val codes = dtcs.map { it.code.uppercase() }.toSet()
        val byModule = dtcs.groupBy { it.module?.ifBlank { null } ?: "Unknown" }

        // U-code + P-code: network fault masking sensor issue
        val uCodes = codes.filter { it.startsWith("U") }
        val pCodes = codes.filter { it.startsWith("P") }
        if (uCodes.isNotEmpty() && pCodes.isNotEmpty()) {
            val pPrimary = pCodes.first()
            return RootCauseHypothesis(
                cause = "Communication fault ($uCodes) likely cascading from primary powertrain code $pPrimary",
                affectedModules = byModule.keys.toList(),
                confidence = 0.72f,
                evidencePoints = listOf(
                    "U-codes often appear when a module stops receiving valid data from ECM.",
                    "Address the P-code root fault before chasing downstream modules.",
                ),
                relatedCodes = (uCodes + pCodes).take(6),
                capabilityHint = "live_data",
            )
        }

        // Low voltage + body codes
        if (codes.contains("P0562") || codes.any { it.startsWith("B") }) {
            return RootCauseHypothesis(
                cause = "Low system voltage — weak battery or alternator before replacing modules",
                affectedModules = byModule.keys.toList(),
                confidence = 0.78f,
                evidencePoints = listOf(
                    "P0562 and multiple B-codes commonly clear after charging system repair.",
                    "Load-test battery and measure charging voltage at 2000 rpm.",
                ),
                relatedCodes = codes.filter { it == "P0562" || it.startsWith("B") }.take(6),
                capabilityHint = "read_dtcs",
            )
        }

        // MAF / boost pair
        if (codes.any { it in setOf("P0101", "P0102", "P0103") } &&
            codes.any { it in setOf("P0299", "P0234") }
        ) {
            return RootCauseHypothesis(
                cause = "MAF sensor or intake leak causing underboost and fuel trim faults",
                affectedModules = byModule.keys.toList(),
                confidence = 0.75f,
                evidencePoints = listOf(
                    "MAF signal errors often trigger boost-related P-codes.",
                    "Inspect MAF wiring and intake boots before turbo work.",
                ),
                relatedCodes = codes.filter {
                    it in setOf("P0101", "P0102", "P0103", "P0299", "P0234")
                },
                capabilityHint = "live_data",
            )
        }

        // Thermostat / coolant
        if (codes.contains("P0128")) {
            return RootCauseHypothesis(
                cause = "Coolant thermostat or ECT sensor — engine not reaching operating temperature",
                affectedModules = listOfNotNull(byModule.keys.firstOrNull()),
                confidence = 0.7f,
                evidencePoints = listOf(
                    "P0128 indicates thermostat stuck open or slow warm-up.",
                    "Verify ECT PID tracks ambient-to-operating range on a road test.",
                ),
                relatedCodes = listOf("P0128"),
                capabilityHint = "live_data",
            )
        }

        // Multi-module same-network
        if (byModule.size >= 3 && uCodes.size >= 2) {
            return RootCauseHypothesis(
                cause = "Widespread network communication loss — check CAN bus wiring and grounds first",
                affectedModules = byModule.keys.toList(),
                confidence = 0.65f,
                evidencePoints = listOf(
                    "${byModule.size} modules reporting faults with ${uCodes.size} U-codes.",
                    "Inspect OBD-II connector and main ground straps.",
                ),
                relatedCodes = uCodes.take(6),
                capabilityHint = "read_dtcs",
            )
        }

        return null
    }
}
