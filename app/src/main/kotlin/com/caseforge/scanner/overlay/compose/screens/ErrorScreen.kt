package com.caseforge.scanner.overlay.compose.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.caseforge.scanner.engine.EngineState
import com.caseforge.scanner.engine.ScreenKind
import com.caseforge.scanner.overlay.compose.Spacing
import com.caseforge.scanner.overlay.compose.TogetherCardShape
import com.caseforge.scanner.overlay.compose.togetherCardColors
import com.caseforge.scanner.overlay.compose.togetherCardElevation
import com.caseforge.scanner.ui.theme.TogetherCarWorksTheme

/**
 * Renders error states: dialogs from OEM diagnostic app and unknown screens.
 *
 * Polish improvements:
 * - Error message shown in a Card with errorContainer color
 * - Small warning icon (Icons.Outlined.Warning)
 * - Primary action button "Peek OEM diagnostic app" that calls onPeek callback
 *
 * For [ScreenKind.Dialog]: shows the dialog text with a hint to peek at OEM diagnostic app.
 * For [ScreenKind.Unknown]: shows an unrecognized screen message with optional hint.
 *
 * All text and colors routed through MaterialTheme (C1 requirement).
 */
@Composable
fun ErrorScreen(
    state: EngineState,
    onAction: (UiAction) -> Unit,
    errorText: String = "",
    isUnknown: Boolean = false,
    onPeek: () -> Unit = {},
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(Spacing.Space20),
        verticalArrangement = Arrangement.spacedBy(Spacing.Space12),
    ) {
        Text(
            if (isUnknown) "Unrecognized screen" else "OEM diagnostic app dialog",
            style = MaterialTheme.typography.headlineSmall,
        )

        // Error message in a Card with errorContainer color
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = TogetherCardShape,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            ),
            elevation = togetherCardElevation(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Spacing.Space16),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(Spacing.Space12),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Warning,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                )
                Column {
                    Text(
                        if (isUnknown) {
                            "Together Car Works doesn't know this OEM diagnostic app screen yet. Tap Peek to use OEM diagnostic app directly, " +
                                "or report this so we can add support."
                        } else {
                            "A dialog appeared in OEM diagnostic app. Tap Peek above to see it and respond directly."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                    if (errorText.isNotBlank()) {
                        Spacer(Modifier.height(Spacing.Space8))
                        Text(
                            errorText,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f),
                        )
                    }
                }
            }
        }

        Spacer(Modifier.weight(1f))

        // "Peek OEM diagnostic app" primary action button
        Button(
            onClick = onPeek,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
        ) {
            Text(
                "Peek OEM diagnostic app",
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ErrorScreenDialogPreview() {
    TogetherCarWorksTheme(mode = "dark") {
        Surface(color = MaterialTheme.colorScheme.background) {
            ErrorScreen(
                state = EngineState(
                    screen = ScreenKind.Dialog("Are you sure you want to clear all codes?"),
                ),
                onAction = {},
                errorText = "Are you sure you want to clear all codes?",
                isUnknown = false,
            )
        }
    }
}

@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ErrorScreenUnknownPreviewDark() {
    TogetherCarWorksTheme(isDarkMode = true) {
        Surface(color = MaterialTheme.colorScheme.background) {
            ErrorScreen(
                state = EngineState(
                    screen = ScreenKind.Unknown("Some undocumented OEM diagnostic app screen"),
                ),
                onAction = {},
                errorText = "Some undocumented OEM diagnostic app screen",
                isUnknown = true,
            )
        }
    }
}
