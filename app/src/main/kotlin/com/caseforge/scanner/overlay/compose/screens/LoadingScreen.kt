package com.caseforge.scanner.overlay.compose.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.caseforge.scanner.engine.EngineState
import com.caseforge.scanner.engine.ScreenKind
import com.caseforge.scanner.overlay.compose.Spacing
import com.caseforge.scanner.ui.theme.CaseForgeTheme

/**
 * Renders a loading/progress screen while the X431 engine is performing a long operation
 * (e.g. a full system scan).
 *
 * Polish improvements:
 * - CircularProgressIndicator centered
 * - "Working..." label below in titleMedium
 * - Fading "current step" subtitle in bodySmall showing EngineState.currentMenuPath if present
 *
 * All text and colors routed through MaterialTheme (C1 requirement).
 */
@Composable
fun LoadingScreen(
    state: EngineState,
    onAction: (UiAction) -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(Spacing.Space20),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(64.dp))

        Spacer(Modifier.height(Spacing.Space20))

        Text(
            "Working...",
            style = MaterialTheme.typography.titleMedium,
        )

        Spacer(Modifier.height(Spacing.Space6))

        Text(
            state.currentMenuPath.joinToString(" › ").ifBlank { "Talking to every module" },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun LoadingScreenPreview() {
    CaseForgeTheme(mode = "dark") {
        Surface(color = MaterialTheme.colorScheme.background) {
            LoadingScreen(
                state = EngineState(
                    screen = ScreenKind.FullScanProgress,
                    currentMenuPath = listOf("Engine", "DTC", "Reading codes"),
                ),
                onAction = {},
            )
        }
    }
}

private val Spacing.Space6
    get() = 6.dp

private val Spacing.Space20
    get() = 20.dp
