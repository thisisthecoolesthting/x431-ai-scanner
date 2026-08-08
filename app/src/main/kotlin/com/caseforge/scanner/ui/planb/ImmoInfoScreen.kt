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
import com.caseforge.scanner.agent.ObdElmEngine
import com.caseforge.scanner.planb.PlanbMarque
import com.caseforge.scanner.planb.displayName
import com.caseforge.scanner.planb.immo.ImmoDataSource
import com.caseforge.scanner.planb.immo.ImmoInfoService
import com.caseforge.scanner.planb.immo.ImmoReadState
import com.caseforge.scanner.planb.immo.SkreemModule
import com.caseforge.scanner.vci.VciCommunicator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun ImmoInfoScreen(
    vehicleVin: String?,
    lastRecordedVin: String?,
    vciCommunicator: VciCommunicator? = null,
    elmEngine: ObdElmEngine? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val service = remember {
        ImmoInfoService(context.applicationContext)
    }

    val primaryVin = vehicleVin?.takeIf { it.isNotBlank() }
        ?: lastRecordedVin?.takeIf { it.isNotBlank() }
    val vinSuggested = remember(primaryVin) { PlanbMarque.fromVin(primaryVin) }

    var selectedMarque by remember(primaryVin) { mutableStateOf(vinSuggested ?: PlanbMarque.JEEP) }
    var state by remember { mutableStateOf<ImmoReadState?>(null) }

    LaunchedEffect(selectedMarque, vciCommunicator, primaryVin, elmEngine) {
        state = withContext(Dispatchers.IO) {
            service.readStateWithLive(selectedMarque, vciCommunicator, primaryVin, elmEngine)
        }
    }

    Column(
        modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PlanbMarqueChipRow(
            vinSuggestedMarque = vinSuggested,
            selectedMarque = selectedMarque,
            onMarqueSelected = { selectedMarque = it },
        )

        val s = state
        if (s == null) {
            Text("Loading…", style = MaterialTheme.typography.bodyMedium)
            return@Column
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(s.riskBanner, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.error)
                Text(s.disclaimer, style = MaterialTheme.typography.bodySmall)
            }
        }

        Card(
            Modifier.fillMaxWidth(),
            colors = when (s.dataSource) {
                ImmoDataSource.LIVE -> CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                )
                ImmoDataSource.LIVE_FALLBACK_STATIC -> CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                )
                ImmoDataSource.STATIC -> CardDefaults.cardColors()
            },
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Immo info · ${s.marque.displayName()} · ${s.sourceBadge}",
                    style = MaterialTheme.typography.titleSmall,
                )
                s.liveStatusLine?.let { live ->
                    Text(
                        live,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                s.liveFallbackReason?.let { reason ->
                    Text(
                        reason,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(s.stateSummary, style = MaterialTheme.typography.bodyMedium)
            }
        }

        val skreem = s.banner?.skreemModule
        if (SkreemModule.isStellantisMarque(s.marque) && skreem != null) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        s.skreemBannerTitle ?: "${skreem.moduleName} · Tier 3 (info only)",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    if (skreem.role.isNotBlank()) {
                        Text(skreem.role, style = MaterialTheme.typography.bodyMedium)
                    }
                    if (skreem.tier3Scope.isNotBlank()) {
                        Text(
                            skreem.tier3Scope,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        "Capability: ${SkreemModule.CAPABILITY_ID} — no automated key learn",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        } else if (s.marque == PlanbMarque.FORD || s.patsBannerTitle != null) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        s.patsBannerTitle ?: "PATS · reference",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        SkreemModule.fordNotApplicableNote(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
