package com.caseforge.scanner.overlay.compose.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.caseforge.scanner.engine.Dtc
import com.caseforge.scanner.engine.EngineState
import com.caseforge.scanner.engine.ScreenKind
import com.caseforge.scanner.ui.theme.CaseForgeTheme

/**
 * Renders scan results: a summary header and a list of diagnostic trouble codes (DTCs).
 *
 * Shows:
 * - "Scan complete" title with DTC count
 * - "Clean scan" message if no DTCs found (via colorScheme.primary)
 * - DTC cards with code, module, and description
 *
 * All text and colors routed through MaterialTheme (C1 requirement).
 * Note: Green color replaced with MaterialTheme.colorScheme.primary for proper theme integration.
 */
@Composable
fun ReportScreen(
    state: EngineState,
    onAction: (UiAction) -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(14.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Scan complete", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text("${state.dtcs.size} DTC(s) found", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        HorizontalDivider()

        if (state.dtcs.isEmpty()) {
            Text(
                "No diagnostic trouble codes stored. Clean scan.",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodySmall,
            )
        } else {
            state.dtcs.forEach { dtc ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(dtc.code, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleSmall)
                        dtc.module?.let { Text("Module: $it", style = MaterialTheme.typography.labelSmall) }
                        dtc.description?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ReportScreenCleanPreview() {
    CaseForgeTheme(mode = "dark") {
        Surface(color = MaterialTheme.colorScheme.background) {
            ReportScreen(
                state = EngineState(
                    screen = ScreenKind.FullScanResults,
                    dtcs = emptyList(),
                ),
                onAction = {},
            )
        }
    }
}

@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ReportScreenWithDtcsPreviewDark() {
    CaseForgeTheme(isDarkMode = true) {
        Surface(color = MaterialTheme.colorScheme.background) {
            ReportScreen(
                state = EngineState(
                    screen = ScreenKind.FullScanResults,
                    dtcs = listOf(
                        Dtc("P0101", description = "Mass or Volume Air Flow Sensor A Range/Performance", module = "Engine"),
                        Dtc("P0405", description = "EGR Sensor A Circuit Low", module = "Engine"),
                        Dtc("B1234", description = "Driver Seat Track Position Memory not stored", module = "Seat Memory"),
                    ),
                ),
                onAction = {},
            )
        }
    }
}
