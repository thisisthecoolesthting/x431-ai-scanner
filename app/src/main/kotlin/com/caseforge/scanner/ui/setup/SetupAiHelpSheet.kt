@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.caseforge.scanner.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.caseforge.scanner.R
import com.caseforge.scanner.agent.SetupAssistantAgent
import com.caseforge.scanner.data.SettingsRepo
import kotlinx.coroutines.launch

/**
 * Slide-over help for a single setup step: offline card first, optional Deep help via [SetupAssistantAgent].
 */
@Composable
fun SetupAiHelpSheet(
    step: SetupLiveStep,
    stepState: SetupStepState,
    settings: SettingsRepo,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val agent = remember(settings) { SetupAssistantAgent(settings) }
    val card = remember(step) { SetupStepHelp.card(step) }
    val failure = remember(stepState) { SetupAssistantAgent.failureFromState(step, stepState) }

    var followUp by remember { mutableStateOf("") }
    var deepReply by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(R.string.setup_ai_sheet_title, step.title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )

            Text(
                stringResource(card.quickHelp),
                style = MaterialTheme.typography.bodyMedium,
            )

            Text(
                stringResource(R.string.setup_ai_fix_tips_heading),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
            )
            Text(
                stringResource(card.fixTips),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (stepState.detail.isNotBlank()) {
                Text(
                    stringResource(R.string.setup_ai_last_result_heading),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    stepState.detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = when (stepState.status) {
                        SetupStepStatus.FAILED -> MaterialTheme.colorScheme.error
                        SetupStepStatus.SKIPPED -> MaterialTheme.colorScheme.tertiary
                        else -> MaterialTheme.colorScheme.onSurface
                    },
                )
            }

            deepReply?.let { reply ->
                Text(
                    stringResource(R.string.setup_ai_deep_reply_heading),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                )
                Text(reply, style = MaterialTheme.typography.bodyMedium)
            }

            OutlinedTextField(
                value = followUp,
                onValueChange = { followUp = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.setup_ai_follow_up_label)) },
                placeholder = { Text(stringResource(R.string.setup_ai_follow_up_hint)) },
                minLines = 2,
                enabled = !loading,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        loading = true
                        scope.launch {
                            deepReply = agent.deepHelp(step, failure, followUp.ifBlank { null })
                            loading = false
                        }
                    },
                    enabled = !loading,
                ) {
                    Text(stringResource(R.string.setup_ai_deep_help))
                }
                OutlinedButton(onClick = onDismiss, enabled = !loading) {
                    Text(stringResource(R.string.setup_ai_close))
                }
            }

            if (loading) {
                CircularProgressIndicator(modifier = Modifier.padding(4.dp))
            }
        }
    }
}
