package com.caseforge.scanner.ui.tuning

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.caseforge.scanner.R
import com.caseforge.scanner.planb.PlanbMarque

@Composable
fun ServiceResetReferenceScreen(
    session: TuningSessionContext,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val primaryVin = session.vehicleVin?.takeIf { it.isNotBlank() }
        ?: session.lastRecordedVin?.takeIf { it.isNotBlank() }
    val vinSuggested = remember(primaryVin) { PlanbMarque.fromVin(primaryVin) }
    var selectedMarque by remember(primaryVin) {
        mutableStateOf(vinSuggested?.takeIf { it in PlanbMarque.TRIAL_MARQUES } ?: PlanbMarque.FORD)
    }
    var bundle by remember { mutableStateOf<ReferenceBundle?>(null) }

    LaunchedEffect(Unit) {
        bundle = TuningReferenceLoader.loadServiceReset(context.applicationContext)
    }

    val procedures = remember(bundle, selectedMarque) {
        TuningReferenceLoader.filterForMarque(bundle, selectedMarque)
    }

    LazyColumn(
        modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            TuningMarqueChipRow(
                vinSuggestedMarque = vinSuggested,
                selectedMarque = selectedMarque,
                onMarqueSelected = { selectedMarque = it },
            )
        }
        item {
            Text(
                bundle?.disclaimer ?: stringResource(R.string.tuning_oem_disclaimer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        items(procedures, key = { it.id }) { proc ->
            ReferenceProcedureCard(proc)
        }
        if (procedures.isEmpty()) {
            item {
                Text(stringResource(R.string.tuning_reference_empty))
            }
        }
    }
}

@Composable
internal fun ReferenceProcedureCard(proc: ReferenceProcedure) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(proc.title, style = MaterialTheme.typography.titleSmall)
            if (proc.summary.isNotBlank()) {
                Text(proc.summary, style = MaterialTheme.typography.bodyMedium)
            }
            proc.steps.forEachIndexed { index, step ->
                Text("${index + 1}. $step", style = MaterialTheme.typography.bodySmall)
            }
            if (proc.requiresOemTool) {
                Text(
                    stringResource(R.string.tuning_requires_oem_launch),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
