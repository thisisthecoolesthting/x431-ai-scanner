package com.caseforge.scanner.ui.tuning

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.caseforge.scanner.R
import com.caseforge.scanner.planb.PlanbMarque
import com.caseforge.scanner.planb.body.BodyDtc
import com.caseforge.scanner.planb.gateway.EcuEntry
import com.caseforge.scanner.planb.gateway.GatewayMap
import com.caseforge.scanner.planb.gateway.GatewaySession
import com.caseforge.scanner.planb.gateway.StellantisGatewayNotes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ModuleScanScreen(
    session: TuningSessionContext,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val primaryVin = session.vehicleVin?.takeIf { it.isNotBlank() }
        ?: session.lastRecordedVin?.takeIf { it.isNotBlank() }
    val vinSuggested = remember(primaryVin) { PlanbMarque.fromVin(primaryVin) }
    var selectedMarque by remember(primaryVin) {
        mutableStateOf(vinSuggested?.takeIf { it in PlanbMarque.TRIAL_MARQUES } ?: PlanbMarque.JEEP)
    }
    val ecuList = remember(selectedMarque) { GatewayMap.forMarque(selectedMarque.id) }
    val dtcByEcu = remember { mutableStateMapOf<String, List<BodyDtc>>() }
    val errorsByEcu = remember { mutableStateMapOf<String, String>() }
    var scanning by remember { mutableStateOf(false) }

    fun scanLive() {
        val comm = session.vciCommunicator
        if (!session.vciConnected || comm == null) return
        scanning = true
        dtcByEcu.clear()
        errorsByEcu.clear()
        scope.launch {
            val gateway = GatewaySession(
                defaultEntries = ecuList,
                marque = selectedMarque.id,
                gatewayReplayEnabled = session.gatewayReplayEnabled,
                vciCommunicator = comm,
            )
            for (entry in ecuList) {
                withContext(Dispatchers.IO) {
                    gateway.connect(entry.id)
                    gateway.readDtcsSuspend().fold(
                        onSuccess = { dtcByEcu[entry.id] = it },
                        onFailure = { errorsByEcu[entry.id] = it.message ?: "Read failed" },
                    )
                }
            }
            scanning = false
        }
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
                stringResource(R.string.tuning_module_scan_gateway_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                stringResource(R.string.tuning_module_scan_sgw_key),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        item {
            Button(
                onClick = { scanLive() },
                enabled = session.vciConnected && session.vciCommunicator != null && !scanning,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (scanning) {
                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.padding(end = 8.dp))
                }
                Text(
                    if (session.vciCommunicator == null) {
                        stringResource(R.string.tuning_module_scan_oem_required)
                    } else {
                        stringResource(R.string.tuning_module_scan_read_dtcs)
                    },
                )
            }
        }
        if (!session.vciConnected) {
            item {
                Text(
                    stringResource(R.string.tuning_module_scan_disconnected),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        items(ecuList, key = { it.id }) { entry ->
            ModuleEcuCard(
                entry = entry,
                dtcs = dtcByEcu[entry.id],
                error = errorsByEcu[entry.id],
            )
        }
    }
}

@Composable
private fun ModuleEcuCard(
    entry: EcuEntry,
    dtcs: List<BodyDtc>?,
    error: String?,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(entry.name, style = MaterialTheme.typography.titleSmall)
            Text(
                "CAN 0x${entry.reqId.toString(16)} → 0x${entry.respId.toString(16)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            when {
                error != null -> Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                dtcs == null -> Text(
                    stringResource(R.string.tuning_module_scan_not_queried),
                    style = MaterialTheme.typography.bodySmall,
                )
                dtcs.isEmpty() -> Text(
                    stringResource(R.string.tuning_module_scan_no_dtcs),
                    style = MaterialTheme.typography.bodySmall,
                )
                else -> dtcs.forEach { d ->
                    Text("${d.code} — ${d.description}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
