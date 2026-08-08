package com.caseforge.scanner.ui.planb

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.caseforge.scanner.planb.PlanbMarque
import com.caseforge.scanner.planb.coding.CodingChecklist
import com.caseforge.scanner.planb.coding.CodingChecklistLoader
import com.caseforge.scanner.planb.coding.ReversibleCodingOp
import com.caseforge.scanner.ui.components.LoadingState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun CodingChecklistScreen(
    vehicleVin: String?,
    lastRecordedVin: String?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val primaryVin = vehicleVin?.takeIf { it.isNotBlank() }
        ?: lastRecordedVin?.takeIf { it.isNotBlank() }
    val vinSuggested = remember(primaryVin) { PlanbMarque.fromVin(primaryVin) }

    var selectedMarque by remember(primaryVin) { mutableStateOf(vinSuggested ?: PlanbMarque.JEEP) }
    var checklist by remember { mutableStateOf<CodingChecklist?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(vinSuggested) {
        val hinted = vinSuggested ?: return@LaunchedEffect
        launch(Dispatchers.IO) {
            CodingChecklistLoader.preload(context = context.applicationContext, marque = hinted)
        }
    }

    LaunchedEffect(selectedMarque) {
        isLoading = true
        checklist = withContext(Dispatchers.IO) {
            CodingChecklistLoader.load(context = context.applicationContext, marque = selectedMarque)
        }
        isLoading = false
    }

    val entries = checklist?.entries.orEmpty()

    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            PlanbMarqueChipRow(
                vinSuggestedMarque = vinSuggested,
                selectedMarque = selectedMarque,
                onMarqueSelected = { selectedMarque = it },
            )
        }
        item {
            Text(
                "Reversible coding checklist (read-only until G4)",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        when {
            checklist == null || isLoading -> item {
                LoadingState(
                    message = "Loading coding checklist",
                    animatedDots = true,
                    showLinearProgress = true,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
            entries.isEmpty() -> item {
                Text(
                    "No entries in checklist for ${selectedMarque.id}.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            else -> items(entries, key = { it.id }) { op ->
                CodingOpRow(op = op)
            }
        }
    }
}

@Composable
private fun CodingOpRow(op: ReversibleCodingOp) {
    Card(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(op.title.ifBlank { op.id }, style = MaterialTheme.typography.titleSmall)
            if (op.description.isNotBlank()) {
                Text(op.description, style = MaterialTheme.typography.bodySmall)
            }
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "Rollback: ${if (op.rollbackSupported) "yes" else "no"} · mode=${op.applyMode}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    enabled = false,
                    onClick = {},
                ) {
                    Text("Apply")
                }
            }
            Text(
                "Disabled — G4 gate",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}
