package com.caseforge.scanner.ai

import kotlinx.serialization.Serializable

@Serializable
data class RecallMatch(
    val campaignId: String,
    val summary: String,
    val consequence: String = "",
    val relatedDtcs: List<String> = emptyList(),
    val isOpenForVin: Boolean = false,
    val manufacturer: String = "",
    val modelYear: Int = 0,
    val make: String = "",
    val model: String = "",
)
