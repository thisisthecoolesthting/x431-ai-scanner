package com.caseforge.scanner.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Store
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.caseforge.scanner.engine.EngineState
import com.caseforge.scanner.presets.PresetCatalog
import com.caseforge.scanner.ui.components.TcwHeaderBar
import com.caseforge.scanner.ui.components.TcwMetricCard
import com.caseforge.scanner.ui.components.TcwPresetCard
import com.caseforge.scanner.ui.components.TcwResumeBar
import com.caseforge.scanner.ui.components.TcwSectionLabel
import com.caseforge.scanner.ui.components.TcwToolButton
import com.caseforge.scanner.ui.theme.TcwTokens

@Composable
fun TcwHomeScreen(
    vehicle: String?,
    connected: Boolean,
    engineState: EngineState,
    resumeText: String? = null,
    onResume: () -> Unit = {},
    onRunPreset: (String) -> Unit,
    onConnect: () -> Unit,
    onOpenCodes: () -> Unit,
    onOpenLiveData: () -> Unit,
    onOpenVehicle: () -> Unit,
    onOpenReport: () -> Unit,
    onOpenAdvanced: () -> Unit,
    onOpenShop: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAiDiagnostic: () -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = TcwTokens.PadScreen),
    ) {
        TcwHeaderBar(
            title = "Together Car Works",
            vehicle = vehicle,
            connected = connected,
            onMenu = onOpenSettings,
        )

        Spacer(modifier = Modifier.height(TcwTokens.PadScreen))

        Column(
            modifier = Modifier.padding(horizontal = TcwTokens.PadScreen),
            verticalArrangement = Arrangement.spacedBy(TcwTokens.Gap),
        ) {

            // Connect CTA or live gauges
            if (!connected) {
                Button(
                    onClick = onConnect,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(TcwTokens.RadiusMedium),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = TcwTokens.Amber,
                        contentColor = TcwTokens.OnAmber,
                    ),
                ) {
                    Text(
                        text = "Connect to Vehicle",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(vertical = 6.dp),
                    )
                }
            } else {
                val liveData = engineState.liveData
                val rpmRaw: Double? = liveData["RPM"] ?: liveData["rpm"] ?: liveData["ENGINE_RPM"]
                val coolantRaw: Double? = liveData["COOLANT_TEMP"]
                    ?: liveData["coolant_temp"]
                    ?: liveData["ENGINE_COOLANT_TEMP"]
                val batteryRaw: Double? = liveData["BATTERY_VOLTAGE"]
                    ?: liveData["battery_voltage"]
                    ?: liveData["CONTROL_MODULE_VOLTAGE"]

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(TcwTokens.Gap),
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        TcwMetricCard(
                            label = "Engine RPM",
                            value = if (rpmRaw != null) rpmRaw.toInt().toString() else "—",
                            unit = "rpm",
                            fillFraction = ((rpmRaw ?: 0.0).toFloat() / 7000f).coerceIn(0f, 1f),
                            color = TcwTokens.Amber,
                        )
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        TcwMetricCard(
                            label = "Coolant",
                            value = if (coolantRaw != null) coolantRaw.toInt().toString() else "—",
                            unit = "°C",
                            fillFraction = ((coolantRaw ?: 0.0).toFloat() / 120f).coerceIn(0f, 1f),
                            color = TcwTokens.Blue,
                        )
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        TcwMetricCard(
                            label = "Battery",
                            value = if (batteryRaw != null) "%.1f".format(batteryRaw) else "—",
                            unit = "V",
                            fillFraction = (((batteryRaw ?: 10.0).toFloat() - 10f) / 4.5f).coerceIn(0f, 1f),
                            color = TcwTokens.Green,
                        )
                    }
                }
            }

            // Resume bar
            if (resumeText != null) {
                TcwResumeBar(text = resumeText, onClick = onResume)
            }

            // One-tap jobs
            TcwSectionLabel(text = "One-tap jobs")

            val presets = PresetCatalog.all
            val grid = presets.take(4)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(TcwTokens.Gap),
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    TcwPresetCard(
                        title = grid[0].title,
                        subtitle = grid[0].subtitle,
                        icon = Icons.Default.Bolt,
                        accent = false,
                        dark = true,
                        onClick = { onRunPreset(grid[0].id) },
                    )
                }
                Box(modifier = Modifier.weight(1f)) {
                    TcwPresetCard(
                        title = grid[1].title,
                        subtitle = grid[1].subtitle,
                        icon = Icons.Default.Star,
                        accent = true,
                        dark = false,
                        onClick = { onRunPreset(grid[1].id) },
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(TcwTokens.Gap),
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    TcwPresetCard(
                        title = grid[2].title,
                        subtitle = grid[2].subtitle,
                        icon = Icons.Default.FactCheck,
                        accent = false,
                        dark = false,
                        onClick = { onRunPreset(grid[2].id) },
                    )
                }
                Box(modifier = Modifier.weight(1f)) {
                    TcwPresetCard(
                        title = grid[3].title,
                        subtitle = grid[3].subtitle,
                        icon = Icons.Default.Autorenew,
                        accent = false,
                        dark = false,
                        onClick = { onRunPreset(grid[3].id) },
                    )
                }
            }

            val roadTest = presets[4]
            TcwPresetCard(
                title = roadTest.title,
                subtitle = roadTest.subtitle,
                icon = Icons.Default.Route,
                accent = false,
                dark = true,
                onClick = { onRunPreset(roadTest.id) },
            )

            // AI Diagnostic Mode - headline feature
            TcwSectionLabel(text = "AI Diagnostic")
            androidx.compose.material3.Button(
                onClick = onOpenAiDiagnostic,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = TcwTokens.PadScreen)
                    .heightIn(min = 56.dp),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(TcwTokens.RadiusMedium),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = TcwTokens.Amber, contentColor = TcwTokens.OnAmber,
                ),
            ) {
                androidx.compose.material3.Icon(Icons.Default.AutoAwesome, contentDescription = null)
                androidx.compose.foundation.layout.Spacer(Modifier.width(8.dp))
                androidx.compose.material3.Text(
                    "Start AI Diagnostic",
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                )
            }
            androidx.compose.foundation.layout.Spacer(Modifier.height(TcwTokens.Gap))

            // Tools row
            TcwSectionLabel(text = "Tools")

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                TcwToolButton(
                    label = "Codes",
                    icon = Icons.Default.ListAlt,
                    onClick = onOpenCodes,
                )
                TcwToolButton(
                    label = "Live Data",
                    icon = Icons.Default.ShowChart,
                    onClick = onOpenLiveData,
                )
                TcwToolButton(
                    label = "Vehicle",
                    icon = Icons.Default.DirectionsCar,
                    onClick = onOpenVehicle,
                )
                TcwToolButton(
                    label = "Report",
                    icon = Icons.Default.Description,
                    onClick = onOpenReport,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                TcwToolButton(
                    label = "Advanced",
                    icon = Icons.Default.Key,
                    onClick = onOpenAdvanced,
                )
                TcwToolButton(
                    label = "Shop",
                    icon = Icons.Default.Store,
                    onClick = onOpenShop,
                )
                TcwToolButton(
                    label = "Settings",
                    icon = Icons.Default.Settings,
                    onClick = onOpenSettings,
                )
            }

            Spacer(modifier = Modifier.height(TcwTokens.Gap))
        }
    }
}
