package com.caseforge.scanner.ui.tuning

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.caseforge.scanner.R

@Composable
fun DieselToolsScreen(
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            stringResource(R.string.tuning_diesel_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            stringResource(R.string.tuning_diesel_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        DieselReferenceCard(
            title = stringResource(R.string.tuning_diesel_ford_dpf_title),
            body = stringResource(R.string.tuning_diesel_ford_dpf_body),
        )
        DieselReferenceCard(
            title = stringResource(R.string.tuning_diesel_ford_def_title),
            body = stringResource(R.string.tuning_diesel_ford_def_body),
        )
        DieselReferenceCard(
            title = stringResource(R.string.tuning_diesel_ram_dpf_title),
            body = stringResource(R.string.tuning_diesel_ram_dpf_body),
        )
        DieselReferenceCard(
            title = stringResource(R.string.tuning_diesel_ram_def_title),
            body = stringResource(R.string.tuning_diesel_ram_def_body),
        )
        Text(
            stringResource(R.string.tuning_requires_oem_launch),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun DieselReferenceCard(title: String, body: String) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(body, style = MaterialTheme.typography.bodySmall)
        }
    }
}
