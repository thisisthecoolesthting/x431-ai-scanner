package com.caseforge.scanner.ui.session

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.caseforge.scanner.agent.session.BackgroundObdSnapshot
import kotlin.math.cos
import kotlin.math.sin

private const val RPM_MAX = 8000f

/** Upper semicircle tach: 0 RPM at left, 8k at right (Compose arc: 180° sweep clockwise). */
private fun needleAngleDegrees(rpm: Float): Float {
    val f = (rpm.coerceIn(0f, RPM_MAX) / RPM_MAX).coerceIn(0f, 1f)
    return 180f - f * 180f
}

@Composable
fun SessionRpmTachLarge(
    snapshot: BackgroundObdSnapshot,
    modifier: Modifier = Modifier,
) {
    SessionRpmTachInner(
        snapshot = snapshot,
        diameter = 220.dp,
        strokeWidth = 10.dp,
        needleLengthRatio = 0.38f,
        showSubtitle = true,
        centerValueSp = 22.sp,
        modifier = modifier,
    )
}

@Composable
fun SessionRpmTachCompact(
    snapshot: BackgroundObdSnapshot,
    modifier: Modifier = Modifier,
) {
    SessionRpmTachInner(
        snapshot = snapshot,
        diameter = 112.dp,
        strokeWidth = 6.dp,
        needleLengthRatio = 0.36f,
        showSubtitle = false,
        centerValueSp = 14.sp,
        modifier = modifier,
    )
}

@Composable
private fun SessionRpmTachInner(
    snapshot: BackgroundObdSnapshot,
    diameter: Dp,
    strokeWidth: Dp,
    needleLengthRatio: Float,
    showSubtitle: Boolean,
    centerValueSp: TextUnit,
    modifier: Modifier = Modifier,
) {
    val live = snapshot.connected && snapshot.rpm != null
    val targetRpm = if (live) snapshot.rpm!!.coerceIn(0f, RPM_MAX) else 0f
    val animatedRpm by animateFloatAsState(
        targetValue = targetRpm,
        animationSpec = tween(durationMillis = 450),
        label = "rpm_needle",
    )
    val needleDeg = needleAngleDegrees(animatedRpm)

    val trackColor = if (live) {
        MaterialTheme.colorScheme.surfaceVariant
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
    }
    val needleColor = if (live) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline

    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(diameter)) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val cx = w / 2f
                val cy = h * 0.92f
                val r = (w.coerceAtMost(h) / 2f) * 0.88f
                val sw = strokeWidth.toPx()

                drawArc(
                    color = trackColor,
                    startAngle = 180f,
                    sweepAngle = 180f,
                    useCenter = false,
                    topLeft = Offset(cx - r, cy - r),
                    size = Size(r * 2, r * 2),
                    style = Stroke(width = sw, cap = StrokeCap.Round),
                )

                val rad = Math.toRadians(needleDeg.toDouble())
                val nl = r * needleLengthRatio
                val x2 = cx + (nl * cos(rad)).toFloat()
                val y2 = cy - (nl * sin(rad)).toFloat()
                drawLine(
                    color = needleColor,
                    start = Offset(cx, cy),
                    end = Offset(x2, y2),
                    strokeWidth = sw * 0.45f,
                    cap = StrokeCap.Round,
                )
                drawCircle(color = needleColor, radius = sw * 0.55f, center = Offset(cx, cy))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (live) "${animatedRpm.toInt()}" else "—",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontSize = centerValueSp,
                        fontWeight = FontWeight.Bold,
                    ),
                    color = if (live) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
                )
                Text(
                    text = "RPM",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (showSubtitle) {
            Text(
                text = if (live) "Live (0–8000)" else "Connect OBD for live RPM",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Mini tach row when OBD is not linked but the user has started chatting (no duplicate when [BackgroundObdSnapshot.connected]). */
@Composable
fun SessionRpmTachStubStrip(
    snapshot: BackgroundObdSnapshot,
    modifier: Modifier = Modifier,
) {
    Surface(
        tonalElevation = 1.dp,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SessionRpmTachCompact(snapshot = snapshot)
            Column {
                Text(
                    "Engine speed",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    if (snapshot.connected && snapshot.rpm != null) {
                        "${snapshot.rpm!!.toInt()} RPM"
                    } else {
                        "— RPM · Connect OBD for live RPM"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
