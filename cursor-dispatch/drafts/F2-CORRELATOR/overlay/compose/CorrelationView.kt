package com.caseforge.scanner.overlay.compose

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.caseforge.scanner.ai.ConfidenceTier
import com.caseforge.scanner.ai.CorrelationReport
import com.caseforge.scanner.ai.RootCauseGroup
import com.caseforge.scanner.engine.Dtc
import com.caseforge.scanner.engine.Severity
import com.caseforge.scanner.ui.theme.CaseForgeTheme

// ---------------------------------------------------------------------------
// F2 — Cross-Module Fault Correlation: CorrelationView
//
// Drop this composable inside FullScanResults (ReportScreen) below the raw
// DTC list. It accepts a nullable [CorrelationReport] so the host screen can
// render it in three states: loading, empty, or populated.
// ---------------------------------------------------------------------------

/**
 * Composable section that renders a [CorrelationReport] inside the
 * FullScanResults screen.
 *
 * States:
 * - [report] == null, [loading] == true  → spinner + "Analyzing…" label
 * - [report] == null, [loading] == false → nothing rendered (no-op)
 * - [report].isEmpty                     → "No correlated groups found" note
 * - otherwise                            → ranked [RootCauseGroupCard] list
 *
 * @param report         The correlation result from [DtcCorrelator]. Null while
 *                       the coroutine is still running.
 * @param loading        True while Claude is being called. Shows a spinner.
 * @param onRunCapability Callback invoked when the technician taps "Run [hint]"
 *                        on a group card. Passes the capability ID string.
 * @param modifier       Standard Compose modifier.
 */
@Composable
fun CorrelationView(
    report: CorrelationReport?,
    loading: Boolean,
    onRunCapability: (capabilityId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        // Section header
        CorrelationSectionHeader()

        HorizontalDivider(modifier = Modifier.padding(bottom = 8.dp))

        when {
            loading -> CorrelationLoadingState()

            report == null -> {
                /* No-op: correlation not yet requested */
            }

            report.isEmpty -> CorrelationEmptyState()

            else -> {
                report.groups.forEachIndexed { index, group ->
                    RootCauseGroupCard(
                        group = group,
                        rank = index + 1,
                        onRunCapability = onRunCapability,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "Generated ${formatRelativeTime(report.generatedAtMs)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 2.dp),
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Section header
// ---------------------------------------------------------------------------

@Composable
private fun CorrelationSectionHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = Icons.Default.AutoAwesome,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
        Text(
            "AI Root-Cause Correlation",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
    }
}

// ---------------------------------------------------------------------------
// Loading state
// ---------------------------------------------------------------------------

@Composable
private fun CorrelationLoadingState() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(20.dp),
            strokeWidth = 2.dp,
        )
        Text(
            "Analyzing faults across all modules…",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ---------------------------------------------------------------------------
// Empty state
// ---------------------------------------------------------------------------

@Composable
private fun CorrelationEmptyState() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.Info,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Text(
            "No cross-module correlations found. DTCs appear independent.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ---------------------------------------------------------------------------
// Root-cause group card
// ---------------------------------------------------------------------------

/**
 * Expandable card for a single [RootCauseGroup].
 *
 * Collapsed:  rank badge + root cause headline + confidence chip
 * Expanded:   + supporting DTC chips + recommended action + capability button
 */
@Composable
private fun RootCauseGroupCard(
    group: RootCauseGroup,
    rank: Int,
    onRunCapability: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(rank == 1) } // top group auto-expands

    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(Modifier.padding(12.dp)) {

            // --- Header row (always visible) ---
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // Rank badge
                Badge(
                    containerColor = confidenceTierColor(group.confidenceTier),
                ) {
                    Text(
                        "#$rank",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                    )
                }

                // Root cause text
                Text(
                    group.rootCause,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )

                // Confidence chip + expand toggle
                Column(horizontalAlignment = Alignment.End) {
                    ConfidenceChip(group.confidenceTier, group.confidence)
                    IconButton(
                        onClick = { expanded = !expanded },
                        modifier = Modifier.size(24.dp),
                    ) {
                        Icon(
                            imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (expanded) "Collapse" else "Expand",
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }

            // --- Expandable detail ---
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically(),
            ) {
                Column(
                    Modifier.padding(top = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // Supporting DTCs
                    if (group.supportingDtcs.isNotEmpty()) {
                        Text(
                            "Supporting DTCs",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium,
                        )
                        DtcChipRow(group.supportingDtcs)
                    }

                    HorizontalDivider()

                    // Recommended action
                    Row(
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp).padding(top = 2.dp),
                        )
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                "Next Action",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Medium,
                            )
                            Text(
                                group.recommendedAction,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }

                    // Capability shortcut button
                    group.capabilityHint?.let { hint ->
                        OutlinedButton(
                            onClick = { onRunCapability(hint) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                "Run: ${hint.replace('_', ' ').replaceFirstChar { it.uppercase() }}",
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Chips
// ---------------------------------------------------------------------------

@Composable
private fun ConfidenceChip(tier: ConfidenceTier, value: Float) {
    val label = "${(value * 100).toInt()}%"
    val bg = confidenceTierColor(tier)
    Surface(
        shape = MaterialTheme.shapes.small,
        color = bg.copy(alpha = 0.18f),
        modifier = Modifier.padding(top = 2.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = bg,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun DtcChipRow(dtcs: List<Dtc>) {
    // Wrap chips in a simple flow-like row using chunked rows of 4
    dtcs.chunked(4).forEach { row ->
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            row.forEach { dtc ->
                DtcChip(dtc)
            }
        }
    }
}

@Composable
private fun DtcChip(dtc: Dtc) {
    val chipColor = when (dtc.severity) {
        Severity.Red   -> MaterialTheme.colorScheme.error
        Severity.Amber -> Color(0xFFE65100) // deep-orange
        Severity.Gray  -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        shape = MaterialTheme.shapes.small,
        color = chipColor.copy(alpha = 0.12f),
    ) {
        Text(
            dtc.code,
            style = MaterialTheme.typography.labelSmall,
            color = chipColor,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
        )
    }
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

@Composable
private fun confidenceTierColor(tier: ConfidenceTier): Color = when (tier) {
    ConfidenceTier.High   -> MaterialTheme.colorScheme.primary
    ConfidenceTier.Medium -> Color(0xFFE65100) // deep-orange; distinct from M3 error red
    ConfidenceTier.Low    -> MaterialTheme.colorScheme.onSurfaceVariant
}

private fun formatRelativeTime(tsMs: Long): String {
    val delta = System.currentTimeMillis() - tsMs
    return when {
        delta < 60_000L  -> "just now"
        delta < 3_600_000L -> "${delta / 60_000}m ago"
        else             -> "${delta / 3_600_000}h ago"
    }
}

// ---------------------------------------------------------------------------
// Previews
// ---------------------------------------------------------------------------

@Preview(showBackground = true, name = "CorrelationView – loading")
@Composable
private fun CorrelationViewLoadingPreview() {
    CaseForgeTheme(mode = "dark") {
        Surface(color = MaterialTheme.colorScheme.background) {
            CorrelationView(
                report = null,
                loading = true,
                onRunCapability = {},
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}

@Preview(showBackground = true, name = "CorrelationView – populated")
@Composable
private fun CorrelationViewPopulatedPreview() {
    CaseForgeTheme(mode = "dark") {
        Surface(color = MaterialTheme.colorScheme.background) {
            val sampleGroups = listOf(
                RootCauseGroup(
                    rootCause = "Faulty engine coolant thermostat causing CAN-bus ghost codes",
                    supportingDtcs = listOf(
                        Dtc("Engine", "U0100", "ECM/PCM lost communication", Severity.Red, null),
                        Dtc("Engine", "P0128", "Coolant below thermostat regulating temp", Severity.Amber, null),
                    ),
                    confidence = 0.91f,
                    recommendedAction = "Check coolant temp with live data at operating temp; replace thermostat if temp stays below 82°C.",
                    capabilityHint = "live_data",
                ),
                RootCauseGroup(
                    rootCause = "Weak battery spawning multiple BCM fault codes",
                    supportingDtcs = listOf(
                        Dtc("Engine", "P0562", "System voltage low", Severity.Amber, null),
                        Dtc("BCM",    "B1000", "BCM internal fault", Severity.Gray, null),
                        Dtc("BCM",    "B1005", "BCM power supply low", Severity.Gray, null),
                    ),
                    confidence = 0.78f,
                    recommendedAction = "Load-test battery; measure alternator output at 2000 rpm.",
                    capabilityHint = "read_dtcs",
                ),
                RootCauseGroup(
                    rootCause = "Failed MAF sensor propagating faults into TCM",
                    supportingDtcs = listOf(
                        Dtc("Engine", "P0102", "MAF sensor circuit low", Severity.Amber, null),
                        Dtc("Engine", "P0299", "Turbo underboost condition", Severity.Amber, null),
                        Dtc("TCM",    "U0401", "Invalid data received from ECM", Severity.Red, null),
                    ),
                    confidence = 0.55f,
                    recommendedAction = "Inspect MAF wiring; measure MAF voltage at idle (~0.9V); replace MAF sensor.",
                    capabilityHint = "actuation",
                ),
            )
            Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
                CorrelationView(
                    report = CorrelationReport(groups = sampleGroups, generatedAtMs = System.currentTimeMillis()),
                    loading = false,
                    onRunCapability = {},
                )
            }
        }
    }
}

@Preview(showBackground = true, name = "CorrelationView – empty")
@Composable
private fun CorrelationViewEmptyPreview() {
    CaseForgeTheme(mode = "dark") {
        Surface(color = MaterialTheme.colorScheme.background) {
            CorrelationView(
                report = CorrelationReport(groups = emptyList(), generatedAtMs = System.currentTimeMillis()),
                loading = false,
                onRunCapability = {},
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}
