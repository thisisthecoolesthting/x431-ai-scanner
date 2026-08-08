@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.caseforge.scanner.ui.obd

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.caseforge.scanner.R
import com.caseforge.scanner.agent.discovery.DiscoveryReport
import com.caseforge.scanner.agent.discovery.TabletHardwareDiscoveryAgent
import com.caseforge.scanner.agent.discovery.VehicleProfileLoader
import com.caseforge.scanner.data.SettingsRepo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Settings / OBD screen panel: scan tablet USB+BT for OBD adapters and show Windstar-oriented guidance.
 */
@Composable
fun ConnectionReadinessPanel(
    selectedProfileId: String = VehicleProfileLoader.DEFAULT_WINDSTAR_ID,
    vinHint: String? = null,
    showWindstarBanner: Boolean = selectedProfileId == VehicleProfileLoader.DEFAULT_WINDSTAR_ID,
    settings: SettingsRepo? = null,
    transportStatus: String? = null,
    modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var report by remember { mutableStateOf<DiscoveryReport?>(null) }
    var reportText by remember { mutableStateOf("") }
    val resolvedProfileId = remember(vinHint, selectedProfileId) {
        vinHint?.let { VehicleProfileLoader.profileIdForVin(ctx, it) } ?: selectedProfileId
    }
    var profileId by remember(resolvedProfileId) { mutableStateOf(resolvedProfileId) }

    val btPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        if (results.values.all { it }) {
            scope.launch { performScan(ctx, profileId) { b, r, t -> busy = b; report = r; reportText = t } }
        }
    }

    fun requestScan() {
        val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
        } else {
            emptyArray()
        }
        val needed = perms.filter {
            ContextCompat.checkSelfPermission(ctx, it) != PackageManager.PERMISSION_GRANTED
        }
        if (needed.isEmpty()) {
            scope.launch { performScan(ctx, profileId) { b, r, t -> busy = b; report = r; reportText = t } }
        } else {
            btPermLauncher.launch(needed.toTypedArray())
        }
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (showWindstarBanner || profileId == VehicleProfileLoader.DEFAULT_WINDSTAR_ID) {
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                ),
            ) {
                Text(
                    stringResource(R.string.windstar_connection_banner),
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                )
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Connection readiness",
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    "Find USB/BT adapters, permissions, and link settings for the selected vehicle profile.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!transportStatus.isNullOrBlank()) {
                    Text(
                        "Current transport: $transportStatus",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                FilterChip(
                    selected = profileId == VehicleProfileLoader.DEFAULT_WINDSTAR_ID,
                    onClick = { profileId = VehicleProfileLoader.DEFAULT_WINDSTAR_ID },
                    label = { Text("2000 Ford Windstar") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = { requestScan() },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) {
                    Text(if (busy) "Scanning…" else "Find drivers & adapters")
                }
                report?.let { r ->
                    Text(
                        r.recommendedAction,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    if (r.missingItems.isNotEmpty()) {
                        r.missingItems.forEach { item ->
                            Text("• $item", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Text(
                        DiscoveryReport.ANDROID_USB_LIMIT_NOTE,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (reportText.isNotBlank()) {
                    Text(
                        reportText,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

private suspend fun performScan(
    ctx: android.content.Context,
    profileId: String,
    onResult: (busy: Boolean, report: DiscoveryReport?, text: String) -> Unit,
) {
    onResult(true, null, "")
    val agent = TabletHardwareDiscoveryAgent(ctx)
    val result = withContext(Dispatchers.Default) { agent.scan(profileId) }
    val text = agent.formatForAgent(result)
    onResult(false, result, text)
}
