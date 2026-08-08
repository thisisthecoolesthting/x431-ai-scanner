package com.caseforge.scanner.ui.tuning

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.caseforge.scanner.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

private const val PID_MONITOR_STATUS = 0x01

@Composable
fun EmissionsToolsScreen(
    session: TuningSessionContext,
    modifier: Modifier = Modifier,
) {
    var monitorLine by remember { mutableStateOf<String?>(null) }
    var liveError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(session.vciConnected, session.vciCommunicator, session.diagnosticPort) {
        monitorLine = null
        liveError = null
        if (!session.vciConnected) return@LaunchedEffect

        val sample = withContext(Dispatchers.IO) {
            withTimeoutOrNull(8.seconds) {
                val comm = session.vciCommunicator
                if (comm != null) {
                    runCatching {
                        comm.livePid(listOf(PID_MONITOR_STATUS)).firstOrNull()
                    }.getOrNull()
                } else {
                    val port = session.diagnosticPort ?: return@withTimeoutOrNull null
                    runCatching {
                        port.liveData(listOf(livePidHex(PID_MONITOR_STATUS))).firstOrNull()
                    }.getOrNull()
                }
            }
        }

        if (sample != null) {
            val valDbl = (sample as? com.caseforge.scanner.engine.LiveSample)?.value 
                ?: (sample as? com.caseforge.scanner.vci.LiveSample)?.value
            val unitStr = (sample as? com.caseforge.scanner.engine.LiveSample)?.unit 
                ?: (sample as? com.caseforge.scanner.vci.LiveSample)?.unit ?: ""
            val valStr = if (valDbl != null) "%.2f".format(valDbl) else ""
            monitorLine = "PID 01 · $valStr $unitStr".trim()
        } else if (session.vciConnected) {
            liveError = "No Mode 01 PID 01 response yet — readiness bits may require OEM VCI or ignition cycle."
        }
    }

    Column(
        modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            stringResource(R.string.tuning_emissions_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.tuning_emissions_readiness_body),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    stringResource(R.string.tuning_emissions_monitors_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (!session.vciConnected) {
            Text(
                stringResource(R.string.tuning_emissions_connect_hint),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        } else {
            monitorLine?.let { line ->
                Card(Modifier.fillMaxWidth()) {
                    Text(
                        stringResource(R.string.tuning_emissions_live_placeholder, line),
                        Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            liveError?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }
        Text(
            stringResource(R.string.tuning_read_only_banner),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
