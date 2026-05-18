package com.caseforge.scanner.overlay.compose

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.caseforge.scanner.voice.VoiceMode

/**
 * VoiceIndicator — compact Composable for the overlay bubble.
 *
 * States:
 *   IDLE      → hidden (zero size)
 *   WAITING   → small static mic icon, grey tint
 *   CAPTURING → pulsing mic icon, accent color, "Listening…" label
 *   ERROR     → mic-off icon, red tint
 *
 * [lastPhrase] is displayed as ghost text below the icon when non-empty
 * and state is WAITING (confirmation of what was just heard).
 *
 * Place inside the floating overlay composable hierarchy, e.g.:
 *
 *   Box(Modifier.fillMaxSize()) {
 *       // ... other overlay content
 *       VoiceIndicator(
 *           state = voiceState,
 *           lastPhrase = lastPhrase,
 *           modifier = Modifier.align(Alignment.BottomStart).padding(8.dp)
 *       )
 *   }
 */
@Composable
fun VoiceIndicator(
    state: VoiceMode.State,
    lastPhrase: String = "",
    modifier: Modifier = Modifier,
    iconSize: Dp = 28.dp,
) {
    if (state == VoiceMode.State.IDLE) return   // completely hidden

    val isCapturing = state == VoiceMode.State.CAPTURING
    val isError     = state == VoiceMode.State.ERROR

    // Pulse animation — only active during CAPTURING
    val infiniteTransition = rememberInfiniteTransition(label = "voicePulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue  = 1.22f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulseScale",
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.75f,
        targetValue  = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulseAlpha",
    )

    // Halo ring behind the mic (visible only while capturing)
    val haloAlpha by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue  = 0.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "haloAlpha",
    )

    val iconColor = when {
        isError     -> Color(0xFFE53935)                       // red
        isCapturing -> MaterialTheme.colorScheme.primary       // accent
        else        -> Color(0xFFB0B0B0)                       // grey when waiting
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // Mic icon with optional halo ring
        Box(contentAlignment = Alignment.Center) {
            // Halo
            if (isCapturing) {
                Box(
                    modifier = Modifier
                        .size(iconSize * 2f)
                        .scale(pulseScale)
                        .alpha(haloAlpha)
                        .background(
                            color = MaterialTheme.colorScheme.primary,
                            shape = CircleShape,
                        )
                )
            }

            // Background pill
            Box(
                modifier = Modifier
                    .size(iconSize + 12.dp)
                    .background(
                        color = Color(0xCC1A1A2E),
                        shape = CircleShape,
                    )
            )

            // Icon
            Icon(
                imageVector = if (isError) Icons.Filled.MicOff else Icons.Filled.Mic,
                contentDescription = when (state) {
                    VoiceMode.State.WAITING   -> "Voice: waiting for Hey Together"
                    VoiceMode.State.CAPTURING -> "Voice: listening for command"
                    VoiceMode.State.ERROR     -> "Voice: error"
                    else                      -> null
                },
                tint = iconColor,
                modifier = Modifier
                    .size(iconSize)
                    .then(
                        if (isCapturing) Modifier.scale(pulseScale).alpha(pulseAlpha)
                        else Modifier
                    ),
            )
        }

        // Status label
        val statusText = when (state) {
            VoiceMode.State.CAPTURING -> "Listening…"
            VoiceMode.State.ERROR     -> "Mic error"
            else                      -> ""
        }
        if (statusText.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = statusText,
                color = iconColor,
                fontSize = 10.sp,
                maxLines = 1,
            )
        }

        // Ghost text: last recognized phrase (shown while WAITING, fades naturally)
        if (state == VoiceMode.State.WAITING && lastPhrase.isNotEmpty()) {
            Spacer(Modifier.height(2.dp))
            Row(
                modifier = Modifier
                    .background(
                        color = Color(0x881A1A2E),
                        shape = RoundedCornerShape(8.dp),
                    )
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "“$lastPhrase”",
                    color = Color(0x99FFFFFF),
                    fontSize = 9.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.width(140.dp),
                )
            }
        }
    }
}
