package com.caseforge.scanner.ui.tuning

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.caseforge.scanner.R

@Composable
fun SessionToolsScreen(
    onHarvestExport: () -> Unit,
    onHistory: () -> Unit,
    onNewSession: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            stringResource(R.string.tuning_session_tools_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            stringResource(R.string.tuning_session_tools_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = onHarvestExport, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.harvest_and_upload))
        }
        OutlinedButton(onClick = onHistory, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.tuning_session_history))
        }
        OutlinedButton(onClick = onNewSession, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.tuning_session_new))
        }
    }
}
