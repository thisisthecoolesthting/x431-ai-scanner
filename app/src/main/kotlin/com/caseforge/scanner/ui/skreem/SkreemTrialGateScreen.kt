package com.caseforge.scanner.ui.skreem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.caseforge.scanner.R
import com.caseforge.scanner.data.SettingsRepo
import com.caseforge.scanner.planb.PlanbMarque
import com.caseforge.scanner.planb.displayName
import com.caseforge.scanner.planb.immo.SkreemModule
import java.text.DateFormat
import java.util.Date

private val SKREEM_TRIAL_MARQUES: List<PlanbMarque> = listOf(
    PlanbMarque.JEEP,
    PlanbMarque.DODGE,
    PlanbMarque.RAM,
    PlanbMarque.CHRYSLER,
)

/**
 * Trial gate for Stellantis SKIM/SKREEM **informational** access (Tier 3 immo scope).
 * Separate from [com.caseforge.scanner.ui.tier4.Tier4TrialGateScreen] — no programming execution.
 */
@Composable
fun SkreemTrialGateScreen(
    settings: SettingsRepo,
    vinHint: String? = null,
    compact: Boolean = false,
    onTrialAccepted: () -> Unit = {},
    onImmoEnabled: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val vinSuggested = remember(vinHint) {
        PlanbMarque.fromVin(vinHint)?.takeIf { SkreemModule.isStellantisMarque(it) }
    }
    var selectedMarque by remember(vinSuggested) {
        mutableStateOf(vinSuggested ?: PlanbMarque.JEEP)
    }
    var termsChecked by remember { mutableStateOf(false) }

    val trialActive = settings.skreemTrialActive
    val trialAccepted = settings.skreemTrialAccepted
    val immoEnabled = settings.skreemImmoInfoEnabled

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (!compact) {
            Text(
                stringResource(R.string.skreem_trial_gate_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }

        Text(
            stringResource(R.string.skreem_trial_terms),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        SkreemMarqueChipRow(
            vinSuggestedMarque = vinSuggested,
            selectedMarque = selectedMarque,
            onMarqueSelected = { selectedMarque = it },
        )

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                stringResource(R.string.skreem_trial_included_heading),
                style = MaterialTheme.typography.titleSmall,
            )
            TrialIncludedRow(stringResource(R.string.skreem_trial_included_module_role))
            TrialIncludedRow(stringResource(R.string.skreem_trial_included_risk))
            TrialIncludedRow(stringResource(R.string.skreem_trial_included_marque_banners))
            TrialIncludedRow(stringResource(R.string.skreem_trial_excluded_programming))
        }

        if (trialAccepted && !trialActive) {
            Text(
                stringResource(R.string.skreem_trial_expired),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        if (trialActive) {
            val expiresLabel = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                .format(Date(settings.skreemTrialExpiresAtMs))
            Text(
                stringResource(R.string.skreem_trial_active_until, expiresLabel),
                style = MaterialTheme.typography.labelMedium,
                color = Color(0xFF2E7D32),
            )
        }

        when {
            !trialAccepted || !trialActive -> {
                TermsAcceptRow(
                    checked = termsChecked,
                    onCheckedChange = { termsChecked = it },
                )
                Button(
                    onClick = {
                        settings.acceptSkreemTrial(selectedMarque.id)
                        onTrialAccepted()
                    },
                    enabled = termsChecked,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.skreem_trial_accept_button))
                }
            }
            !immoEnabled -> {
                Text(
                    stringResource(R.string.skreem_trial_enable_immo_body),
                    style = MaterialTheme.typography.bodySmall,
                )
                Button(
                    onClick = {
                        settings.enableSkreemImmoForTrial()
                        onImmoEnabled()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.skreem_trial_enable_immo_button))
                }
            }
            else -> {
                RowWithIcon(stringResource(R.string.skreem_trial_immo_ready))
            }
        }
    }
}

@Composable
private fun TrialIncludedRow(text: String) {
    RowWithIcon(text)
}

@Composable
private fun RowWithIcon(text: String) {
    androidx.compose.foundation.layout.Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Default.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(text, style = MaterialTheme.typography.bodySmall)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SkreemMarqueChipRow(
    vinSuggestedMarque: PlanbMarque?,
    selectedMarque: PlanbMarque,
    onMarqueSelected: (PlanbMarque) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            stringResource(R.string.skreem_trial_marque_heading),
            style = MaterialTheme.typography.titleSmall,
        )
        vinSuggestedMarque?.let { marque ->
            Text(
                stringResource(R.string.skreem_trial_vin_suggests, marque.displayName()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            SKREEM_TRIAL_MARQUES.forEach { marque ->
                FilterChip(
                    selected = marque == selectedMarque,
                    onClick = { onMarqueSelected(marque) },
                    label = { Text(marque.displayName()) },
                )
            }
        }
    }
}

@Composable
private fun TermsAcceptRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    OutlinedButton(
        onClick = { onCheckedChange(!checked) },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            if (checked) {
                stringResource(R.string.skreem_trial_terms_acknowledged)
            } else {
                stringResource(R.string.skreem_trial_terms_acknowledge_action)
            },
        )
    }
}
