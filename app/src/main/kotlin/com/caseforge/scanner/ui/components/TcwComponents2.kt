package com.caseforge.scanner.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.caseforge.scanner.ui.theme.TcwTokens

// ─────────────────────────────────────────────────────────────────────────────
// TcwToolButton
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Small square tool button: icon above label.
 * Used in a horizontal tools row (DTC lookup, freeze frame, CSV export, …).
 *
 * @param label   Short label shown below the icon.
 * @param icon    Icon to display.
 * @param onClick Tap handler.
 */
@Composable
fun TcwToolButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit = {},
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(TcwTokens.RadiusMedium))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = TcwTokens.Amber,
            modifier = Modifier.size(24.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// TcwResumeBar
// ─────────────────────────────────────────────────────────────────────────────

/**
 * "Resume where you left off" contextual strip.
 * Shown at the top of the home screen when a previous session exists.
 *
 * @param text    Description of the session to resume ("F-150 · Full Scan — 12 min ago").
 * @param onClick Tap handler (opens session history / resumes session).
 */
@Composable
fun TcwResumeBar(
    text: String,
    onClick: () -> Unit = {},
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(TcwTokens.RadiusMedium))
            .clickable(onClick = onClick),
        color = TcwTokens.BlueSubtle,
        shape = RoundedCornerShape(TcwTokens.RadiusMedium),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = TcwTokens.PadCard, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(TcwTokens.Gap),
        ) {
            Icon(
                imageVector = Icons.Filled.History,
                contentDescription = "Resume",
                tint = TcwTokens.Blue,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = TcwTokens.Blue,
                ),
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Icon(
                imageVector = Icons.Filled.ArrowForwardIos,
                contentDescription = null,
                tint = TcwTokens.Blue,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// TcwSectionLabel
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Small uppercase section header — sits above a group of cards or a row of tools.
 * No decoration; relies on typography + colour contrast.
 *
 * @param text The section label ("QUICK ACTIONS", "LIVE DATA", …).
 */
@Composable
fun TcwSectionLabel(
    text: String,
) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelLarge.copy(
            letterSpacing = 1.2.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
        modifier = Modifier.padding(
            start = 2.dp,
            bottom = 4.dp,
        ),
    )
}
