package com.caseforge.scanner.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
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
import com.caseforge.scanner.ui.theme.TcwTokens

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

@Composable
fun TcwResumeBar(
    text: String,
    onClick: () -> Unit = {},
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
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
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = TcwTokens.Blue,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Icon(
                imageVector = Icons.Filled.ArrowForward,
                contentDescription = null,
                tint = TcwTokens.Blue,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
fun TcwSectionLabel(
    text: String,
) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = TcwTokens.Muted,
        modifier = Modifier.padding(start = 2.dp, bottom = 4.dp),
    )
}
