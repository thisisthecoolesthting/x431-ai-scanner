package com.caseforge.scanner.overlay.compose

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.caseforge.scanner.agent.AgentStatus
import com.caseforge.scanner.engine.EngineDriver
import com.caseforge.scanner.engine.EngineState

@Composable
fun LiveActivityTicker(
    engineState: EngineState,
    modifier: Modifier = Modifier,
) {
    val agentRunning by AgentStatus.running.collectAsState()
    val engineWork by EngineDriver.workActive.collectAsState()
    val lastAction by AgentStatus.lastAction.collectAsState()
    val active = agentRunning || engineWork || engineState.busy

    val pulseAlpha = if (active) {
        val transition = rememberInfiniteTransition(label = "ticker_pulse")
        transition.animateFloat(
            initialValue = 0.3f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1000), RepeatMode.Reverse),
            label = "pulse_alpha",
        ).value
    } else {
        0.35f
    }

    val label = when {
        active -> lastAction?.take(60) ?: engineState.errorBanner?.take(60) ?: "working…"
        !lastAction.isNullOrBlank() -> lastAction!!.take(60)
        else -> "idle"
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(24.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(8.dp)
                .alpha(pulseAlpha)
                .background(
                    if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    CircleShape,
                ),
        )
        Text(
            text = label,
            modifier = Modifier.padding(start = 8.dp),
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            color = if (active) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            },
            maxLines = 1,
        )
    }
}
