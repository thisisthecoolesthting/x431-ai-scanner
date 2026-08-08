package com.caseforge.scanner.ui.tier4

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
import java.text.DateFormat
import java.util.Date

/**
 * Trial gate for Ford + Stellantis Tier 4 programming **reference** access.
 * Does not claim or enable automated flash — checklists and Plan A fallback only.
 */
@Composable
fun Tier4TrialGateScreen(
    settings: SettingsRepo,
    vinHint: String? = null,
    compact: Boolean = false,
    onTrialAccepted: () -> Unit = {},
    onChecklistsEnabled: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val vinSuggested = remember(vinHint) { PlanbMarque.fromVin(vinHint) }
    val trialMarques = remember { PlanbMarque.TRIAL_MARQUES.toList() }
    var selectedMarque by remember(vinSuggested) {
        mutableStateOf(
            vinSuggested?.takeIf { it.isTier4TrialMarque }
                ?: PlanbMarque.FORD,
        )
    }
    var termsChecked by remember { mutableStateOf(false) }

    val trialActive = settings.tier4TrialActive
    val trialAccepted = settings.tier4TrialAccepted
    val fullLicense = settings.tier4FullLicenseEnabled
    val hasEntitlement = settings.hasTier4ProgrammingEntitlement
    val trialExpired = trialAccepted && !trialActive && !fullLicense
    val programmingEnabled = settings.tier4ProgrammingEnabled

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (!compact) {
            Text(
                stringResource(R.string.tier4_trial_gate_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }

        Text(
            stringResource(R.string.tier4_trial_terms),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        TrialMarqueChipRow(
            vinSuggestedMarque = vinSuggested?.takeIf { it.isTier4TrialMarque },
            selectedMarque = selectedMarque,
            marques = trialMarques,
            onMarqueSelected = { selectedMarque = it },
        )

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                stringResource(R.string.tier4_trial_included_heading),
                style = MaterialTheme.typography.titleSmall,
            )
            TrialIncludedRow(stringResource(R.string.tier4_trial_included_checklists))
            TrialIncludedRow(stringResource(R.string.tier4_trial_included_runbook))
            TrialIncludedRow(stringResource(R.string.tier4_trial_included_plan_a))
            TrialIncludedRow(stringResource(R.string.tier4_trial_excluded_flash))
        }

        if (trialExpired) {
            Text(
                stringResource(R.string.tier4_trial_expired),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
            Text(
                stringResource(R.string.tier4_license_path_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (fullLicense) {
            Text(
                stringResource(R.string.tier4_license_active),
                style = MaterialTheme.typography.labelMedium,
                color = Color(0xFF2E7D32),
            )
            settings.tier4LicenseMarques?.let { marques ->
                Text(
                    stringResource(R.string.tier4_license_marques_active, marques),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else if (trialActive) {
            val expiresLabel = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                .format(Date(settings.tier4TrialExpiresAtMs))
            Text(
                stringResource(R.string.tier4_trial_active_until, expiresLabel),
                style = MaterialTheme.typography.labelMedium,
                color = Color(0xFF2E7D32),
            )
        }

        when {
            !hasEntitlement -> {
                TermsAcceptRow(
                    checked = termsChecked,
                    onCheckedChange = { termsChecked = it },
                )
                Button(
                    onClick = {
                        settings.acceptTier4Trial(selectedMarque.id)
                        onTrialAccepted()
                    },
                    enabled = termsChecked,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.tier4_trial_accept_button))
                }
            }
            !programmingEnabled -> {
                Text(
                    stringResource(R.string.tier4_trial_enable_checklists_body),
                    style = MaterialTheme.typography.bodySmall,
                )
                Button(
                    onClick = {
                        settings.enableTier4ProgrammingForTrial()
                        onChecklistsEnabled()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.tier4_trial_enable_checklists_button))
                }
            }
            else -> {
                RowWithIcon(stringResource(R.string.tier4_trial_checklists_ready))
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
private fun TrialMarqueChipRow(
    vinSuggestedMarque: PlanbMarque?,
    selectedMarque: PlanbMarque,
    marques: List<PlanbMarque>,
    onMarqueSelected: (PlanbMarque) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            stringResource(R.string.tier4_trial_marque_heading),
            style = MaterialTheme.typography.titleSmall,
        )
        vinSuggestedMarque?.let { marque ->
            Text(
                stringResource(R.string.tier4_trial_vin_suggests, marque.displayName()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            marques.forEach { marque ->
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
                stringResource(R.string.tier4_trial_terms_acknowledged)
            } else {
                stringResource(R.string.tier4_trial_terms_acknowledge_action)
            },
        )
    }
}
