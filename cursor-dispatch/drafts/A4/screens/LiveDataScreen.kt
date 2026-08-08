package com.caseforge.scanner.overlay.compose.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.caseforge.scanner.engine.EngineState
import com.caseforge.scanner.engine.ScreenKind
import com.caseforge.scanner.ui.theme.CaseForgeTheme

/**
 * Renders live data stream: PIDs polled from the engine as key-value cards with units.
 *
 * Shows:
 * - Title "Live data"
 * - "Waiting for PIDs" placeholder if none available
 * - Rows of live data with label and formatted numeric value
 */
@Composable
fun LiveDataScreen(
    state: EngineState,
    onAction: (UiAction) -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(14.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("Live data", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        if (state.liveData.isEmpty()) {
            Text(
                "Waiting for PIDs from the engine…",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            state.liveData.forEach { (k, v) ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    Text(k, modifier = Modifier.weight(1f))
                    Text("%.2f".format(v), fontWeight = FontWeight.SemiBold)
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

@Preview(showBackground = true)
@Composable
private fun LiveDataScreenWithDataPreview() {
    CaseForgeTheme(mode = "dark") {
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
