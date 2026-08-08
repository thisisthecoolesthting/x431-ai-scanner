package com.caseforge.scanner.ui.tuning

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.caseforge.scanner.R
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@Composable
fun CustomPidScreen(
    session: TuningSessionContext,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var hexPid by remember { mutableStateOf("0C") }
    var valueLine by remember { mutableStateOf<String?>(null) }
    var polling by remember { mutableStateOf(false) }
    var pollJob by remember { mutableStateOf<Job?>(null) }

    fun stopPoll() {
        pollJob?.cancel()
        pollJob = null
        polling = false
    }

    fun startPoll() {
        val port = session.diagnosticPort
        if (!session.vciConnected || port == null) return
        val pidHex = hexPid.trim().removePrefix("0x").removePrefix("0X")
        if (pidHex.length != 2 || pidHex.toIntOrNull(16) == null) {
            valueLine = "Enter a two-digit hex PID (e.g. 0C)."
            return
        }
        stopPoll()
        polling = true
        pollJob = scope.launch {
            port.liveData(listOf(pidHex)).collectLatest { sample ->
                valueLine = "${sample.value} ${sample.unit}".trim()
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose { stopPoll() }
    }

    Column(
        modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            stringResource(R.string.tuning_custom_pid_title),
            style = MaterialTheme.typography.titleMedium,
        )
        OutlinedTextField(
            value = hexPid,
            onValueChange = { hexPid = it.uppercase().take(4) },
            label = { Text(stringResource(R.string.tuning_custom_pid_label)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        if (!session.vciConnected) {
            Text(
                stringResource(R.string.tuning_custom_pid_disconnected),
                color = MaterialTheme.colorScheme.error,
            )
        } else {
            RowActions(
                polling = polling,
                onStart = { startPoll() },
                onStop = { stopPoll() },
            )
        }
        valueLine?.let { line ->
            Card(Modifier.fillMaxWidth()) {
                Text(
                    stringResource(R.string.tuning_custom_pid_value, line),
                    Modifier.padding(12.dp),
                )
            }
        }
        Text(
            stringResource(R.string.tuning_read_only_banner),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RowActions(
    polling: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = { if (polling) onStop() else onStart() }, modifier = Modifier.fillMaxWidth()) {
            Text(
                if (polling) stringResource(R.string.tuning_custom_pid_stop)
                else stringResource(R.string.tuning_custom_pid_poll),
            )
        }
    }
}
