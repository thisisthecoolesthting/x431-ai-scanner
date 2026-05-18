package com.caseforge.scanner.overlay.compose.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.caseforge.scanner.engine.EngineState
import com.caseforge.scanner.engine.ScreenKind
import com.caseforge.scanner.overlay.compose.Spacing
import com.caseforge.scanner.ui.theme.CaseForgeTheme

/**
 * Renders live data stream: PIDs polled from the engine as key-value cards with units.
 *
 * Shows:
 * - Title "Live data"
 * - "Waiting for PIDs" placeholder if none available
 * - Rows of live data with label and formatted numeric value
 *
 * All text and colors routed through MaterialTheme (C1 requirement).
 */
@Composable
fun LiveDataScreen(
    state: EngineState,
    onAction: (UiAction) -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(Spacing.Space14)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Spacing.Space6),
    ) {
        Text(
            "Live data",
            style = MaterialTheme.typography.headlineSmall,
        )
        if (state.liveData.isEmpty()) {
            Text(
                "Waiting for PIDs from the engine…",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        } else {
            state.liveData.forEach { (k, v) ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant,
                            RoundedCornerShape(Spacing.Space6),
                        )
                        .padding(horizontal = Spacing.Space10, vertical = Spacing.Space6),
                ) {
                    Text(
                        k,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        "%.2f".format(v),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun LiveDataScreenEmptyPreview() {
    CaseForgeTheme(mode = "dark") {
        Surface(color = MaterialTheme.colorScheme.background) {
            LiveDataScreen(
                state = EngineState(
                    screen = ScreenKind.LiveDataView,
                    liveData = emptyMap(),
                ),
                onAction = {},
            )
        }
    }
}

@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun LiveDataScreenWithDataPreviewDark() {
    CaseForgeTheme(isDarkMode = true) {
        Surface(color = MaterialTheme.colorScheme.background) {
            LiveDataScreen(
                state = EngineState(
                    screen = ScreenKind.LiveDataView,
                    liveData = mapOf(
                        "Engine RPM" to 1250.5,
                        "Vehicle Speed" to 35.2,
                        "Coolant Temp (°C)" to 85.0,
                        "Intake Air Temp (°C)" to 22.5,
                        "Throttle (%)" to 12.5,
                        "MAP (kPa)" to 45.3,
                    ),
                ),
                onAction = {},
            )
        }
    }
}

private val Spacing.Space6
    get() = 6.dp

private val Spacing.Space10
    get() = 10.dp

private val Spacing.Space14
    get() = 14.dp
