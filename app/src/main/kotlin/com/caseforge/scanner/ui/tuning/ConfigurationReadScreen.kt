package com.caseforge.scanner.ui.tuning

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.caseforge.scanner.R
import com.caseforge.scanner.planb.PlanbMarque
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ConfigurationReadScreen(
    session: TuningSessionContext,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val primaryVin = session.vehicleVin?.takeIf { it.isNotBlank() }
        ?: session.lastRecordedVin?.takeIf { it.isNotBlank() }
    val marque = remember(primaryVin) { PlanbMarque.fromVin(primaryVin) }
    var probeLine by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    Column(
        modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            stringResource(R.string.tuning_config_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (marque == PlanbMarque.FORD) {
                        stringResource(R.string.tuning_config_as_built)
                    } else {
                        stringResource(R.string.tuning_config_proxi)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    stringResource(R.string.tuning_config_partner_lab),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Button(
            onClick = {
                val comm = session.vciCommunicator
                if (comm == null || !session.vciConnected) {
                    probeLine = null
                    return@Button
                }
                busy = true
                scope.launch {
                    probeLine = withContext(Dispatchers.IO) {
                        runCatching {
                            comm.readImmoStatus(
                                reqCanId = 0x7E0,
                                respCanId = 0x7E8,
                                dataIdentifier = 0xF190,
                            ).fold(
                                onSuccess = { r ->
                                    "Read-only UDS 0x22 DID F190 — ${r.tpPayload.size} byte TP payload (interpretation requires partner lab)."
                                },
                                onFailure = { e ->
                                    "DID read not available: ${e.message}"
                                },
                            )
                        }.getOrElse { "Probe error: ${it.message}" }
                    }
                    busy = false
                }
            },
            enabled = session.vciConnected && session.vciCommunicator != null && !busy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (busy) {
                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.padding(end = 8.dp))
            }
            Text(stringResource(R.string.tuning_config_try_read))
        }
        if (!session.vciConnected) {
            Text(
                stringResource(R.string.tuning_config_connect),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        } else if (session.vciCommunicator == null) {
            Text(
                stringResource(R.string.tuning_config_oem_only),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        probeLine?.let {
            Card(Modifier.fillMaxWidth()) {
                Text(it, Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
            }
        }
        Text(
            stringResource(R.string.tuning_read_only_banner),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
