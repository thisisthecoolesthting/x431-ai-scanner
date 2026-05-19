package com.caseforge.scanner.overlay.compose.screens

import com.caseforge.scanner.evidence.EvidenceType

/**
 * Sealed class of user actions emitted by screen composables.
 *
 * No business logic lives in screens — they are stateless renderers of [EngineState].
 * All interactivity goes through [UiAction] events that bubble to [OverlayRoot] or
 * the hosting ViewModel/Service, which resolves them against the engine.
 */
sealed class UiAction {
    /**
     * User tapped a capability card on the home/menu screen.
     * The hosting service will dispatch this capability ID to the OEM diagnostic app engine.
     */
    data class TapCapability(val capabilityId: String) : UiAction()

    data class RunSequence(val sequenceId: String) : UiAction()

    data class AdvanceSequence(val sequenceId: String) : UiAction()

    data object AcceptSuggestedTest : UiAction()

    data object DeclineSuggestedTest : UiAction()

    data class BookmarkEvidence(val type: EvidenceType, val label: String) : UiAction()
}
