package com.caseforge.scanner.ui.planb

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.caseforge.scanner.planb.PlanbMarque
import com.caseforge.scanner.planb.displayName

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlanbMarqueChipRow(
    vinSuggestedMarque: PlanbMarque?,
    selectedMarque: PlanbMarque,
    onMarqueSelected: (PlanbMarque) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("Marque", style = MaterialTheme.typography.titleSmall)
        vinSuggestedMarque?.let { m ->
            Text(
                "VIN WMI suggests: ${m.displayName()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            PlanbMarque.entries.forEach { m ->
                FilterChip(
                    selected = m == selectedMarque,
                    onClick = { onMarqueSelected(m) },
                    label = { Text(m.displayName()) },
                )
            }
        }
    }
}
