package com.caseforge.scanner.ui.tuning

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.caseforge.scanner.R
import com.caseforge.scanner.vci.VciCommunicator
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@Composable
fun LivePidDashboardScreen(
    session: TuningSessionContext,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val values = remember { mutableStateMapOf<Int, Pair<Double, String>>() }
    var pollError by remember { mutableStateOf<String?>(null) }
    var polling by remember { mutableStateOf(false) }

    DisposableEffect(session.vciConnected, session.diagnosticPort) {
        val port = session.diagnosticPort
        var job: Job? = null
        if (session.vciConnected && port != null) {
            polling = true
            pollError = null
            val hexPids = VciCommunicator.DEFAULT_LIVE_PIDS.map { livePidHex(it) }
            job = scope.launch {
                runCatching {
                    port.liveData(hexPids).collectLatest { sample ->
                        val pidInt = sample.pid.removePrefix("0x").toIntOrNull(16)
                            ?: return@collectLatest
                        values[pidInt] = sample.value to sample.unit
                    }
                }.onFailure { pollError = it.message ?: it.javaClass.simpleName }
                polling = false
            }
        } else {
            values.clear()
            polling = false
        }
        onDispose {
            job?.cancel()
            polling = false
        }
    }

    Column(
        modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            stringResource(R.string.tuning_live_pids_title),
            style = MaterialTheme.typography.titleMedium,
        )
        if (!session.vciConnected) {
            Card(Modifier.fillMaxWidth()) {
                Text(
                    stringResource(R.string.tuning_live_pids_disconnected),
                    Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.error,
                )
            }
            return@Column
        }
        if (polling && values.isEmpty()) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(strokeWidth = 2.dp)
                Text(stringResource(R.string.tuning_live_pids_polling))
            }
        }
        pollError?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(VciCommunicator.DEFAULT_LIVE_PIDS, key = { it }) { pid ->
                val sample = values[pid]
                Card(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column {
                            Text(livePidLabel(pid), style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Mode 01 · 0x${livePidHex(pid)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            if (sample != null) {
                                "${"%.1f".format(sample.first)} ${sample.second}".trim()
                            } else {
                                "—"
                            },
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
        }
        Text(
            stringResource(R.string.tuning_read_only_banner),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
