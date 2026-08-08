package com.caseforge.scanner.overlay.compose.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.caseforge.scanner.agent.NextTestSuggestion
import com.caseforge.scanner.overlay.compose.Spacing
import com.caseforge.scanner.overlay.compose.TogetherCardShape
import com.caseforge.scanner.overlay.compose.togetherCardColors
import com.caseforge.scanner.overlay.compose.togetherCardElevation
import com.caseforge.scanner.ui.theme.TogetherCarWorksTheme

@Composable
fun SuggestedTestCard(
    suggestion: NextTestSuggestion?,
    loading: Boolean,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!loading && suggestion == null) return

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = TogetherCardShape,
        colors = togetherCardColors(),
        elevation = togetherCardElevation(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.Space12),
            verticalArrangement = Arrangement.spacedBy(Spacing.Space8),
        ) {
            Text(
                "Suggested next test",
                style = MaterialTheme.typography.titleSmall,
            )

            when {
                loading -> {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.Space12),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(strokeWidth = 2.dp)
                        Text(
                            "Analyzing scan results…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                suggestion != null -> {
                    Text(
                        suggestion.testName,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        "${(suggestion.probability * 100).toInt()}% likely next step",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        suggestion.rationale,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.Space8),
                    ) {
                        Button(
                            onClick = onAccept,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("Run test")
                        }
                        OutlinedButton(onClick = onDecline) {
                            Text("Dismiss")
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SuggestedTestCardPreview() {
    TogetherCarWorksTheme(mode = "dark") {
        SuggestedTestCard(
            suggestion = NextTestSuggestion(
                testName = "Coolant temp live data at operating temp",
                probability = 0.88f,
                rationale = "P0128 with U0100 often clears after confirming thermostat operation.",
                capabilityId = "live_data",
            ),
            loading = false,
            onAccept = {},
            onDecline = {},
        )
    }
}
