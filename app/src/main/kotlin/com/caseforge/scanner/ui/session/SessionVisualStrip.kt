package com.caseforge.scanner.ui.session

import com.caseforge.scanner.agent.session.BackgroundObdSnapshot
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.max

@Composable
fun SessionVisualStrip(
    sessionId: String,
    pane: SessionVisualComposer.PaneState,
    obdSnapshot: BackgroundObdSnapshot,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (pane.gaugeKinds.isNotEmpty()) {
            SessionGaugePane(
                sessionId = sessionId,
                kinds = pane.gaugeKinds,
                snapshot = obdSnapshot,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        StripCard { StripItemContent(pane.primary, obdSnapshot) }
        pane.secondary?.let { sec ->
            StripCard { StripItemContent(sec, obdSnapshot) }
        }
    }
}

@Composable
private fun StripCard(content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(Modifier.padding(12.dp)) { content() }
    }
}

@Composable
private fun StripItemContent(item: SessionVisualComposer.StripItem, obdSnapshot: BackgroundObdSnapshot) {
    when (item) {
        is SessionVisualComposer.StripItem.PhotoThumbnails -> PhotoThumbnailsRow(item.paths)
        is SessionVisualComposer.StripItem.WedgeCard -> WedgeCardContent(item)
        is SessionVisualComposer.StripItem.DiaStatus -> DiaStatusContent(item)
        is SessionVisualComposer.StripItem.LiveMeasurements -> LiveMeasurementsContent(item, obdSnapshot)
        is SessionVisualComposer.StripItem.ComponentHint -> ComponentHintContent(item)
        is SessionVisualComposer.StripItem.PhotoInsightCard -> PhotoInsightCardContent(item)
    }
}

@Composable
private fun PhotoInsightCardContent(item: SessionVisualComposer.StripItem.PhotoInsightCard) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            "Photo insights (${item.confidence})",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
        )
        item.bullets.take(5).forEach { bullet ->
            Text("• $bullet", style = MaterialTheme.typography.bodySmall)
        }
        item.suggestedNextSteps.forEach { step ->
            Text("→ $step", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        }
        Text(
            "Visual estimate — verify on vehicle.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PhotoThumbnailsRow(paths: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Session photos", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        ) {
            paths.forEachIndexed { i, path ->
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.size(width = 96.dp, height = 64.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            listOf("Engine bay", "Door jamb", "Dashboard").getOrElse(i) { "Photo ${i + 1}" },
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
        }
        Text(
            paths.size.toString() + " capture(s) on disk",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun WedgeCardContent(item: SessionVisualComposer.StripItem.WedgeCard) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            "${item.marque} ${item.model}",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text("Platform ${item.platformCode}", style = MaterialTheme.typography.bodySmall)
        item.gatewayNote?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DiaStatusContent(item: SessionVisualComposer.StripItem.DiaStatus) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("DIA link", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(item.connectionState, style = MaterialTheme.typography.bodyMedium)
            Text("${item.dtcCount} DTC", style = MaterialTheme.typography.bodySmall)
        }
        item.protocol?.let { Text("Protocol: $it", style = MaterialTheme.typography.bodySmall) }
        item.ecuAddr?.let { Text("ECU: $it", style = MaterialTheme.typography.bodySmall) }
        item.monitors?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun LiveMeasurementsContent(
    item: SessionVisualComposer.StripItem.LiveMeasurements,
    obdSnapshot: BackgroundObdSnapshot,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Live data", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SessionRpmTachCompact(snapshot = obdSnapshot)
            Row(
                Modifier.weight(1f),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                MeasurementChip("Coolant", item.coolantC?.let { "%.0f °C".format(it) } ?: "—")
                MeasurementChip("V", item.voltage?.let { "%.1f".format(it) } ?: "—")
            }
        }
        if (item.rpmHistory.size >= 2) {
            Sparkline(
                series = item.rpmHistory,
                label = "RPM",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
            )
        }
    }
}

@Composable
private fun MeasurementChip(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun ComponentHintContent(item: SessionVisualComposer.StripItem.ComponentHint) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            item.component.replaceFirstChar { it.uppercase() },
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(item.hint, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun Sparkline(
    series: List<Float>,
    label: String,
    modifier: Modifier = Modifier,
    lineColor: Color = MaterialTheme.colorScheme.primary,
) {
    val min = series.minOrNull() ?: 0f
    val maxV = max(series.maxOrNull() ?: 1f, min + 1f)
    Canvas(modifier = modifier) {
        if (series.size < 2) return@Canvas
        val path = Path()
        series.forEachIndexed { i, v ->
            val x = i.toFloat() / (series.size - 1).coerceAtLeast(1) * size.width
            val y = size.height - ((v - min) / (maxV - min)) * size.height
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, lineColor, style = Stroke(width = 3f))
    }
    Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
