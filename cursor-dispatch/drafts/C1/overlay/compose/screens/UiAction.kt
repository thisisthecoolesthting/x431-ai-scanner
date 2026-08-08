package com.caseforge.scanner.overlay.compose.screens

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
     * The hosting service will dispatch this capability ID to the X431 engine.
     */
    data class TapCapability(val capabilityId: String) : UiAction()
}
