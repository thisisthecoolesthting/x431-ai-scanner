@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.caseforge.scanner.ui.transfer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.caseforge.scanner.R
import com.caseforge.scanner.transfer.CnlaunchPathResolver
import com.caseforge.scanner.transfer.CnlaunchQuickSend
import com.caseforge.scanner.transfer.CnlaunchStorageAccess
import com.caseforge.scanner.transfer.CnlaunchZipper
import com.caseforge.scanner.transfer.LanExportConfig
import kotlinx.coroutines.launch

@Composable
fun CnlaunchOneTapSendCard(
    modifier: Modifier = Modifier,
    onSent: (() -> Unit)? = null,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var inventory by remember { mutableStateOf(CnlaunchPathResolver.scan()) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var done by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        inventory = CnlaunchPathResolver.scan()
    }

    fun refresh() {
        inventory = CnlaunchPathResolver.scan()
    }

    fun sendNow() {
        error = null
        status = null
        done = false
        if (CnlaunchStorageAccess.needsAllFilesAccess()) {
            CnlaunchStorageAccess.openAllFilesAccessSettings(ctx)
            error = ctx.getString(R.string.export_need_files_access)
            return
        }
        refresh()
        if (!inventory.hasData) {
            error = CnlaunchZipper.EmptyCnlaunchException.emptyMessage(inventory)
            return
        }
        busy = true
        scope.launch {
            CnlaunchQuickSend.zipAndSend(ctx) { step -> status = step }
                .fold(
                    onSuccess = {
                        status = ctx.getString(R.string.export_send_done_short, LanExportConfig.RECEIVER_PC_HOST)
                        done = true
                        busy = false
                        onSent?.invoke()
                    },
                    onFailure = {
                        error = it.message ?: "Send failed"
                        busy = false
                    },
                )
        }
    }

    Card(modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                stringResource(R.string.export_one_tap_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (!done) {
                if (inventory.hasData) {
                    Text(
                        stringResource(
                            R.string.export_inventory_summary,
                            inventory.fileCount,
                            inventory.totalBytes / (1024 * 1024),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (busy) {
                    CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    status?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                } else {
                    Button(
                        onClick = { sendNow() },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !busy,
                    ) {
                        Text(
                            stringResource(
                                R.string.export_one_tap_button,
                                LanExportConfig.RECEIVER_PC_HOST,
                            ),
                        )
                    }
                    if (CnlaunchStorageAccess.needsAllFilesAccess()) {
                        OutlinedButton(
                            onClick = { CnlaunchStorageAccess.openAllFilesAccessSettings(ctx) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.export_grant_files))
                        }
                    }
                }
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            } else {
                Text(
                    status ?: stringResource(R.string.export_send_done),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}
