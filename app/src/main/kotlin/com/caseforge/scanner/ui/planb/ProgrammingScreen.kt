package com.caseforge.scanner.ui.planb

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.caseforge.scanner.R
import com.caseforge.scanner.planb.immo.SkreemModule
import com.caseforge.scanner.planb.PlanbMarque
import com.caseforge.scanner.planb.Tier4ManualFlashRunbook
import com.caseforge.scanner.planb.programming.FlashOp
import com.caseforge.scanner.planb.programming.FlashRequestResult
import com.caseforge.scanner.planb.programming.ProgrammingChecklist
import com.caseforge.scanner.planb.programming.ProgrammingChecklistLoader
import com.caseforge.scanner.planb.programming.ProgrammingSession
import com.caseforge.scanner.ui.components.LoadingState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Static copy for shops coordinating Tier 4 work outside the app. */
private const val PARTNER_HANDOFF_TEXT =
    "PCM flash, skim, and security provisioning stay on licensed OEM or partner tools. " +
        "Use this list as a checklist only—confirm each step with your authorized workflow " +
        "before changing vehicle state."

@Composable
fun ProgrammingScreen(
    vehicleVin: String?,
    lastRecordedVin: String?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val primaryVin = vehicleVin?.takeIf { it.isNotBlank() }
        ?: lastRecordedVin?.takeIf { it.isNotBlank() }
    val vinSuggested = remember(primaryVin) { PlanbMarque.fromVin(primaryVin) }

    var selectedMarque by remember(primaryVin) { mutableStateOf(vinSuggested ?: PlanbMarque.JEEP) }
    var checklist by remember { mutableStateOf<ProgrammingChecklist?>(null) }
    var showRunbook by remember { mutableStateOf(false) }
    var runbookLines by remember { mutableStateOf<List<String>>(emptyList()) }
    var checklistLoading by remember { mutableStateOf(true) }
    var runbookLoading by remember { mutableStateOf(true) }

    LaunchedEffect(vinSuggested) {
        val hinted = vinSuggested ?: return@LaunchedEffect
        launch(Dispatchers.IO) {
            ProgrammingChecklistLoader.preload(context = context.applicationContext, marque = hinted)
        }
    }

    LaunchedEffect(Unit) {
        runbookLoading = true
        runbookLines = withContext(Dispatchers.IO) {
            Tier4ManualFlashRunbook.loadLines(context.applicationContext)
        }
        runbookLoading = false
    }

    LaunchedEffect(selectedMarque) {
        checklistLoading = true
        checklist = withContext(Dispatchers.IO) {
            ProgrammingChecklistLoader.load(context = context.applicationContext, marque = selectedMarque)
        }
        checklistLoading = false
    }

    if (showRunbook) {
        AlertDialog(
            onDismissRequest = { showRunbook = false },
            title = { Text(Tier4ManualFlashRunbook.titleShort()) },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    if (runbookLoading) {
                        LoadingState(
                            message = "Loading runbook",
                            animatedDots = true,
                            showLinearProgress = true,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    }
                    for (line in runbookLines) {
                        Text(
                            text = line.ifBlank { " " },
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(vertical = 2.dp),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showRunbook = false }) {
                    Text("Close")
                }
            },
        )
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
                PARTNER_HANDOFF_TEXT,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        item {
            TextButton(
                onClick = { showRunbook = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "${Tier4ManualFlashRunbook.titleShort()} — tap to open bundled runbook (Jeep/Ford/Dodge)",
                    style = MaterialTheme.typography.titleSmall,
                )
            }
        }
        if (SkreemModule.isStellantisMarque(selectedMarque)) {
            item {
                Text(
                    SkreemModule.TIER4_BLOCKED_BANNER,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        item {
            Text(
                "Programming reference (checklist from bundled data — Tier 4 partner/manual only)",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        when {
            checklist == null || checklistLoading -> item {
                LoadingState(
                    message = "Loading programming checklist",
                    animatedDots = true,
                    showLinearProgress = true,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
            entries.isEmpty() -> item {
                Text(
                    "No checklist entries for ${selectedMarque.id}.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            else -> itemsIndexed(
                entries,
                key = { index, op -> "programming-entry-$index-${op.id}" },
            ) { _, op ->
                ProgrammingTier4Row(
                    op = op,
                    marque = selectedMarque,
                )
            }
        }
    }
}

@Composable
private fun ProgrammingTier4Row(
    op: FlashOp,
    marque: PlanbMarque,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var flashResult by remember(op.id, marque) { mutableStateOf<FlashRequestResult?>(null) }
    var flashBusy by remember(op.id) { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        ),
    ) {
        Column(
            Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(op.title.ifBlank { op.id }, style = MaterialTheme.typography.titleSmall)
            if (op.description.isNotBlank()) {
                Text(
                    op.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                "Partner handoff: ${if (op.partnerHandoff) "yes" else "no"} · mode=${op.applyMode}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (op.isSkreemEntry()) {
                Text(
                    "SKIM/SKREEM · ${SkreemModule.CAPABILITY_ID} · PIN required · no in-app apply",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Text(
                "Tier 4 — partner/manual only",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.error,
            )
            TextButton(
                enabled = !flashBusy,
                onClick = {
                    flashBusy = true
                    scope.launch {
                        flashResult = withContext(Dispatchers.IO) {
                            ProgrammingSession(context.applicationContext, marque).requestFlash(op)
                        }
                        flashBusy = false
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.programming_flash_request_button))
            }
            flashResult?.let { result ->
                Text(
                    text = flashRequestStatusLabel(result),
                    style = MaterialTheme.typography.bodySmall,
                    color = flashRequestStatusColor(result),
                )
                Text(
                    text = result.message,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun flashRequestStatusLabel(result: FlashRequestResult): String = when (result) {
    is FlashRequestResult.Blocked -> stringResource(R.string.programming_flash_status_blocked)
    is FlashRequestResult.PartnerRequired -> stringResource(R.string.programming_flash_status_partner_required)
    is FlashRequestResult.QueuedForLab -> stringResource(R.string.programming_flash_status_queued_for_lab)
}

@Composable
private fun flashRequestStatusColor(result: FlashRequestResult) = when (result) {
    is FlashRequestResult.Blocked -> MaterialTheme.colorScheme.error
    is FlashRequestResult.PartnerRequired -> MaterialTheme.colorScheme.tertiary
    is FlashRequestResult.QueuedForLab -> MaterialTheme.colorScheme.primary
}
