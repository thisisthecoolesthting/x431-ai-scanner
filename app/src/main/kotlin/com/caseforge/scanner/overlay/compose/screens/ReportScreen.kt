package com.caseforge.scanner.overlay.compose.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.caseforge.scanner.engine.ScrapedDtc
import com.caseforge.scanner.overlay.compose.RecallBanner
import com.caseforge.scanner.overlay.compose.screens.SuggestedTestCard
import com.caseforge.scanner.engine.EngineState
import com.caseforge.scanner.engine.ScreenKind
import com.caseforge.scanner.overlay.compose.Spacing
import com.caseforge.scanner.overlay.compose.TogetherCardShape
import com.caseforge.scanner.overlay.compose.togetherCardColors
import com.caseforge.scanner.overlay.compose.togetherCardElevation
import com.caseforge.scanner.ui.theme.CaseForgeTheme

/**
 * Renders scan results: a summary header and a list of diagnostic trouble codes (DTCs).
 *
 * Polish improvements:
 * - DTC code in monospace (FontFamily.Monospace), left-aligned
 * - 2-line description with ellipsis (maxLines = 2)
 * - Severity chip on the right using Material3 AssistChip with severity-tinted container color
 *
 * Shows:
 * - "Scan complete" title with DTC count
 * - "Clean scan" message if no DTCs found (via colorScheme.primary)
 * - DTC cards with code, module, and description
 *
 * All text and colors routed through MaterialTheme (C1 requirement).
 */
@Composable
fun ReportScreen(
    state: EngineState,
    onAction: (UiAction) -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(Spacing.Space14)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Spacing.Space8),
    ) {
        Text(
            "Scan complete",
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            "${state.dtcs.size} DTC(s) found",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
        HorizontalDivider()

        if (state.recallMatches.isNotEmpty()) {
            RecallBanner(recalls = state.recallMatches)
        }

        SuggestedTestCard(
            suggestion = state.suggestedNextTest,
            loading = state.nextTestLoading,
            onAccept = { onAction(UiAction.AcceptSuggestedTest) },
            onDecline = { onAction(UiAction.DeclineSuggestedTest) },
        )

        state.rootCauseHypothesis?.let { hypothesis ->
            RootCauseCard(
                hypothesis = hypothesis,
                onRunCapability = { onAction(UiAction.TapCapability(it)) },
            )
        }

        if (state.dtcs.isEmpty()) {
            Text(
                "No diagnostic trouble codes stored. Clean scan.",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodySmall,
            )
        } else {
            state.dtcs.forEach { dtc ->
                DtcCard(dtc)
            }
        }
    }
}

/**
 * Individual DTC card with monospace code, 2-line description, and severity indicator.
 */
@Composable
private fun DtcCard(dtc: ScrapedDtc) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = TogetherCardShape,
        colors = togetherCardColors(),
        elevation = togetherCardElevation(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.Space12),
            verticalArrangement = Arrangement.spacedBy(Spacing.Space6),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // DTC code in monospace, left-aligned
                Text(
                    dtc.code,
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.weight(1f),
                )

                // Severity chip (tinted container color)
                AssistChip(
                    onClick = {},
                    label = {
                        Text(
                            "Error",
                            style = MaterialTheme.typography.labelSmall,
                        )
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        labelColor = MaterialTheme.colorScheme.onErrorContainer,
                    ),
                )
            }

            // Module row
            dtc.module?.let {
                Text(
                    "Module: $it",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Description: 2 lines max with ellipsis
            dtc.description?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
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
                        ScrapedDtc("P0101", description = "Mass or Volume Air Flow Sensor A Range/Performance", module = "Engine"),
                        ScrapedDtc("P0405", description = "EGR Sensor A Circuit Low", module = "Engine"),
                        ScrapedDtc("B1234", description = "Driver Seat Track Position Memory not stored", module = "Seat Memory"),
                    ),
                ),
                onAction = {},
            )
        }
    }
}
