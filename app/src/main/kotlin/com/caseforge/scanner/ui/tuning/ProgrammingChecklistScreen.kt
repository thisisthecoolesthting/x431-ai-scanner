package com.caseforge.scanner.ui.tuning

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import com.caseforge.scanner.planb.immo.SkreemModule
import com.caseforge.scanner.planb.programming.FlashOp
import com.caseforge.scanner.planb.programming.ProgrammingChecklist
import com.caseforge.scanner.planb.programming.ProgrammingChecklistLoader
import com.caseforge.scanner.ui.components.LoadingState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun ProgrammingChecklistScreen(
    session: TuningSessionContext,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val primaryVin = session.vehicleVin?.takeIf { it.isNotBlank() }
        ?: session.lastRecordedVin?.takeIf { it.isNotBlank() }
    val vinSuggested = remember(primaryVin) { PlanbMarque.fromVin(primaryVin) }
    var selectedMarque by remember(primaryVin) {
        mutableStateOf(vinSuggested?.takeIf { it in PlanbMarque.TRIAL_MARQUES } ?: PlanbMarque.JEEP)
    }
    var checklist by remember { mutableStateOf<ProgrammingChecklist?>(null) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(selectedMarque) {
        loading = true
        checklist = withContext(Dispatchers.IO) {
            ProgrammingChecklistLoader.load(context.applicationContext, selectedMarque)
        }
        loading = false
    }

    LazyColumn(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Text(
                stringResource(R.string.tuning_programming_read_only),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            TuningMarqueChipRow(
                vinSuggestedMarque = vinSuggested,
                selectedMarque = selectedMarque,
                onMarqueSelected = { selectedMarque = it },
            )
        }
        if (SkreemModule.isStellantisMarque(selectedMarque)) {
            item {
                Text(
                    SkreemModule.TIER4_BLOCKED_BANNER,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        when {
            loading -> item {
                LoadingState(
                    message = stringResource(R.string.tuning_programming_loading),
                    animatedDots = true,
                    showLinearProgress = true,
                )
            }
            checklist?.entries.isNullOrEmpty() -> item {
                Text(stringResource(R.string.tuning_programming_empty))
            }
            else -> itemsIndexed(
                checklist!!.entries,
                key = { index, op -> "tuning-prog-$index-${op.id}" },
            ) { _, op ->
                ProgrammingChecklistRow(op)
            }
        }
    }
}

@Composable
private fun ProgrammingChecklistRow(op: FlashOp) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        ),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(op.title, style = MaterialTheme.typography.titleSmall)
            if (op.description.isNotBlank()) {
                Text(op.description, style = MaterialTheme.typography.bodySmall)
            }
            Text(
                stringResource(R.string.tuning_programming_blocked_badge),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}
