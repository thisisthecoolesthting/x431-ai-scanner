package com.caseforge.scanner.planb.programming

import kotlinx.serialization.Serializable

@Serializable
data class ProgrammingChecklist(
    val schemaVersion: Int = 1,
    val marqueId: String = "",
    val entries: List<FlashOp> = emptyList(),
    /** Present on SKREEM overlay asset; marque checklists may omit. */
    val capabilityId: String? = null,
)
