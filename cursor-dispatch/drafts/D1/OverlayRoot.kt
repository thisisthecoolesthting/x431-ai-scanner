@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.caseforge.scanner.overlay.compose

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Minimize
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.awaitFirstDown
import androidx.compose.ui.input.pointer.awaitLongPressOrCancellation
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.caseforge.scanner.engine.EngineState
import com.caseforge.scanner.engine.HealthState
import com.caseforge.scanner.engine.ScreenKind
import com.caseforge.scanner.overlay.compose.screens.ActuationScreen
import com.caseforge.scanner.overlay.compose.screens.ErrorScreen
import com.caseforge.scanner.overlay.compose.screens.LiveDataScreen
import com.caseforge.scanner.overlay.compose.screens.LoadingScreen
import com.caseforge.scanner.overlay.compose.screens.ModuleListScreen
import com.caseforge.scanner.overlay.compose.screens.ReportScreen
import com.caseforge.scanner.overlay.compose.screens.UiAction
import com.caseforge.scanner.ui.theme.CaseForgeTheme

/**
 * Root composable rendered inside the full-screen overlay window. This is what the
 * technician actually sees all day — X431 is hidden behind it.
 *
 * Reads [engineState] (live from the scraper) and delegates rendering to the appropriate
 * screen composable in screens/. All user actions emit [UiAction] events through callbacks.
 *
 * Preserves A3's errorBanner slot at the very top of the overlay column, above the
 * [OverlayTopBar]. The banner is shown only when [HealthState.isHealthy] is false.
 *
 * ## D1 additions
 * - Root Surface now wraps a pointerInput modifier that detects 3-second long-press on dead space.
 * - [onEmergencyDismiss] callback is wired from FullScreenOverlayService to handle the dismiss action.
 * - Buttons and interactive Material3 elements consume press events first, so dismiss only fires
 *   on non-interactive areas.
 */
@Composable
fun OverlayRoot(
    engineState: EngineState,
    alpha: Float,
    onMinimize: () -> Unit,
    onDismiss: () -> Unit,
    onPeek: () -> Unit,
    onCapability: (String) -> Unit,
    onEmergencyDismiss: () -> Unit,
    healthState: HealthState? = null,
) {
    CaseForgeTheme(mode = "dark") {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .alpha(alpha)
                .pointerInput(Unit) {
                    // D1: 3-second press-and-hold on dead space dismisses the overlay.
                    // Buttons and interactive elements consume press events first (pointerEventPass = Main),
                    // so this only fires on non-interactive areas (gaps, empty space).
                    awaitPointerEventScope {
                        while (true) {
                            val down = awaitFirstDown(pass = PointerEventPass.Main)
                            val longPress = awaitLongPressOrCancellation(
                                down.id,
                                timeoutMillis = 3000
                            )
                            if (longPress != null) {
                                // 3-second hold completed without cancellation or drag.
                                onEmergencyDismiss()
                            }
                        }
                    }
                },
            color = MaterialTheme.colorScheme.background,
        ) {
            Column(Modifier.fillMaxSize()) {

                // A3: health banner ABOVE the top bar so it is always visible.
                healthState
                    ?.takeIf { !it.isHealthy }
                    ?.lastError
                    ?.let { msg -> HealthErrorBanner(msg) }

                OverlayTopBar(
                    state = engineState,
                    onMinimize = onMinimize,
                    onPeek = onPeek,
                    onDismiss = onDismiss,
                )

                // Engine-level error banner (e.g. capability dispatch failures).
                engineState.errorBanner?.let { msg ->
                    ErrorBanner(msg)
                }

                Box(Modifier.weight(1f)) {
                    ScreenRouter(
                        state = engineState,
                        onAction = { action ->
                            when (action) {
                                is UiAction.TapCapability -> onCapability(action.capabilityId)
                                // Other UiAction types handled by FullScreenOverlayService or ViewModel
                            }
                        },
                    )
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
 * Uses Material3 [colorScheme.errorContainer] / [colorScheme.onErrorContainer].
 */
@Composable
private fun HealthErrorBanner(message: String) {
    Surface(color = MaterialTheme.colorScheme.errorContainer) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
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
        }
    }
}

// ---------------------------------------------------------------------------
//  Top bar with action buttons
// ---------------------------------------------------------------------------

@Composable
private fun OverlayTopBar(
    state: EngineState,
    onMinimize: () -> Unit,
    onPeek: () -> Unit,
    onDismiss: () -> Unit,
) {
    TopAppBar(
        title = {
            Column {
                Text("Launch AI", fontWeight = FontWeight.Bold)
                Text(
                    state.screen::class.simpleName ?: "—",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        actions = {
            IconButton(onClick = onPeek) {
                Icon(Icons.Default.Visibility, contentDescription = "Peek at X431")
            }
            IconButton(onClick = onMinimize) {
                Icon(Icons.Default.Minimize, contentDescription = "Minimize to bubble")
            }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = "Dismiss overlay")
            }
        },
    )
}

@Composable
private fun ErrorBanner(msg: String) {
    Surface(color = MaterialTheme.colorScheme.errorContainer) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
            Text(msg, color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodySmall)
        }
    }
}
