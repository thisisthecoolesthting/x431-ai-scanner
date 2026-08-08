@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.caseforge.scanner.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.caseforge.scanner.data.SettingsRepo
import com.caseforge.scanner.engine.CapabilityCatalogStore
import com.caseforge.scanner.engine.CapabilityEntry
import com.caseforge.scanner.planb.CapabilityWedgeFilter
import com.caseforge.scanner.planb.MarqueWedgeConfig
import com.caseforge.scanner.vin.VinNormalizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

/**
 * Read-only browser for bundled/merged [capabilities.json], filtered by marque wedge resolved from VIN.
 * Does not execute OEM capabilities.
 */
@Composable
fun CapabilitiesBrowseScreen(
    settings: SettingsRepo,
    vehicleVin: String?,
    onBack: () -> Unit,
    onNavigateImmo: () -> Unit,
    onNavigateProgramming: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current.applicationContext
    var loaded by remember { mutableStateOf<List<CapabilityEntry>?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf<CapabilityEntry?>(null) }
    var forcedCardId by remember { mutableStateOf<String?>(null) }

    val vinEffective = remember(vehicleVin, settings.fastWorkflowState.lastVin) {
        vehicleVin?.trim()?.takeIf { VinNormalizer.normalizeOcrText(it).length >= VinNormalizer.VIN_LENGTH }
            ?: settings.fastWorkflowState.lastVin?.trim()?.takeIf {
                VinNormalizer.normalizeOcrText(it).length >= VinNormalizer.VIN_LENGTH
            }
        ?.let { VinNormalizer.normalizeOcrText(it) }
    }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                val store = CapabilityCatalogStore(context, context.cacheDir, OkHttpClient())
                loaded = store.load().capabilities
                loadError = null
            } catch (t: Throwable) {
                loaded = emptyList()
                loadError = t.message ?: "load failed"
            }
        }
    }

    val matrix = remember { MarqueWedgeConfig.load(context) }
    val wedgeCard = remember(vinEffective, matrix) {
        if (vinEffective.isNullOrBlank()) null
        else matrix?.let { MarqueWedgeConfig.findCardForVin(vinEffective, it) }
    }

    val selectedCard = remember(forcedCardId, matrix, wedgeCard) {
        val forced = forcedCardId
        if (!forced.isNullOrBlank()) {
            matrix?.platformCards?.firstOrNull { it.id.equals(forced, ignoreCase = true) } ?: wedgeCard
        } else {
            wedgeCard
        }
    }

    val filtered = remember(loaded, selectedCard) {
        val all = loaded ?: emptyList()
        all.filter { CapabilityWedgeFilter.matchesWedge(selectedCard, it) }
            .sortedWith(compareBy({ it.category }, { it.label }))
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Capabilities (browse)") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            },
        )
        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = when {
                    vinEffective.isNullOrBlank() ->
                        "No VIN — choose a marque profile below or connect vehicle for auto-detect."
                    selectedCard == null ->
                        "VIN ${vinEffective.take(11)}… — no wedge card for this WMI/model year; list shows global-scope rows only."
                    else ->
                        "${selectedCard.marque} ${selectedCard.platformCode} · ${selectedCard.model} ${selectedCard.modelYearStart}-${selectedCard.modelYearEnd} — ${filtered.size} rows for this wedge."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            matrix?.let { mx ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    FilterChip(
                        selected = forcedCardId == null,
                        onClick = { forcedCardId = null },
                        label = { Text("Auto") },
                    )
                    mx.platformCards.forEach { card ->
                        FilterChip(
                            selected = forcedCardId?.equals(card.id, ignoreCase = true) == true,
                            onClick = { forcedCardId = card.id },
                            label = { Text("${card.marque} ${card.platformCode}") },
                        )
                    }
                }
            }
            loadError?.let { err ->
                Text(
                    text = "Load note: $err",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (loaded == null) {
                Text("Loading catalog…", style = MaterialTheme.typography.bodyMedium)
            } else if (filtered.isEmpty()) {
                Text("No capability rows match this filter.", style = MaterialTheme.typography.bodyMedium)
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                ) {
                    items(filtered, key = { it.id }) { row ->
                        ListItem(
                            headlineContent = { Text(row.label) },
                            supportingContent = {
                                Text(
                                    "${row.category} · ${row.id}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                            modifier = Modifier.clickable { selected = row },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    selected?.let { row ->
        val skreem = CapabilityWedgeFilter.isSkreemImmobilizerRow(row)
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text(row.label) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("done_when", style = MaterialTheme.typography.labelMedium)
                    Text(row.doneWhen.ifBlank { "—" }, style = MaterialTheme.typography.bodySmall)
                    Text("path", style = MaterialTheme.typography.labelMedium)
                    Text(
                        row.path.joinToString(" → ").ifBlank { "—" },
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                    )
                    Text("note", style = MaterialTheme.typography.labelMedium)
                    Text(row.note.ifBlank { "—" }, style = MaterialTheme.typography.bodySmall)
                    if (skreem) {
                        Text(
                            "SKREEM / immobilizer — display only; use Plan B Immo or Programming for checklists.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            },
            confirmButton = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (skreem) {
                        TextButton(
                            onClick = {
                                selected = null
                                onNavigateImmo()
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Open Immobilizer info")
                        }
                        TextButton(
                            onClick = {
                                selected = null
                                onNavigateProgramming()
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Open Programming reference")
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { selected = null }) { Text("Close") }
                    }
                }
            },
        )
    }
}
