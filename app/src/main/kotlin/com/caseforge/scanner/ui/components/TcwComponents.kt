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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.caseforge.scanner.ui.theme.TcwTokens

@Composable
fun TcwHeaderBar(
    title: String,
    vehicle: String? = null,
    connected: Boolean = false,
    onMenu: () -> Unit = {},
) {
    val pillBg = if (connected) TcwTokens.GreenSubtle else MaterialTheme.colorScheme.surfaceVariant
    val dotColor = if (connected) TcwTokens.Green else TcwTokens.Muted
    val pillText = if (connected) "Connected" else "Not connected"

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
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = TcwTokens.Amber,
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
                        .background(
                            color = dotColor,
                            shape = RoundedCornerShape(50),
                        ),
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

@Composable
fun TcwMetricCard(
    label: String,
    value: String,
    unit: String = "",
    fillFraction: Float = 0f,
    color: Color = TcwTokens.Amber,
) {
    val safeF = fillFraction.coerceIn(0f, 1f)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(TcwTokens.RadiusMedium),
        shadowElevation = 1.dp,
    ) {
        Column(modifier = Modifier.padding(TcwTokens.PadCard)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = color,
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
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(2.dp),
                    ),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction = safeF)
                        .height(4.dp)
                        .background(
                            color = color,
                            shape = RoundedCornerShape(2.dp),
                        ),
                )
            }
        }
    }
}

@Composable
fun TcwPresetCard(
    title: String,
    subtitle: String = "",
    icon: ImageVector,
    accent: Boolean = false,
    dark: Boolean = false,
    onClick: () -> Unit = {},
) {
    val bgColor: Color
    val textColor: Color
    val iconColor: Color
    val showBorder: Boolean

    when {
        accent -> {
            bgColor = TcwTokens.Amber
            textColor = TcwTokens.OnAmber
            iconColor = TcwTokens.OnAmber
            showBorder = false
        }
        dark -> {
            bgColor = TcwTokens.Ink
            textColor = TcwTokens.OnInk
            iconColor = TcwTokens.Amber
            showBorder = false
        }
        else -> {
            bgColor = MaterialTheme.colorScheme.surface
            textColor = MaterialTheme.colorScheme.onSurface
            iconColor = TcwTokens.Amber
            showBorder = true
        }
    }

    val borderMod = if (showBorder) {
        Modifier.border(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant,
            shape = RoundedCornerShape(TcwTokens.RadiusMedium),
        )
    } else {
        Modifier
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .then(borderMod)
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
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = textColor,
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
