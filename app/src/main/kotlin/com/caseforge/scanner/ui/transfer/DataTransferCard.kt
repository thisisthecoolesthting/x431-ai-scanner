@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.caseforge.scanner.ui.transfer

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.caseforge.scanner.data.SettingsRepo

/** Commercial data-export card (free share default; optional self-hosted / LAN). */
@Composable
fun DataTransferCard(
    settings: SettingsRepo,
    modifier: Modifier = Modifier,
    onOpenTransferLog: (() -> Unit)? = null,
    onOpenSettings: (() -> Unit)? = null,
    onSent: (() -> Unit)? = null,
) {
    OneTapSendCard(
        settings = settings,
        modifier = modifier,
        onOpenTransferLog = onOpenTransferLog,
        onOpenSettings = onOpenSettings,
        onSent = onSent,
    )
}
