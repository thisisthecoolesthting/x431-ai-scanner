package com.caseforge.scanner.planb.programming

import kotlinx.serialization.Serializable

/**
 * One Tier 4 (programming / flash-style) checklist entry from bundled JSON.
 * Applies are blocked in-app; entries are informational and align with authorized partner workflows.
 */
@Serializable
data class FlashOp(
    val id: String = "",
    val title: String = "",
    val description: String = "",
    val applyMode: String = "blocked",
    val partnerHandoff: Boolean = true,
    /** When set, aligns with [com.caseforge.scanner.planb.immo.SkreemModule.CAPABILITY_ID] in capabilities.json. */
    val capabilityId: String? = null,
) {
    fun isSkreemEntry(): Boolean =
        capabilityId == com.caseforge.scanner.planb.immo.SkreemModule.CAPABILITY_ID ||
            id.contains("skreem", ignoreCase = true) ||
            id.contains("skim", ignoreCase = true)
}
