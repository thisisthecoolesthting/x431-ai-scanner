package com.caseforge.scanner.overlay.compose.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.caseforge.scanner.engine.DtcCorrelator
import com.caseforge.scanner.overlay.compose.Spacing
import com.caseforge.scanner.overlay.compose.TogetherCardShape
import com.caseforge.scanner.overlay.compose.togetherCardColors
import com.caseforge.scanner.overlay.compose.togetherCardElevation

@Composable
fun RootCauseCard(
    hypothesis: DtcCorrelator.RootCauseHypothesis,
    onRunCapability: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = TogetherCardShape,
        colors = togetherCardColors(),
        elevation = togetherCardElevation(),
    ) {
        Column(
            Modifier.padding(Spacing.Space12),
            verticalArrangement = Arrangement.spacedBy(Spacing.Space8),
        ) {
            Text(
                "Likely root cause",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                hypothesis.cause,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "Modules: ${hypothesis.affectedModules.joinToString()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            hypothesis.evidencePoints.forEach { point ->
                Text(
                    "• $point",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            hypothesis.capabilityHint?.let { capId ->
                TextButton(onClick = { onRunCapability(capId) }) {
                    Text("Run suggested test: $capId")
                }
            }
        }
    }
}
