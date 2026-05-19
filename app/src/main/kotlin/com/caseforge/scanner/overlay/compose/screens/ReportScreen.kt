package com.caseforge.scanner.overlay.compose.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import com.caseforge.scanner.diagnostics.GuidedTestMatch
import com.caseforge.scanner.diagnostics.GuidedTestPlanner
import com.caseforge.scanner.engine.EngineState
import com.caseforge.scanner.engine.ScrapedDtc
import com.caseforge.scanner.engine.ScreenKind
import com.caseforge.scanner.offline.OfflineDtc
import com.caseforge.scanner.overlay.compose.RecallBanner
import com.caseforge.scanner.overlay.compose.Spacing
import com.caseforge.scanner.overlay.compose.TogetherCardShape
import com.caseforge.scanner.overlay.compose.togetherCardColors
import com.caseforge.scanner.overlay.compose.togetherCardElevation
import com.caseforge.scanner.report.ReportDtcTableFormatter
import com.caseforge.scanner.report.ShopExport
import com.caseforge.scanner.report.ShopExportFormatter
import com.caseforge.scanner.ui.theme.TogetherCarWorksTheme

/**
 * Optional host wiring for offline lookup, shop export share lane, and guided-test navigation.
 * Defaults are no-ops so [ReportScreen] compiles in MainActivity / overlay without Context.
 */
data class ReportScreenCallbacks(
    val symptomQuery: String? = null,
    /** When null, offline snippet rows show a wiring placeholder instead of [OfflineDtc] text. */
    val offlineLookup: ((String) -> OfflineDtc?)? = null,
    val transportLabel: String? = null,
    val onCopyDtcTable: (body: String) -> Unit = {},
    val onExportPlainText: (body: String) -> Unit = {},
    val onExportCsv: (body: String) -> Unit = {},
    val onExportPdf: () -> Unit = {},
    val onRunGuidedTest: (testId: String) -> Unit = {},
)

/**
 * Renders scan results: summary, shop export actions, guided tests, offline DTC snippets, and DTC cards.
 *
 * All text and colors routed through MaterialTheme (C1 requirement).
 */
@Composable
fun ReportScreen(
    state: EngineState,
    onAction: (UiAction) -> Unit,
    callbacks: ReportScreenCallbacks = ReportScreenCallbacks(),
) {
    val shopExport = remember(state, callbacks.transportLabel) {
        ShopExport.fromEngineState(state, transport = callbacks.transportLabel)
    }
    val guidedMatches = remember(state.dtcs, callbacks.symptomQuery) {
        GuidedTestPlanner.suggest(
            symptomQuery = callbacks.symptomQuery,
            dtcCodes = state.dtcs.map { it.code },
        )
    }

    Column(
        Modifier
            .fillMaxWidth()
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

        ShopExportSection(
            enabled = state.dtcs.isNotEmpty() || state.vehicleVin != null,
            onCopyDtcTable = {
                callbacks.onCopyDtcTable(ReportDtcTableFormatter.formatForClipboard(state.dtcs))
            },
            onPlainText = {
                callbacks.onExportPlainText(
                    ShopExportFormatter.toPlainText(shopExport),
                )
            },
            onCsv = {
                callbacks.onExportCsv(ShopExportFormatter.toCsv(shopExport))
            },
            onPdf = callbacks.onExportPdf,
        )

        if (state.recallMatches.isNotEmpty()) {
            RecallBanner(recalls = state.recallMatches)
        }

        SuggestedTestCard(
            suggestion = state.suggestedNextTest,
            loading = state.nextTestLoading,
            onAccept = { onAction(UiAction.AcceptSuggestedTest) },
            onDecline = { onAction(UiAction.DeclineSuggestedTest) },
        )

        GuidedTestsSection(
            matches = guidedMatches,
            symptomQuery = callbacks.symptomQuery,
            onRunGuidedTest = callbacks.onRunGuidedTest,
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
                DtcCard(
                    dtc = dtc,
                    offlineEntry = callbacks.offlineLookup?.invoke(dtc.code),
                    offlineWired = callbacks.offlineLookup != null,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ShopExportSection(
    enabled: Boolean,
    onCopyDtcTable: () -> Unit,
    onPlainText: () -> Unit,
    onCsv: () -> Unit,
    onPdf: () -> Unit,
) {
    SectionCard(title = "Shop export") {
        Text(
            "Share formatted results with your shop system or clipboard.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Spacing.Space8),
            verticalArrangement = Arrangement.spacedBy(Spacing.Space8),
        ) {
            OutlinedButton(onClick = onCopyDtcTable, enabled = enabled) {
                Text("Copy DTC table")
            }
            OutlinedButton(onClick = onPlainText, enabled = enabled) {
                Text("Plain text")
            }
            OutlinedButton(onClick = onCsv, enabled = enabled) {
                Text("CSV")
            }
            OutlinedButton(onClick = onPdf, enabled = enabled) {
                Text("PDF (soon)")
            }
        }
    }
}

@Composable
private fun GuidedTestsSection(
    matches: List<GuidedTestMatch>,
    symptomQuery: String?,
    onRunGuidedTest: (String) -> Unit,
) {
    SectionCard(title = "Guided tests") {
        if (symptomQuery.isNullOrBlank()) {
            Text(
                "Symptom text improves ranking when the host passes symptomQuery.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (matches.isEmpty()) {
            Text(
                "No bundled guided plans match these codes yet. Try adding symptom text or run a full scan.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            matches.forEach { match ->
                GuidedTestMatchRow(
                    match = match,
                    onRun = { onRunGuidedTest(match.test.id) },
                )
            }
        }
    }
}

@Composable
private fun GuidedTestMatchRow(
    match: GuidedTestMatch,
    onRun: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.Space4),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    match.test.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                val matchHint = buildList {
                    if (match.matchedDtcPrefixes.isNotEmpty()) {
                        add("DTC ${match.matchedDtcPrefixes.joinToString()}")
                    }
                    if (match.matchedAliases.isNotEmpty()) {
                        add(match.matchedAliases.take(2).joinToString())
                    }
                }.joinToString(" · ")
                if (matchHint.isNotBlank()) {
                    Text(
                        matchHint,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            TextButton(onClick = onRun) {
                Text("Open")
            }
        }
        Text(
            match.test.reportWording,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        HorizontalDivider()
    }
}

@Composable
private fun DtcCard(
    dtc: ScrapedDtc,
    offlineEntry: OfflineDtc?,
    offlineWired: Boolean,
) {
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
                Text(
                    dtc.code,
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.weight(1f),
                )
                AssistChip(
                    onClick = {},
                    label = {
                        Text(
                            dtc.status?.replaceFirstChar { it.uppercase() } ?: "Stored",
                            style = MaterialTheme.typography.labelSmall,
                        )
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        labelColor = MaterialTheme.colorScheme.onErrorContainer,
                    ),
                )
            }

            dtc.module?.let {
                Text(
                    "Module: $it",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            dtc.description?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            OfflineSnippetBlock(
                code = dtc.code,
                entry = offlineEntry,
                lookupWired = offlineWired,
            )
        }
    }
}

@Composable
private fun OfflineSnippetBlock(
    code: String,
    entry: OfflineDtc?,
    lookupWired: Boolean,
) {
    HorizontalDivider()
    Text(
        "Offline reference",
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
    )
    when {
        entry != null -> {
            Text(
                entry.title,
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                entry.summary,
                style = MaterialTheme.typography.bodySmall,
            )
            if (entry.likelyCauses.isNotEmpty()) {
                Text(
                    "Likely causes",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                entry.likelyCauses.take(3).forEach { cause ->
                    Text("• $cause", style = MaterialTheme.typography.bodySmall)
                }
            }
            if (entry.firstChecks.isNotEmpty()) {
                Text(
                    "First checks",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                entry.firstChecks.take(3).forEach { check ->
                    Text("• $check", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        lookupWired -> {
            Text(
                "No offline entry for $code in the bundled dictionary.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        else -> {
            Text(
                "Wire OfflineDtcLookup via ReportScreenCallbacks.offlineLookup to show bundled explanations.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = TogetherCardShape,
        colors = togetherCardColors(),
        elevation = togetherCardElevation(),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(Spacing.Space12),
            verticalArrangement = Arrangement.spacedBy(Spacing.Space8),
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            content()
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ReportScreenCleanPreview() {
    TogetherCarWorksTheme(mode = "dark") {
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
    val sampleOffline = OfflineDtc(
        code = "P0101",
        title = "MAF range / performance",
        summary = "Air meter reading is outside expected range for load and RPM.",
        likelyCauses = listOf("Dirty MAF", "Vacuum leak upstream"),
        firstChecks = listOf("Inspect ducting", "Compare MAF g/s to spec"),
    )
    TogetherCarWorksTheme(isDarkMode = true) {
        Surface(color = MaterialTheme.colorScheme.background) {
            ReportScreen(
                state = EngineState(
                    screen = ScreenKind.FullScanResults,
                    vehicleVin = "1HGCM82633A004352",
                    dtcs = listOf(
                        ScrapedDtc(
                            "P0101",
                            description = "Mass or Volume Air Flow Sensor A Range/Performance",
                            module = "Engine",
                            status = "current",
                        ),
                        ScrapedDtc(
                            "P0405",
                            description = "EGR Sensor A Circuit Low",
                            module = "Engine",
                            status = "pending",
                        ),
                    ),
                ),
                onAction = {},
                callbacks = ReportScreenCallbacks(
                    symptomQuery = "rough idle",
                    offlineLookup = { code ->
                        if (code.equals("P0101", ignoreCase = true)) sampleOffline else null
                    },
                    transportLabel = "ELM327 USB",
                ),
            )
        }
    }
}
