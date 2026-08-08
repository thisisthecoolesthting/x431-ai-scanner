package com.caseforge.scanner.ui.session

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Renders [SessionChatMessage] as text bubbles plus rich visual blocks (cards, charts, DTC tables).
 */
@Composable
fun SessionMessageContent(
    message: SessionChatMessage,
    modifier: Modifier = Modifier,
) {
    val isTech = message.role == "tech"
    Column(
        modifier = modifier.fillMaxWidth(if (isTech) 0.92f else 1f),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        message.visualAttachments.forEach { att ->
            VisualAttachmentBlock(att)
        }
        if (message.text.isNotBlank()) {
            Surface(
                tonalElevation = if (isTech) 2.dp else 0.dp,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    message.text,
                    modifier = Modifier.padding(12.dp),
                    color = when (message.role) {
                        "assistant" -> MaterialTheme.colorScheme.primary
                        "system" -> MaterialTheme.colorScheme.onSurfaceVariant
                        else -> MaterialTheme.colorScheme.onSurface
                    },
                )
            }
        }
    }
}

@Composable
fun VisualAttachmentBlock(att: VisualAttachment) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (att.title.isNotBlank()) {
                Text(att.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            }
            if (att.subtitle.isNotBlank()) {
                Text(att.subtitle, style = MaterialTheme.typography.bodySmall)
            }
            att.bullets.forEach { bullet ->
                Text("• $bullet", style = MaterialTheme.typography.bodySmall)
            }
            when (att.kind) {
                "chart" -> if (att.chartSeries.size >= 2) {
                    Sparkline(
                        series = att.chartSeries,
                        label = att.chartLabel.ifBlank { "Trend" },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                    )
                }
                "dtc_table" -> att.dtcRows.forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(row.code, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Text(row.status, style = MaterialTheme.typography.bodySmall)
                    }
                }
                "photo" -> att.imagePath?.let {
                    Text("Photo: ${it.substringAfterLast('/')}", style = MaterialTheme.typography.bodySmall)
                }
                else -> { /* visual_card — title/subtitle only */ }
            }
        }
    }
}
