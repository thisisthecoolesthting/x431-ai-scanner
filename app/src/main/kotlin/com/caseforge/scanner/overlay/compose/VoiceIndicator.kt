package com.caseforge.scanner.overlay.compose

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.caseforge.scanner.voice.VoiceMode

@Composable
fun VoiceIndicator(state: VoiceMode.State, lastPhrase: String = "", modifier: Modifier = Modifier) {
    if (state == VoiceMode.State.IDLE) return
    val capturing = state == VoiceMode.State.CAPTURING
    val error = state == VoiceMode.State.ERROR
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        1f, 1.2f, infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "s",
    )
    val tint = when {
        error -> MaterialTheme.colorScheme.error
        capturing -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(40.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape), contentAlignment = Alignment.Center) {
            Icon(
                if (error) Icons.Filled.MicOff else Icons.Filled.Mic,
                contentDescription = "Voice",
                tint = tint,
                modifier = Modifier.size(24.dp).then(if (capturing) Modifier.scale(pulse) else Modifier),
            )
        }
        if (capturing) Text("Listening?", style = MaterialTheme.typography.labelSmall, color = tint)
        if (state == VoiceMode.State.WAITING && lastPhrase.isNotEmpty()) {
            Text(
                "\"$lastPhrase\"",
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.width(140.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)).padding(Spacing.Space8),
            )
        }
    }
}
