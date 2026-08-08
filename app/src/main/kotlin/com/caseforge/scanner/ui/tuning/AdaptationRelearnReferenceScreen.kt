package com.caseforge.scanner.ui.tuning

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
fun AdaptationRelearnReferenceScreen(
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
        bundle = TuningReferenceLoader.loadAdaptation(context.applicationContext)
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
