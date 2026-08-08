package com.caseforge.scanner.planb.coding

import kotlinx.serialization.Serializable

/**
 * One reversible coding operation from bundled checklist JSON.
 * [applyMode] stays `stub` until gateway session work (G4) can execute writes safely.
 */
@Serializable
data class ReversibleCodingOp(
    val id: String = "",
    val title: String = "",
    val description: String = "",
    val rollbackSupported: Boolean = true,
    val applyMode: String = "stub",
)

@Serializable
data class CodingChecklist(
    val schemaVersion: Int = 1,
    val marqueId: String = "",
    val entries: List<ReversibleCodingOp> = emptyList(),
)
