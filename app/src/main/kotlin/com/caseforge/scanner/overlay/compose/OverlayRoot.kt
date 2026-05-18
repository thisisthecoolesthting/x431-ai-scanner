@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.caseforge.scanner.overlay.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Minimize
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.caseforge.scanner.engine.CapabilityMap
import com.caseforge.scanner.engine.EngineState
import com.caseforge.scanner.engine.ScreenKind
import com.caseforge.scanner.ui.theme.CaseForgeTheme

/**
 * Root composable rendered inside the full-screen overlay window. This is what the
 * technician actually sees all day — X431 is hidden behind it.
 *
 * Reads [engineState] (live from the scraper) and renders the appropriate screen.
 * All user actions go through callbacks back into FullScreenOverlayService.
 */
@Composable
fun OverlayRoot(
    engineState: EngineState,
    alpha: Float,
    onMinimize: () -> Unit,
    onDismiss: () -> Unit,
    onPeek: () -> Unit,
    onCapability: (String) -> Unit,
) {
    CaseForgeTheme(mode = "dark") {
        Surface(
            modifier = Modifier.fillMaxSize().alpha(alpha),
            color = MaterialTheme.colorScheme.background,
        ) {
            Column(Modifier.fillMaxSize()) {
                OverlayTopBar(
                    state = engineState,
                    onMinimize = onMinimize,
                    onPeek = onPeek,
                    onDismiss = onDismiss,
                )
                engineState.errorBanner?.let { msg ->
                    ErrorBanner(msg)
                }
                Box(Modifier.weight(1f)) {
                    when (val k = engineState.screen) {
                        is ScreenKind.NoEngine -> NoEngineHint()
                        is ScreenKind.FullScanProgress -> ScanProgressView(engineState)
                        is ScreenKind.FullScanResults -> ScanResultsView(engineState)
                        is ScreenKind.LiveDataView -> LiveDataView(engineState)
                        is ScreenKind.ActuationTest -> ActuationView(engineState)
                        is ScreenKind.Dialog -> DialogPassThrough(k.text)
                        is ScreenKind.Unknown -> UnknownScreenView(k.hint)
                        else -> HomeView(engineState, onCapability)
                    }
                }
            }
        }
    }
}

@Composable
private fun OverlayTopBar(
    state: EngineState,
    onMinimize: () -> Unit,
    onPeek: () -> Unit,
    onDismiss: () -> Unit,
) {
    TopAppBar(
        title = {
            Column {
                Text("Launch AI", fontWeight = FontWeight.Bold)
                Text(
                    state.screen::class.simpleName ?: "—",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        actions = {
            IconButton(onClick = onPeek) {
                Icon(Icons.Default.Visibility, contentDescription = "Peek at X431")
            }
            IconButton(onClick = onMinimize) {
                Icon(Icons.Default.Minimize, contentDescription = "Minimize to bubble")
            }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = "Dismiss overlay")
            }
        },
    )
}

@Composable
private fun ErrorBanner(msg: String) {
    Surface(color = MaterialTheme.colorScheme.errorContainer) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
            Text(msg, color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun HomeView(state: EngineState, onCapability: (String) -> Unit) {
    var selectedCategory by remember { mutableStateOf(CapabilityMap.Category.Scan) }

    Column(Modifier.fillMaxSize().padding(12.dp).verticalScroll(rememberScrollState()),
           verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Vehicle card
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Default.DirectionsCar, contentDescription = null)
                    Column(Modifier.weight(1f)) {
                        Text(
                            state.vehicleVin?.let { "VIN: $it" } ?: "No vehicle detected yet",
                            fontWeight = FontWeight.SemiBold,
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

        // Category tabs
        ScrollableTabRow(
            selectedTabIndex = CapabilityMap.Category.values().indexOf(selectedCategory),
            edgePadding = 0.dp,
        ) {
            CapabilityMap.Category.values().forEach { c ->
                Tab(
                    selected = selectedCategory == c,
                    onClick = { selectedCategory = c },
                    text = { Text(c.name) },
                )
            }
        }

        // Capability cards
        val caps = CapabilityMap.byCategory(selectedCategory)
        caps.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                pair.forEach { cap ->
                    OutlinedCard(
                        onClick = { onCapability(cap.id) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(cap.label, fontWeight = FontWeight.SemiBold)
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

@Composable
private fun ScanProgressView(state: EngineState) {
    Column(
        Modifier.fillMaxSize().padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(64.dp))
        Spacer(Modifier.height(20.dp))
        Text("Full scan in progress…", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Text(
            state.currentMenuPath.joinToString(" › ").ifBlank { "Talking to every module" },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ScanResultsView(state: EngineState) {
    Column(Modifier.fillMaxSize().padding(14.dp).verticalScroll(rememberScrollState()),
           verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Scan complete", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text("${state.dtcs.size} DTC(s) found", color = MaterialTheme.colorScheme.onSurfaceVariant)
        HorizontalDivider()
        if (state.dtcs.isEmpty()) {
            Text("No diagnostic trouble codes stored. Clean scan.", color = Color(0xFF66BB6A))
        } else {
            state.dtcs.forEach { dtc ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(dtc.code, fontWeight = FontWeight.SemiBold)
                        dtc.module?.let { Text("Module: $it", style = MaterialTheme.typography.labelSmall) }
                        dtc.description?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveDataView(state: EngineState) {
    Column(Modifier.fillMaxSize().padding(14.dp).verticalScroll(rememberScrollState()),
           verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Live data", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        if (state.liveData.isEmpty()) {
            Text("Waiting for PIDs from the engine…", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            state.liveData.forEach { (k, v) ->
                Row(Modifier.fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp)) {
                    Text(k, modifier = Modifier.weight(1f))
                    Text("%.2f".format(v), fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun ActuationView(state: EngineState) {
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Actuation test", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text(
            "Bidirectional control is active. Watch the vehicle for the expected response. " +
                "If anything looks wrong, dismiss the overlay and use X431 directly.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun NoEngineHint() {
    Column(Modifier.fillMaxSize().padding(20.dp),
           verticalArrangement = Arrangement.Center,
           horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Default.Build, contentDescription = null, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(12.dp))
        Text("X431 engine not running", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text(
            "Tap any capability and Launch AI will start the X431 engine for you. " +
                "If nothing happens, install the X431 app from your tablet's store first.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DialogPassThrough(text: String) {
    Column(Modifier.fillMaxSize().padding(20.dp),
           verticalArrangement = Arrangement.Center,
           horizontalAlignment = Alignment.CenterHorizontally) {
        Text("X431 dialog: $text", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(12.dp))
        Text(
            "Tap Peek above to see the dialog and respond directly in X431.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun UnknownScreenView(hint: String) {
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Unrecognized screen", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(
            "Launch AI doesn't know this X431 screen yet. Tap Peek to use X431 directly, " +
                "or report this so we can add support.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Hint: $hint",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
        )
    }
}
