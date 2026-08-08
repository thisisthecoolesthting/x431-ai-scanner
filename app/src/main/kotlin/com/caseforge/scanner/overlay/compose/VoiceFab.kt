package com.caseforge.scanner.overlay.compose

import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

@Composable
fun VoiceFab(enabled: Boolean, onPressStart: () -> Unit, onPressEnd: () -> Unit, modifier: Modifier = Modifier) {
    if (!enabled) return
    FloatingActionButton(
        onClick = {},
        modifier = modifier.size(56.dp).pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                onPressStart()
                waitForUpOrCancellation()
                onPressEnd()
            }
        },
        shape = CircleShape,
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) { Icon(Icons.Default.Mic, contentDescription = "Hold to speak") }
}
