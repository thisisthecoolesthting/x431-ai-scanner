package com.caseforge.scanner.ui.tuning

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.caseforge.scanner.R
import com.caseforge.scanner.planb.PlanbMarque
import com.caseforge.scanner.planb.displayName

private data class TuningHubTile(
    val titleRes: Int,
    val subtitleRes: Int,
    val route: String,
    val requiresTier4: Boolean = false,
)

@Composable
fun TuningHubScreen(
    session: TuningSessionContext,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val primaryVin = session.vehicleVin?.takeIf { it.isNotBlank() }
        ?: session.lastRecordedVin?.takeIf { it.isNotBlank() }
    val vinSuggested = remember(primaryVin) { PlanbMarque.fromVin(primaryVin) }
    var selectedMarque by remember(primaryVin) {
        mutableStateOf(
            vinSuggested?.takeIf { it in PlanbMarque.TRIAL_MARQUES } ?: PlanbMarque.FORD,
        )
    }

    val tiles = listOf(
        TuningHubTile(R.string.tuning_tile_live_pids, R.string.tuning_tile_live_pids_sub, TuningRoutes.LIVE_PIDS),
        TuningHubTile(R.string.tuning_tile_immo, R.string.tuning_tile_immo_sub, TuningRoutes.IMMO),
        TuningHubTile(
            R.string.tuning_tile_programming,
            R.string.tuning_tile_programming_sub,
            TuningRoutes.PROGRAMMING,
            requiresTier4 = true,
        ),
        TuningHubTile(R.string.tuning_tile_service_reset, R.string.tuning_tile_service_reset_sub, TuningRoutes.SERVICE_RESET),
        TuningHubTile(R.string.tuning_tile_adaptation, R.string.tuning_tile_adaptation_sub, TuningRoutes.ADAPTATION),
        TuningHubTile(R.string.tuning_tile_emissions, R.string.tuning_tile_emissions_sub, TuningRoutes.EMISSIONS),
        TuningHubTile(R.string.tuning_tile_module_scan, R.string.tuning_tile_module_scan_sub, TuningRoutes.MODULE_SCAN),
        TuningHubTile(R.string.tuning_tile_disclaimer, R.string.tuning_tile_disclaimer_sub, TuningRoutes.DISCLAIMER),
        TuningHubTile(R.string.tuning_tile_diesel, R.string.tuning_tile_diesel_sub, TuningRoutes.DIESEL),
        TuningHubTile(R.string.tuning_tile_config_read, R.string.tuning_tile_config_read_sub, TuningRoutes.CONFIG_READ),
        TuningHubTile(R.string.tuning_tile_custom_pid, R.string.tuning_tile_custom_pid_sub, TuningRoutes.CUSTOM_PID),
        TuningHubTile(R.string.tuning_tile_session_tools, R.string.tuning_tile_session_tools_sub, TuningRoutes.SESSION_TOOLS),
    )

    Column(
        modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            stringResource(R.string.tuning_hub_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            stringResource(R.string.tuning_hub_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!session.vciConnected) {
            Text(
                stringResource(R.string.tuning_hub_disconnected_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        TuningMarqueChipRow(
            vinSuggestedMarque = vinSuggested,
            selectedMarque = selectedMarque,
            onMarqueSelected = { selectedMarque = it },
        )
        Text(
            stringResource(R.string.tuning_hub_marque_scope, selectedMarque.displayName()),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f),
        ) {
            items(tiles, key = { it.route }) { tile ->
                val locked = tile.requiresTier4 && !session.tier4ProgrammingReady
                Card(
                    Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !locked) { onNavigate(tile.route) },
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            stringResource(tile.titleRes),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            stringResource(
                                if (locked) R.string.tuning_tile_tier4_locked else tile.subtitleRes,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
