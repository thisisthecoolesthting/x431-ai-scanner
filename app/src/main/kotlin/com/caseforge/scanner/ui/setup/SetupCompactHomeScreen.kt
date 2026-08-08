@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.caseforge.scanner.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.caseforge.scanner.R
import com.caseforge.scanner.ui.session.NewSessionButton
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class ConnectWizardPhase {
    DETECT,
    PERMISSION,
    PROBE,
    TEST_READ,
    CONNECTED,
    ERROR,
}

@Composable
fun SetupCompactHomeScreen(
    buildInfo: String,
    passedSteps: Int,
    totalSteps: Int,
    tier4TrialActive: Boolean,
    tier4ProgrammingReady: Boolean,
    vciConnected: Boolean,
    vin: String?,
    linkDetail: String?,
    connectError: String?,
    connectBusy: Boolean,
    usbDeviceCount: Int,
    lastWorkingConnectAtMs: Long,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onNewSession: () -> Unit,
    onSettings: () -> Unit,
    onOpenConnectionDiagnostics: () -> Unit,
    onRerunSetup: () -> Unit,
    onOpenTier4Programming: () -> Unit,
    onOpenTier4Gate: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Together Car Works") },
            actions = {
                IconButton(onClick = onSettings) {
                    Icon(Icons.Default.Settings, contentDescription = "Settings")
                }
            },
        )
        Column(
            Modifier
                .weight(1f)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            HomeConnectionWizardCard(
                vciConnected = vciConnected,
                vin = vin,
                linkDetail = linkDetail,
                connectError = connectError,
                connectBusy = connectBusy,
                usbDeviceCount = usbDeviceCount,
                lastWorkingConnectAtMs = lastWorkingConnectAtMs,
                onConnect = onConnect,
                onDisconnect = onDisconnect,
                onOpenConnectionDiagnostics = onOpenConnectionDiagnostics,
            )

            Card(Modifier.fillMaxWidth()) {
                Column(
                    Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.Start,
                ) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        "All systems live",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "Setup checklist $passedSteps/$totalSteps resolved. Use shortcuts below for daily shop work.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        buildInfo,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            NewSessionButton(onClick = onNewSession, modifier = Modifier.fillMaxWidth())
            if (tier4TrialActive) {
                OutlinedButton(
                    onClick = {
                        if (tier4ProgrammingReady) onOpenTier4Programming() else onOpenTier4Gate()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.tier4_compact_home_programming))
                }
            }
            Button(onClick = onSettings, modifier = Modifier.fillMaxWidth()) {
                Text("Settings")
            }
            OutlinedButton(onClick = onRerunSetup, modifier = Modifier.fillMaxWidth()) {
                Text("Re-run setup checklist")
            }
        }
    }
}

@Composable
private fun HomeConnectionWizardCard(
    vciConnected: Boolean,
    vin: String?,
    linkDetail: String?,
    connectError: String?,
    connectBusy: Boolean,
    usbDeviceCount: Int,
    lastWorkingConnectAtMs: Long,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onOpenConnectionDiagnostics: () -> Unit,
) {
    val phase = when {
        vciConnected -> ConnectWizardPhase.CONNECTED
        connectBusy -> when {
            usbDeviceCount == 0 -> ConnectWizardPhase.DETECT
            connectError?.contains("permission", ignoreCase = true) == true -> ConnectWizardPhase.PERMISSION
            else -> ConnectWizardPhase.PROBE
        }
        connectError != null -> ConnectWizardPhase.ERROR
        usbDeviceCount == 0 -> ConnectWizardPhase.DETECT
        else -> ConnectWizardPhase.DETECT
    }

    Card(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                stringResource(R.string.home_connect_wizard_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                stringResource(R.string.home_connect_wizard_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            WizardStepRow(
                label = stringResource(R.string.home_connect_step_detect),
                detail = if (usbDeviceCount > 0) {
                    stringResource(R.string.home_connect_step_detect_ok, usbDeviceCount)
                } else {
                    stringResource(R.string.home_connect_step_detect_missing)
                },
                done = usbDeviceCount > 0 || vciConnected,
                active = phase == ConnectWizardPhase.DETECT && connectBusy,
            )
            WizardStepRow(
                label = stringResource(R.string.home_connect_step_permission),
                detail = if (connectError?.contains("permission", ignoreCase = true) == true) {
                    connectError
                } else if (usbDeviceCount > 0) {
                    stringResource(R.string.home_connect_step_permission_ready)
                } else {
                    stringResource(R.string.home_connect_step_permission_wait)
                },
                done = vciConnected || (usbDeviceCount > 0 && connectError?.contains("permission", ignoreCase = true) != true),
                active = phase == ConnectWizardPhase.PERMISSION && connectBusy,
            )
            WizardStepRow(
                label = stringResource(R.string.home_connect_step_probe),
                detail = stringResource(R.string.home_connect_step_probe_detail),
                done = vciConnected,
                active = phase == ConnectWizardPhase.PROBE && connectBusy,
            )
            WizardStepRow(
                label = stringResource(R.string.home_connect_step_test_read),
                detail = when {
                    vciConnected && !vin.isNullOrBlank() -> stringResource(R.string.home_connect_step_test_vin, vin)
                    vciConnected -> stringResource(R.string.home_connect_step_test_ok)
                    connectBusy -> stringResource(R.string.home_connect_step_test_running)
                    else -> stringResource(R.string.home_connect_step_test_pending)
                },
                done = vciConnected,
                active = phase == ConnectWizardPhase.TEST_READ && connectBusy,
            )

            linkDetail?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            connectError?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            if (lastWorkingConnectAtMs > 0L) {
                val whenLabel = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
                    .format(Date(lastWorkingConnectAtMs))
                Text(
                    stringResource(R.string.home_connect_last_working, whenLabel),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (vciConnected) {
                OutlinedButton(onClick = onDisconnect, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.home_connect_disconnect))
                }
            } else {
                Button(
                    onClick = onConnect,
                    enabled = !connectBusy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (connectBusy) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.padding(2.dp))
                            Text(stringResource(R.string.home_connect_connecting))
                        }
                    } else {
                        Text(stringResource(R.string.home_connect_connect))
                    }
                }
            }

            TextButton(onClick = onOpenConnectionDiagnostics, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.home_connect_troubleshoot))
            }
        }
    }
}

@Composable
private fun WizardStepRow(
    label: String,
    detail: String,
    done: Boolean,
    active: Boolean,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        when {
            active -> CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.padding(top = 2.dp))
            done -> Icon(
                Icons.Default.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            else -> Text("○", style = MaterialTheme.typography.bodyMedium)
        }
        Column {
            Text(label, fontWeight = FontWeight.Medium)
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
