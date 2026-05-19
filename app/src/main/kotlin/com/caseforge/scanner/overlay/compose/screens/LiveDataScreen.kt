package com.caseforge.scanner.overlay.compose.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import com.caseforge.scanner.engine.EngineState
import com.caseforge.scanner.engine.PidCatalog
import com.caseforge.scanner.engine.ScreenKind
import com.caseforge.scanner.overlay.compose.Spacing
import com.caseforge.scanner.ui.theme.TogetherCarWorksTheme

/**
 * Renders live data stream: PIDs polled from the engine with explicit units, session min/max,
 * and guidance for the default fast PID set.
 */
@Suppress("UNUSED_PARAMETER")
@Composable
fun LiveDataScreen(
    state: EngineState,
    onAction: (UiAction) -> Unit,
) {
    val minMax = remember { mutableStateMapOf<String, MinMaxSession>() }
    var statsGeneration by rememberSaveable { mutableStateOf(0) }

    LaunchedEffect(state.liveData, state.updatedAtMs, statsGeneration) {
        state.liveData.forEach { (key, value) ->
            val label = PidCatalog.canonicalLabel(key)
            val entry = minMax.getOrPut(label) { MinMaxSession(value, value) }
            entry.min = minOf(entry.min, value)
            entry.max = maxOf(entry.max, value)
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(Spacing.Space14)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Spacing.Space8),
    ) {
        Text(
            "Live data",
            style = MaterialTheme.typography.headlineSmall,
        )

        PerformanceHintCard()

        FastPidSetCard()

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Samples",
                style = MaterialTheme.typography.titleSmall,
            )
            if (minMax.isNotEmpty()) {
                TextButton(
                    onClick = {
                        minMax.clear()
                        statsGeneration++
                    },
                ) {
                    Text("Reset min/max")
                }
            }
        }

        if (state.busy && state.liveData.isEmpty()) {
            Text(
                "Starting PID stream…",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        } else if (state.liveData.isEmpty()) {
            Text(
                "Waiting for PIDs from the engine…",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                "Select parameters in the OEM live-data screen or connect OBD to populate readings.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        } else {
            PidCatalog.orderedLiveEntries(state.liveData).forEach { (key, value) ->
                val label = PidCatalog.canonicalLabel(key)
                val unit = PidCatalog.unitFor(key)
                val stats = minMax[label]
                LivePidRow(
                    label = label,
                    valueText = PidCatalog.formatValue(key, value),
                    unit = unit,
                    minText = stats?.min?.let { PidCatalog.formatValue(key, it) },
                    maxText = stats?.max?.let { PidCatalog.formatValue(key, it) },
                )
            }
        }
    }
}

@Composable
private fun PerformanceHintCard() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Spacing.Space8),
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Text(
            PidCatalog.PERFORMANCE_HINT,
            modifier = Modifier.padding(Spacing.Space10),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

@Composable
private fun FastPidSetCard() {
    Column(
        Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surfaceVariant,
                RoundedCornerShape(Spacing.Space8),
            )
            .padding(Spacing.Space10),
        verticalArrangement = Arrangement.spacedBy(Spacing.Space6),
    ) {
        Text(
            "Fast default set",
            style = MaterialTheme.typography.titleSmall,
        )
        Text(
            "Recommended for smooth refresh when you control PID selection:",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        PidCatalog.FAST_DEFAULT.forEach { def ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    def.label,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = buildString {
                        append("PID ")
                        append(def.pollId)
                        if (def.unit.isNotEmpty()) {
                            append(" · ")
                            append(def.unit)
                        }
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}

@Composable
private fun LivePidRow(
    label: String,
    valueText: String,
    unit: String,
    minText: String?,
    maxText: String?,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surfaceVariant,
                RoundedCornerShape(Spacing.Space6),
            )
            .padding(horizontal = Spacing.Space10, vertical = Spacing.Space8),
        verticalArrangement = Arrangement.spacedBy(Spacing.Space4),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
        )
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    valueText,
                    style = MaterialTheme.typography.titleLarge,
                    fontFamily = FontFamily.Monospace,
                )
                if (unit.isNotEmpty()) {
                    Text(
                        " $unit",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = Spacing.Space4, bottom = Spacing.Space4),
                    )
                }
            }
        }
        if (minText != null && maxText != null) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                MinMaxChip(title = "Min", valueText = minText, unit = unit)
                MinMaxChip(title = "Max", valueText = maxText, unit = unit)
            }
        }
    }
}

@Composable
private fun MinMaxChip(title: String, valueText: String, unit: String) {
    Column {
        Text(
            title,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = if (unit.isEmpty()) valueText else "$valueText $unit",
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
        )
    }
}

private data class MinMaxSession(var min: Double, var max: Double)

@Preview(showBackground = true)
@Composable
private fun LiveDataScreenEmptyPreview() {
    TogetherCarWorksTheme(mode = "dark") {
        Surface(color = MaterialTheme.colorScheme.background) {
            LiveDataScreen(
                state = EngineState(
                    screen = ScreenKind.LiveDataView,
                    liveData = emptyMap(),
                ),
                onAction = {},
            )
        }
    }
}

@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun LiveDataScreenWithDataPreviewDark() {
    TogetherCarWorksTheme(isDarkMode = true) {
        Surface(color = MaterialTheme.colorScheme.background) {
            LiveDataScreen(
                state = EngineState(
                    screen = ScreenKind.LiveDataView,
                    liveData = mapOf(
                        "Engine RPM" to 1250.0,
                        "0D" to 35.0,
                        "Coolant Temp" to 85.0,
                        "STFT B1" to 2.5,
                        "LTFT B1" to -4.2,
                        "Battery Voltage" to 13.82,
                    ),
                    updatedAtMs = System.currentTimeMillis(),
                ),
                onAction = {},
            )
        }
    }
}
