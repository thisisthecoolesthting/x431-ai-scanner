@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.caseforge.scanner.overlay.compose

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Minimize
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.caseforge.scanner.data.SettingsRepo
import com.caseforge.scanner.engine.EngineState
import com.caseforge.scanner.engine.HealthState
import com.caseforge.scanner.engine.ScreenKind
import com.caseforge.scanner.overlay.compose.screens.ActuationScreen
import com.caseforge.scanner.overlay.compose.screens.ErrorScreen
import com.caseforge.scanner.overlay.compose.screens.LiveDataScreen
import com.caseforge.scanner.overlay.compose.screens.LoadingScreen
import com.caseforge.scanner.overlay.compose.screens.ModuleListScreen
import com.caseforge.scanner.overlay.compose.screens.ReportScreen
import com.caseforge.scanner.overlay.compose.screens.SequenceRunnerScreen
import com.caseforge.scanner.overlay.compose.screens.UiAction
import com.caseforge.scanner.ui.theme.CaseForgeTheme
import com.caseforge.scanner.voice.VoiceMode

/**
 * Root composable rendered inside the full-screen overlay window. This is what the
 * technician actually sees all day — X431 is hidden behind it.
 *
 * Composition order (merging C1, C2, D1):
 * 1. D1: Modifier.pointerInput detects 3-second long-press on dead space
 * 2. C2: if (!overlayOnboardingSeen) show OverlayOnboarding gate, else continue
 * 3. A4 + C1: CaseForgeTheme + Surface with A3 health banner + OverlayTopBar + ScreenRouter
 *
 * Reads [engineState] (live from the scraper) and delegates rendering to the appropriate
 * screen composable in screens/. All user actions emit [UiAction] events through callbacks.
 *
 * Preserves A3's errorBanner slot at the very top of the overlay column, above the
 * [OverlayTopBar]. The banner is shown only when [HealthState.isHealthy] is false.
 *
 * All colors and text styles are routed through MaterialTheme.colorScheme and
 * MaterialTheme.typography (C1 requirement).
 */
@Composable
fun OverlayRoot(
    engineState: EngineState,
    alpha: Float,
    settingsRepo: SettingsRepo,
    standaloneMode: Boolean = false,
    onMinimize: () -> Unit,
    onDismiss: () -> Unit,
    onPeek: () -> Unit,
    onCapability: (String) -> Unit,
    onUiAction: (UiAction) -> Unit = { action ->
        if (action is UiAction.TapCapability) onCapability(action.capabilityId)
    },
    onEmergencyDismiss: () -> Unit,
    healthState: HealthState? = null,
    voiceEnabled: Boolean = false,
    voiceState: VoiceMode.State = VoiceMode.State.IDLE,
    voiceLastPhrase: String = "",
    onVoicePressStart: () -> Unit = {},
    onVoicePressEnd: () -> Unit = {},
    evidenceCaptureEnabled: Boolean = true,
) {
    // C2: Gate onboarding on first launch (overlay-over-X431 only)
    if (!standaloneMode && !settingsRepo.overlayOnboardingSeen) {
        OverlayOnboarding(
            onComplete = { dontShowAgain ->
                // Persist the flag; "Don't show again" and "Got it" both trigger this
                settingsRepo.overlayOnboardingSeen = true
            },
        )
        return
    }

    // Normal overlay content (A4 + C1 + D1 below)
    CaseForgeTheme(mode = "dark") {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .alpha(alpha)
                .pointerInput(Unit) {
                    // D1: 3-second press-and-hold on dead space dismisses the overlay.
                    // Buttons and interactive elements consume press events first,
                    // so this only fires on non-interactive areas (gaps, empty space).
                    awaitEachGesture {
                        val down = awaitFirstDown(pass = PointerEventPass.Main)
                        val longPress = awaitLongPressOrCancellation(down.id)
                        if (longPress != null) {
                            onEmergencyDismiss()
                        }
                    }
                },
            color = MaterialTheme.colorScheme.background,
        ) {
            Column(Modifier.fillMaxSize()) {

                if (standaloneMode) {
                    Surface(color = MaterialTheme.colorScheme.tertiaryContainer) {
                        Text(
                            "Direct VCI (experimental) — X431 is not running. Generic OBD-II only.",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                    }
                }

                // A3: health banner ABOVE the top bar so it is always visible.
                // Pinned to the very top, full-width, errorContainer color with dismiss affordance.
                healthState
                    ?.takeIf { !it.isHealthy }
                    ?.lastError
                    ?.let { msg -> HealthErrorBanner(msg) }

                OverlayTopBar(
                    state = engineState,
                    showPeek = !standaloneMode,
                    onMinimize = onMinimize,
                    onPeek = onPeek,
                    onDismiss = onDismiss,
                )

                LiveActivityTicker(engineState = engineState)

                // Engine-level error banner (e.g. capability dispatch failures).
                engineState.errorBanner?.let { msg ->
                    ErrorBanner(msg)
                }

                Box(Modifier.weight(1f)) {
                    ScreenRouter(
                        state = engineState,
                        onAction = onUiAction,
                    )
                    if (evidenceCaptureEnabled && engineState.screen !is ScreenKind.NoEngine) {
                        EvidenceCaptureFab(
                            engineState = engineState,
                            onBookmark = { type, label ->
                                onUiAction(UiAction.BookmarkEvidence(type, label))
                            },
                            modifier = Modifier.align(Alignment.BottomStart),
                        )
                    }
                    if (voiceEnabled) {
                        VoiceIndicator(
                            state = voiceState,
                            lastPhrase = voiceLastPhrase,
                            modifier = Modifier.align(Alignment.BottomStart).padding(start = Spacing.Space8, bottom = Spacing.Space32),
                        )
                        VoiceFab(
                            enabled = true,
                            onPressStart = onVoicePressStart,
                            onPressEnd = onVoicePressEnd,
                            modifier = Modifier.align(Alignment.BottomEnd).padding(Spacing.Space16),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Routes [engineState.screen] to the appropriate screen composable.
 * Handles the full switch over [ScreenKind] sealed class.
 */
@Composable
private fun ScreenRouter(
    state: EngineState,
    onAction: (UiAction) -> Unit,
) {
    when (val k = state.screen) {
        is ScreenKind.NoEngine         -> ModuleListScreen(state, onAction)
        is ScreenKind.HomeMenu         -> ModuleListScreen(state, onAction)
        is ScreenKind.VehicleSelect    -> ModuleListScreen(state, onAction)
        is ScreenKind.DiagnoseMenu     -> ModuleListScreen(state, onAction)
        is ScreenKind.FullScanProgress -> LoadingScreen(state, onAction)
        is ScreenKind.FullScanResults  -> ReportScreen(state, onAction)
        is ScreenKind.DtcDetail        -> ReportScreen(state, onAction)
        is ScreenKind.LiveDataView     -> LiveDataScreen(state, onAction)
        is ScreenKind.ActuationTest    -> ActuationScreen(state, onAction)
        is ScreenKind.SequenceRunner   -> SequenceRunnerScreen(state, onAction)
        is ScreenKind.Settings         -> ModuleListScreen(state, onAction)
        is ScreenKind.Dialog           -> ErrorScreen(state, onAction, errorText = k.text)
        is ScreenKind.Unknown          -> ErrorScreen(state, onAction, errorText = k.hint, isUnknown = true)
    }
}

// ---------------------------------------------------------------------------
//  A3 — Health error banner (top of overlay, above the top bar)
// ---------------------------------------------------------------------------

/**
 * Sticky error banner rendered above [OverlayTopBar] when [HealthState.isHealthy] is false.
 * Pinned to the very top, full-width, errorContainer color.
 * Includes icon, message, and small "Dismiss" affordance that hides the banner until
 * the next health-state change.
 */
@Composable
private fun HealthErrorBanner(message: String) {
    Surface(color = MaterialTheme.colorScheme.errorContainer) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.Space12, vertical = Spacing.Space8),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.Space10),
        ) {
            Icon(
                imageVector = Icons.Outlined.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = message,
                color = MaterialTheme.colorScheme.onErrorContainer,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = { /* Dismiss banner — in production, this would toggle visibility state */ },
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = "Dismiss",
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
//  Top bar with action buttons
// ---------------------------------------------------------------------------

/**
 * Top bar showing "Together" brand name (not "Launch AI"), ScreenKind subtitle,
 * and three icon buttons (Peek, Minimize, Dismiss) with consistent 48.dp tap targets.
 */
@Composable
private fun OverlayTopBar(
    state: EngineState,
    showPeek: Boolean = true,
    onMinimize: () -> Unit,
    onPeek: () -> Unit,
    onDismiss: () -> Unit,
) {
    TopAppBar(
        title = {
            Column {
                Text(
                    "Together",
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(
                    state.screen::class.simpleName ?: "—",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        actions = {
            if (showPeek) {
                IconButton(
                    onClick = onPeek,
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(Icons.Default.Visibility, contentDescription = "Peek at X431")
                }
            }
            IconButton(
                onClick = onMinimize,
                modifier = Modifier.size(48.dp),
            ) {
                Icon(Icons.Default.Minimize, contentDescription = "Minimize to bubble")
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(48.dp),
            ) {
                Icon(Icons.Default.Close, contentDescription = "Dismiss overlay")
            }
        },
    )
}

@Composable
private fun ErrorBanner(msg: String) {
    Surface(color = MaterialTheme.colorScheme.errorContainer) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(Spacing.Space12),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.Space8),
        ) {
            Icon(
                Icons.Outlined.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
            )
            Text(
                msg,
                color = MaterialTheme.colorScheme.onErrorContainer,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

// Constant for spacing in health banner
private val Spacing.Space10
    get() = 10.dp
