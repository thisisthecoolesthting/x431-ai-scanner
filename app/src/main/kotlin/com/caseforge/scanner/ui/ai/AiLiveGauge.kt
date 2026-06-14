package com.caseforge.scanner.ui.ai

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.caseforge.scanner.ai.GaugeReading
import com.caseforge.scanner.ui.theme.TcwTokens

/**
 * A single radial gauge drawn with Compose Canvas (no external dependency).
 * 270° sweep, faint background arc + a colored value arc, value in the center.
 */
@Composable
fun AiLiveGauge(
    label: String,
    value: String,
    unit: String,
    fillFraction: Float,
    color: Color = TcwTokens.Amber,
    modifier: Modifier = Modifier,
) {
    val frac = fillFraction.coerceIn(0f, 1f)
    val startAngle = 135f
    val sweepTotal = 270f

    Column(
        modifier = modifier.width(108.dp).height(112.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier = Modifier.size(84.dp),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.fillMaxWidth().size(84.dp).padding(6.dp)) {
                val stroke = 8.dp.toPx()
                val diameter = size.minDimension - stroke
                val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
                val arcSize = Size(diameter, diameter)
                drawArc(
                    color = TcwTokens.Muted.copy(alpha = 0.25f),
                    startAngle = startAngle,
                    sweepAngle = sweepTotal,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
                drawArc(
                    color = color,
                    startAngle = startAngle,
                    sweepAngle = sweepTotal * frac,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    value,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    fontFamily = FontFamily.Monospace,
                    color = TcwTokens.Ink,
                )
                if (unit.isNotBlank()) {
                    Text(unit, fontSize = 9.sp, color = TcwTokens.Muted)
                }
            }
        }
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = TcwTokens.Muted,
            fontWeight = FontWeight.Medium,
        )
    }
}

/** Grid of gauges bound to the live readings. */
@Composable
fun AiLiveGaugeGrid(readings: List<GaugeReading>, modifier: Modifier = Modifier) {
    if (readings.isEmpty()) return
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = modifier.fillMaxWidth().height(((readings.size + 2) / 3 * 120).dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(readings, key = { it.pid }) { r ->
            AiLiveGauge(
                label = r.label,
                value = r.value,
                unit = r.unit,
                fillFraction = r.fillFraction,
                color = gaugeColor(r.pid),
            )
        }
    }
}

private fun gaugeColor(pid: String): Color = when (pid.uppercase()) {
    "0C", "RPM" -> TcwTokens.Amber
    "05", "COOLANT", "ECT" -> TcwTokens.Red
    "0D", "SPEED" -> TcwTokens.Blue
    else -> TcwTokens.Green
}
