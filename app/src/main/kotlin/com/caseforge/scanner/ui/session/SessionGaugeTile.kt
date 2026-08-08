package com.caseforge.scanner.ui.session

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.caseforge.scanner.R
import com.caseforge.scanner.agent.session.BackgroundObdSnapshot
import java.util.concurrent.ConcurrentHashMap

/** Live gauge kinds shown as collapsible tiles in the session visual area. */
enum class GaugeKind(val title: String) {
    RPM("RPM"),
    COOLANT("Coolant"),
    VOLTAGE("Voltage"),
}

/**
 * Per-session dismiss + collapse memory (in-process; survives recomposition within the chat).
 */
object SessionGaugeUiState {
    private data class SessionState(
        val dismissed: MutableSet<GaugeKind> = mutableSetOf(),
        val collapsed: MutableSet<GaugeKind> = mutableSetOf(),
    )

    private val bySession = ConcurrentHashMap<String, SessionState>()

    private fun state(sessionId: String): SessionState =
        bySession.getOrPut(sessionId) { SessionState() }

    fun clearSession(sessionId: String) {
        bySession.remove(sessionId)
    }

    fun isVisible(sessionId: String, kind: GaugeKind): Boolean =
        kind !in state(sessionId).dismissed

    fun isExpanded(sessionId: String, kind: GaugeKind): Boolean =
        kind !in state(sessionId).collapsed

    fun dismiss(sessionId: String, kind: GaugeKind) {
        state(sessionId).dismissed += kind
    }

    fun toggleCollapsed(sessionId: String, kind: GaugeKind) {
        val s = state(sessionId)
        if (kind in s.collapsed) s.collapsed -= kind else s.collapsed += kind
    }
}

@Composable
fun SessionGaugePane(
    sessionId: String,
    kinds: List<GaugeKind>,
    snapshot: BackgroundObdSnapshot,
    modifier: Modifier = Modifier,
) {
    val visible = kinds.filter { SessionGaugeUiState.isVisible(sessionId, it) }
    if (visible.isEmpty()) return

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        visible.forEach { kind ->
            SessionGaugeTile(
                kind = kind,
                sessionId = sessionId,
                snapshot = snapshot,
                expanded = SessionGaugeUiState.isExpanded(sessionId, kind),
                onToggleExpand = { SessionGaugeUiState.toggleCollapsed(sessionId, kind) },
                onClose = { SessionGaugeUiState.dismiss(sessionId, kind) },
                modifier = Modifier.widthIn(min = 132.dp, max = 200.dp),
            )
        }
    }
}

@Composable
fun SessionGaugeTile(
    kind: GaugeKind,
    sessionId: String,
    snapshot: BackgroundObdSnapshot,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 2.dp,
        modifier = modifier,
    ) {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    kind.title,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Row {
                    IconButton(onClick = onToggleExpand, modifier = Modifier.size(32.dp)) {
                        Icon(
                            if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = stringResource(
                                if (expanded) R.string.a11y_collapse_gauge else R.string.a11y_expand_gauge,
                            ),
                        )
                    }
                    IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(R.string.a11y_close_gauge),
                        )
                    }
                }
            }
            if (expanded) {
                when (kind) {
                    GaugeKind.RPM -> GaugeRpmBody(snapshot)
                    GaugeKind.COOLANT -> GaugeNumericBarBody(
                        label = "Coolant",
                        valueText = snapshot.coolantC?.let { "%.0f °C".format(it) } ?: "—",
                        live = snapshot.connected && snapshot.coolantC != null,
                        fraction = snapshot.coolantC?.let { (it / 120f).coerceIn(0f, 1f) },
                        barColor = MaterialTheme.colorScheme.primary,
                    )
                    GaugeKind.VOLTAGE -> GaugeNumericBarBody(
                        label = "Battery",
                        valueText = snapshot.voltage?.let { "%.1f V".format(it) } ?: "—",
                        live = snapshot.connected && snapshot.voltage != null,
                        fraction = snapshot.voltage?.let { ((it - 10f) / 5f).coerceIn(0f, 1f) },
                        barColor = MaterialTheme.colorScheme.tertiary,
                    )
                }
            } else {
                Text(
                    collapsedSummary(kind, snapshot),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun GaugeRpmBody(snapshot: BackgroundObdSnapshot) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth(),
    ) {
        SessionRpmTachCompact(snapshot = snapshot)
        if (snapshot.rpmHistory.size >= 2) {
            Sparkline(
                series = snapshot.rpmHistory,
                label = "RPM trend",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp)
                    .padding(top = 4.dp),
            )
        } else if (!snapshot.connected) {
            Text(
                "Connect OBD for live RPM",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun GaugeNumericBarBody(
    label: String,
    valueText: String,
    live: Boolean,
    fraction: Float?,
    barColor: Color,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp),
    ) {
        Text(
            valueText,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = if (live) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
        )
        MiniGaugeBar(
            fraction = fraction ?: 0f,
            active = live,
            color = barColor,
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp),
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MiniGaugeBar(
    fraction: Float,
    active: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    val fill = if (active) color else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
    Canvas(modifier = modifier) {
        val h = size.height
        drawRoundRect(
            color = track,
            size = size,
            cornerRadius = CornerRadius(h / 2, h / 2),
        )
        if (fraction > 0f) {
            drawRoundRect(
                color = fill,
                topLeft = Offset.Zero,
                size = Size(size.width * fraction.coerceIn(0f, 1f), h),
                cornerRadius = CornerRadius(h / 2, h / 2),
            )
        }
    }
}

private fun collapsedSummary(kind: GaugeKind, snapshot: BackgroundObdSnapshot): String = when (kind) {
    GaugeKind.RPM -> when {
        snapshot.connected && snapshot.rpm != null -> "${snapshot.rpm!!.toInt()} RPM"
        else -> "— RPM"
    }
    GaugeKind.COOLANT -> snapshot.coolantC?.let { "%.0f °C".format(it) } ?: "— °C"
    GaugeKind.VOLTAGE -> snapshot.voltage?.let { "%.1f V".format(it) } ?: "— V"
}
