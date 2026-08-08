package com.caseforge.scanner.overlay.compose.screens

import androidx.compose.foundation.layout.*
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
 * Renders error states: dialogs from X431 and unknown screens.
 *
 * For [ScreenKind.Dialog]: shows the dialog text with a hint to peek at X431.
 * For [ScreenKind.Unknown]: shows an unrecognized screen message with optional hint.
 *
 * Shows:
 * - Error title
 * - Error message or dialog text
 * - "Peek to see X431" hint
 */
@Composable
fun ErrorScreen(
    state: EngineState,
    onAction: (UiAction) -> Unit,
    errorText: String = "",
    isUnknown: Boolean = false,
) {
    Column(
        Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            if (isUnknown) "Unrecognized screen" else "X431 dialog",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            if (isUnknown) {
                "Launch AI doesn't know this X431 screen yet. Tap Peek to use X431 directly, " +
                    "or report this so we can add support."
            } else {
                "A dialog appeared in X431. Tap Peek above to see it and respond directly."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (errorText.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(
                errorText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ErrorScreenDialogPreview() {
    CaseForgeTheme(mode = "dark") {
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

@Preview(showBackground = true)
@Composable
private fun ErrorScreenUnknownPreview() {
    CaseForgeTheme(mode = "dark") {
        Surface(color = MaterialTheme.colorScheme.background) {
            ErrorScreen(
                state = EngineState(
                    screen = ScreenKind.Unknown("Some undocumented X431 screen"),
                ),
                onAction = {},
                errorText = "Some undocumented X431 screen",
                isUnknown = true,
            )
        }
    }
}
