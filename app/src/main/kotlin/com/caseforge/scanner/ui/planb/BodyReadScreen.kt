package com.caseforge.scanner.ui.planb

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import android.util.Log
import com.caseforge.scanner.planb.PlanbMarque
import com.caseforge.scanner.planb.body.BodyReadSession
import com.caseforge.scanner.planb.gateway.GatewaySession
import com.caseforge.scanner.planb.gateway.dodgeWedgeDefaults
import com.caseforge.scanner.planb.gateway.fordGatewayDefaultsForVin
import com.caseforge.scanner.planb.gateway.jeepWedgeDefaults
import com.caseforge.scanner.planb.gateway.replay.GoldenReplaySource
import com.caseforge.scanner.ui.components.LoadingState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val GATEWAY_NEUTRAL_NOTE =
    "A vehicle gateway may restrict diagnostic requests toward some ECUs. Silence, timeouts, or negative " +
        "responses can reflect aftermarket-path or policy limits rather than a wiring fault. Some requests " +
        "may require correct session or authentication."
private const val TAG = "BodyReadScreen"

@Composable
fun BodyReadScreen(
    vehicleVin: String?,
    lastRecordedVin: String?,
    gatewayReplayEnabled: Boolean = false,
    gatewaySessionReuseEnabled: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val primaryVin = vehicleVin?.takeIf { it.isNotBlank() }
        ?: lastRecordedVin?.takeIf { it.isNotBlank() }
    val vinSuggested = remember(primaryVin) { PlanbMarque.fromVin(primaryVin) }

    var selectedMarque by remember(primaryVin) { mutableStateOf(vinSuggested ?: PlanbMarque.JEEP) }
    var preloadLoading by remember { mutableStateOf(gatewayReplayEnabled) }
    var replayAssetMissing by remember { mutableStateOf(false) }

    LaunchedEffect(gatewayReplayEnabled, selectedMarque) {
        preloadLoading = gatewayReplayEnabled
        replayAssetMissing = gatewayReplayEnabled &&
            !GoldenReplaySource.hasBundledAsset(context.applicationContext, selectedMarque)
        if (!gatewayReplayEnabled) return@LaunchedEffect
        if (replayAssetMissing) return@LaunchedEffect
        runCatching {
            withContext(Dispatchers.IO) {
                GoldenReplaySource.preload(context.applicationContext, selectedMarque)
            }
        }.onFailure {
            Log.w(TAG, "Golden preload failed; continuing without preload", it)
        }.also {
            preloadLoading = false
        }
    }

    fun defaultsForMarque(m: PlanbMarque) = when (m) {
        PlanbMarque.JEEP -> jeepWedgeDefaults()
        PlanbMarque.FORD -> fordGatewayDefaultsForVin(context, primaryVin)
        PlanbMarque.DODGE -> dodgeWedgeDefaults()
        PlanbMarque.RAM -> dodgeWedgeDefaults()
        PlanbMarque.CHRYSLER -> dodgeWedgeDefaults()
        PlanbMarque.CHEVROLET -> jeepWedgeDefaults()
        PlanbMarque.TOYOTA -> jeepWedgeDefaults()
        PlanbMarque.HONDA -> jeepWedgeDefaults()
        PlanbMarque.NISSAN -> jeepWedgeDefaults()
        PlanbMarque.HYUNDAI -> jeepWedgeDefaults()
    }

    val session = remember(selectedMarque, gatewayReplayEnabled, gatewaySessionReuseEnabled, primaryVin, replayAssetMissing) {
        val golden =
            if (gatewayReplayEnabled && !replayAssetMissing) {
                GoldenReplaySource.loadForMarqueIfPresent(context, selectedMarque)
            } else {
                null
            }
        BodyReadSession(
            planbBodyRead = true,
            gateway = GatewaySession(
                defaultEntries = defaultsForMarque(selectedMarque),
                marque = selectedMarque.id,
                gatewayReplayEnabled = gatewayReplayEnabled,
                goldenReplaySource = golden,
                reuseConnectionScaffoldEnabled = gatewaySessionReuseEnabled,
            ),
        )
    }
    val dtcRows = remember(selectedMarque, gatewayReplayEnabled, primaryVin) {
        session.readDtcs(ecuId = "pcm").getOrElse { emptyList() }
    }
    val liveRows = remember(selectedMarque) {
        session.readLiveData(ecuId = "pcm", dids = listOf("STUB_DID")).getOrElse { emptyList() }
    }

    Column(
        modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            primaryVin?.let { "Last / live VIN: $it" } ?: "No VIN yet — pick a marque below",
            style = MaterialTheme.typography.titleMedium,
        )
        PlanbMarqueChipRow(
            vinSuggestedMarque = vinSuggested,
            selectedMarque = selectedMarque,
            onMarqueSelected = { selectedMarque = it },
        )

        if (gatewayReplayEnabled && replayAssetMissing) {
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                ),
            ) {
                Text(
                    "Gateway replay is on but no bundled golden log exists for ${selectedMarque.id} " +
                        "(${GoldenReplaySource.defaultAssetPath(selectedMarque)}). Turn replay off or add the asset.",
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    GATEWAY_NEUTRAL_NOTE,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Body read (stub)",
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    "Tier 1 — [BodyReadSession] DTC lane via GatewaySession (${selectedMarque.id}); " +
                        "Ford Windstar wedge card swaps in the Windstar PCM scaffold label when VIN resolves to that card. " +
                        "Live data still stub reader.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    "DTC count: ${dtcRows.size} (golden logs will populate)",
                    style = MaterialTheme.typography.bodyMedium,
                )
                liveRows.take(4).forEach { row ->
                    Text(
                        "${row.did}: ${row.value}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (preloadLoading) {
                    LoadingState(
                        message = "Preparing gateway replay",
                        animatedDots = true,
                        showLinearProgress = true,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}
