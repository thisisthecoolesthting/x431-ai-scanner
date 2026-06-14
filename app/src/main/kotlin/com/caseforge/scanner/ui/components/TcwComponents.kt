package com.caseforge.scanner.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.caseforge.scanner.ui.theme.TcwTokens

// ─────────────────────────────────────────────────────────────────────────────
// TcwHeaderBar
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Premium top bar with brand area, optional vehicle/VIN context line,
 * and a connection status pill.
 *
 * @param title        Screen title (e.g. "Together Car Works").
 * @param vehicle      Vehicle descriptor shown beneath the title (e.g. "2019 F-150 · 5.0L").
 *                     Null hides the line.
 * @param connected    True → green "Connected" pill; false → grey "Not connected".
 * @param onMenu       Called when the user taps the menu area (left side of bar).
 */
@Composable
fun TcwHeaderBar(
    title: String,
    vehicle: String? = null,
    connected: Boolean = false,
    onMenu: () -> Unit = {},
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp,
        shape = RoundedCornerShape(
            bottomStart = TcwTokens.RadiusLarge,
            bottomEnd = TcwTokens.RadiusLarge,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onMenu)
                .padding(horizontal = TcwTokens.PadScreen, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Brand / title column
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = TcwTokens.Amber,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (vehicle != null) {
                    Text(
                        text = vehicle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Spacer(modifier = Modifier.width(TcwTokens.Gap))

            // Connection status pill
            val pillBg = if (connected) TcwTokens.GreenSubtle else MaterialTheme.colorScheme.surfaceVariant
            val dotColor = if (connected) TcwTokens.Green else TcwTokens.Muted
            val pillText = if (connected) "Connected" else "Not connected"

            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(TcwTokens.RadiusSmall))
                    .background(pillBg)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(dotColor),
                )
                Text(
                    text = pillText,
                    style = MaterialTheme.typography.labelLarge,
                    color = dotColor,
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// TcwMetricCard
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Gauge-style metric card for live data values (RPM, coolant temp, battery, etc.).
 *
 * @param label        Metric label ("Engine RPM", "Battery", …).
 * @param value        Current reading as string ("1 250", "12.4").
 * @param unit         Unit label shown beside value ("rpm", "V", "°C").
 * @param fillFraction 0f–1f — how full the progress bar is (e.g. rpm / maxRpm).
 * @param color        Accent colour for the bar and value text; defaults to Amber.
 */
@Composable
fun TcwMetricCard(
    label: String,
    value: String,
    unit: String = "",
    fillFraction: Float = 0f,
    color: Color = TcwTokens.Amber,
) {
    Card(
        shape = RoundedCornerShape(TcwTokens.RadiusMedium),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(TcwTokens.PadCard)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.Baseline) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = color,
                    ),
                )
                if (unit.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = unit,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            // Progress bar track
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction = fillFraction.coerceIn(0f, 1f))
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(color),
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// TcwPresetCard
// ─────────────────────────────────────────────────────────────────────────────

/**
 * One-tap workflow preset card.
 *
 * @param title    Primary label ("Quick Check", "Full Diagnostic", …).
 * @param subtitle Short description or estimated time.
 * @param icon     Icon representing the preset.
 * @param accent   If true, amber-fill style (CTA / primary action).
 * @param dark     If true, charcoal-fill style (secondary featured action).
 *                 accent takes precedence over dark.
 * @param onClick  Tap handler.
 */
@Composable
fun TcwPresetCard(
    title: String,
    subtitle: String = "",
    icon: ImageVector,
    accent: Boolean = false,
    dark: Boolean = false,
    onClick: () -> Unit = {},
) {
    val (bgColor, textColor, iconColor, borderColor) = when {
        accent -> Quad(TcwTokens.Amber,   TcwTokens.OnAmber, TcwTokens.OnAmber, Color.Transparent)
        dark   -> Quad(TcwTokens.Ink,     TcwTokens.OnInk,   TcwTokens.Amber,   Color.Transparent)
        else   -> Quad(
            MaterialTheme.colorScheme.surface,
            MaterialTheme.colorScheme.onSurface,
            TcwTokens.Amber,
            MaterialTheme.colorScheme.outlineVariant,
        )
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(TcwTokens.RadiusMedium))
            .border(
                width = if (borderColor == Color.Transparent) 0.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(TcwTokens.RadiusMedium),
            )
            .clickable(onClick = onClick),
        color = bgColor,
        shape = RoundedCornerShape(TcwTokens.RadiusMedium),
    ) {
        Row(
            modifier = Modifier.padding(TcwTokens.PadCard),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(TcwTokens.Gap),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(28.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = textColor,
                    ),
                )
                if (subtitle.isNotEmpty()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = textColor.copy(alpha = 0.72f),
                    )
                }
            }
        }
    }
}

// Tiny data class to avoid destructuring a List<Color>.
private data class Quad(val a: Color, val b: Color, val c: Color, val d: Color)
operator fun Quad.component1() = a
operator fun Quad.component2() = b
operator fun Quad.component3() = c
operator fun Quad.component4() = d
