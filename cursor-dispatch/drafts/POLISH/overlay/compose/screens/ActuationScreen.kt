package com.caseforge.scanner.overlay.compose.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.caseforge.scanner.engine.EngineState
import com.caseforge.scanner.engine.ScreenKind
import com.caseforge.scanner.overlay.compose.Spacing
import com.caseforge.scanner.ui.theme.CaseForgeTheme

/**
 * Renders the bidirectional actuation/test screen.
 *
 * This screen is shown while the X431 engine is actively performing a two-way
 * actuator control test (e.g. toggling a solenoid, running a fuel pump, etc.).
 *
 * Shows:
 * - Title indicating actuation is active
 * - Warning message to watch the vehicle for expected response
 * - Guidance to dismiss if anything looks wrong
 *
 * All text and colors routed through MaterialTheme (C1 requirement).
 */
@Composable
fun ActuationScreen(
    state: EngineState,
    onAction: (UiAction) -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(Spacing.Space20),
        verticalArrangement = Arrangement.spacedBy(Spacing.Space8),
    ) {
        Text(
            "Actuation test",
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            "Bidirectional control is active. Watch the vehicle for the expected response. " +
                "If anything looks wrong, dismiss the overlay and use X431 directly.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ActuationScreenPreview() {
    CaseForgeTheme(mode = "dark") {
        Surface(color = MaterialTheme.colorScheme.background) {
            ActuationScreen(
                state = EngineState(
                    screen = ScreenKind.ActuationTest,
                ),
                onAction = {},
            )
        }
    }
}

private val Spacing.Space8
    get() = 8.dp

private val Spacing.Space20
    get() = 20.dp
