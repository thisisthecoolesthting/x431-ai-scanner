package com.caseforge.scanner.overlay.compose.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.caseforge.scanner.engine.CapabilityMap
import com.caseforge.scanner.engine.EngineState
import com.caseforge.scanner.engine.ScreenKind
import com.caseforge.scanner.overlay.compose.Spacing
import com.caseforge.scanner.overlay.compose.TogetherCardShape
import com.caseforge.scanner.overlay.compose.togetherCardColors
import com.caseforge.scanner.overlay.compose.togetherCardElevation
import com.caseforge.scanner.ui.theme.CaseForgeTheme

/**
 * Renders module list, menu navigation, and capability cards.
 *
 * This screen is shown for ScreenKind.NoEngine (hint to start engine),
 * ScreenKind.HomeMenu (main menu), and other menu-like states.
 *
 * Polish improvements:
 * - All Cards use TogetherCardShape and consistent elevation
 * - Spacing tokens throughout (no raw .dp)
 * - Typography hierarchy: titles use titleMedium, descriptions use bodySmall
 *
 * Displays:
 * - Vehicle info card (VIN, summary, busy indicator)
 * - Capability category tabs
 * - Capability cards in a 2-column grid
 *
 * All text and colors routed through MaterialTheme (C1 requirement).
 */
@Composable
fun ModuleListScreen(
    state: EngineState,
    onAction: (UiAction) -> Unit,
) {
    var selectedCategory by remember { mutableStateOf(CapabilityMap.Category.Scan) }

    Column(
        Modifier
            .fillMaxSize()
            .padding(Spacing.Space12)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Spacing.Space12),
    ) {
        // Vehicle card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = TogetherCardShape,
            colors = togetherCardColors(),
            elevation = togetherCardElevation(),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(Spacing.Space14),
                verticalArrangement = Arrangement.spacedBy(Spacing.Space6),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.Space10),
                ) {
                    Icon(Icons.Default.DirectionsCar, contentDescription = null)
                    Column(Modifier.weight(1f)) {
                        Text(
                            state.vehicleVin?.let { "VIN: $it" } ?: "No vehicle detected yet",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            state.vehicleSummary ?: state.currentMenuPath.joinToString(" › ").ifBlank {
                                "Plug in VCI and start a scan."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (state.busy) {
                        CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }

        // Engine not running hint (only shown for NoEngine)
        if (state.screen is ScreenKind.NoEngine) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = TogetherCardShape,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
                elevation = togetherCardElevation(),
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(Spacing.Space14),
                    verticalArrangement = Arrangement.spacedBy(Spacing.Space8),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.Space10),
                    ) {
                        Icon(Icons.Default.Build, contentDescription = null)
                        Text(
                            "X431 engine not running",
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                    Text(
                        "Tap any capability below and Launch AI will start the X431 engine for you. " +
                            "If nothing happens, install the X431 app from your tablet's store first.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // Category tabs
        ScrollableTabRow(
            selectedTabIndex = CapabilityMap.Category.values().indexOf(selectedCategory),
            edgePadding = 0.dp,
        ) {
            CapabilityMap.Category.values().forEach { c ->
                Tab(
                    selected = selectedCategory == c,
                    onClick = { selectedCategory = c },
                    text = { Text(c.name, style = MaterialTheme.typography.labelLarge) },
                )
            }
        }

        // Capability cards
        val caps = CapabilityMap.byCategory(selectedCategory)
        caps.chunked(2).forEach { pair ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.Space8),
                modifier = Modifier.fillMaxWidth(),
            ) {
                pair.forEach { cap ->
                    OutlinedCard(
                        onClick = {
                            onAction(
                                if (selectedCategory == CapabilityMap.Category.Sequences) {
                                    UiAction.RunSequence(cap.id)
                                } else {
                                    UiAction.TapCapability(cap.id)
                                },
                            )
                        },
                        modifier = Modifier.weight(1f),
                        shape = TogetherCardShape,
                        colors = CardDefaults.outlinedCardColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                        ),
                    ) {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .padding(Spacing.Space12),
                            verticalArrangement = Arrangement.spacedBy(Spacing.Space4),
                        ) {
                            Text(
                                cap.label,
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                cap.note ?: cap.path.joinToString(" › "),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                if (pair.size == 1) Box(Modifier.weight(1f))
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ModuleListScreenPreview() {
    CaseForgeTheme(mode = "dark") {
        Surface(color = MaterialTheme.colorScheme.background) {
            ModuleListScreen(
                state = EngineState(
                    screen = ScreenKind.NoEngine,
                    vehicleVin = "5FNRL6H73LB123456",
                    vehicleSummary = "2020 Honda Odyssey",
                    currentMenuPath = listOf("Scan", "Full System"),
                    busy = false,
                ),
                onAction = {},
            )
        }
    }
}

@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ModuleListScreenDarkPreview() {
    CaseForgeTheme(isDarkMode = true) {
        Surface(color = MaterialTheme.colorScheme.background) {
            ModuleListScreen(
                state = EngineState(
                    screen = ScreenKind.HomeMenu,
                    vehicleVin = "5FNRL6H73LB123456",
                    vehicleSummary = "2020 Honda Odyssey",
                    currentMenuPath = listOf("Main Menu"),
                    busy = false,
                ),
                onAction = {},
            )
        }
    }
}
