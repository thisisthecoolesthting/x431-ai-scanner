package com.caseforge.scanner.overlay.compose.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.caseforge.scanner.engine.EngineState
import com.caseforge.scanner.engine.ScreenKind
import com.caseforge.scanner.engine.SequenceDefinitions
import com.caseforge.scanner.overlay.compose.Spacing
import com.caseforge.scanner.overlay.compose.TogetherCardShape
import com.caseforge.scanner.overlay.compose.togetherCardColors
import com.caseforge.scanner.ui.theme.CaseForgeTheme

@Composable
fun SequenceRunnerScreen(
    state: EngineState,
    onAction: (UiAction) -> Unit,
) {
    val runner = state.screen as? ScreenKind.SequenceRunner
    val sequence = runner?.sequenceId?.let { id ->
        SequenceDefinitions.ALL.firstOrNull { it.id == id }
    }
    val currentStep = sequence?.steps?.getOrNull(runner?.stepIndex ?: 0)
    val stepNumber = (runner?.stepIndex ?: 0) + 1
    val totalSteps = runner?.totalSteps ?: sequence?.steps?.size ?: 0
    val progress = if (totalSteps == 0) 0f else stepNumber.coerceAtMost(totalSteps).toFloat() / totalSteps

    Column(
        Modifier
            .fillMaxSize()
            .padding(Spacing.Space16),
        verticalArrangement = Arrangement.spacedBy(Spacing.Space12),
    ) {
        Text(
            text = sequence?.name ?: runner?.title ?: "Diagnostic sequence",
            style = MaterialTheme.typography.headlineSmall,
        )
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = "Step ${stepNumber.coerceAtMost(totalSteps.coerceAtLeast(1))} of ${totalSteps.coerceAtLeast(1)}",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = TogetherCardShape,
            colors = togetherCardColors(),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(Spacing.Space16),
                verticalArrangement = Arrangement.spacedBy(Spacing.Space8),
            ) {
                Text(
                    text = currentStep?.title ?: runner?.title ?: "Running sequence",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = currentStep?.instruction ?: "Waiting for the next diagnostic step.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if ((currentStep?.durationSeconds ?: 0) > 0) {
                    Text(
                        text = "Target time: ${currentStep?.durationSeconds}s",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Spacer(Modifier.weight(1f))

        Button(
            onClick = { runner?.sequenceId?.let { onAction(UiAction.AdvanceSequence(it)) } },
            modifier = Modifier.align(Alignment.End),
            enabled = runner != null,
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(Spacing.Space8))
            Text("Advance")
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SequenceRunnerScreenPreview() {
    CaseForgeTheme(mode = "dark") {
        Surface(color = MaterialTheme.colorScheme.background) {
            SequenceRunnerScreen(
                state = EngineState(
                    screen = ScreenKind.SequenceRunner(
                        sequenceId = "seq_relative_compression",
                        stepIndex = 1,
                        totalSteps = 4,
                        title = "Open live data",
                    ),
                ),
                onAction = {},
            )
        }
    }
}
