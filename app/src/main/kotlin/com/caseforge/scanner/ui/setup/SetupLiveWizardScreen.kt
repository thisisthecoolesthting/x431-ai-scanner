@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.caseforge.scanner.ui.setup

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.caseforge.scanner.data.SettingsRepo
import com.caseforge.scanner.transfer.SetupProgressReporter
import com.caseforge.scanner.ui.tier4.Tier4TrialGateScreen
import com.caseforge.scanner.ui.session.SessionPhotoCapture
import java.io.File
import kotlinx.coroutines.launch

@Composable
fun SetupLiveWizardScreen(
    settings: SettingsRepo,
    progress: SetupProgressStore,
    buildInfo: String,
    onSettings: () -> Unit,
    onSetupComplete: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var refreshTick by remember { mutableIntStateOf(0) }
    var expandedStep by remember { mutableStateOf(SetupLiveStep.APP_HEALTH) }
    var runningStep by remember { mutableStateOf<SetupLiveStep?>(null) }
    var skipReasonDraft by remember { mutableStateOf("") }
    var showCameraFlow by remember { mutableStateOf(false) }
    var aiHelpStep by remember { mutableStateOf<SetupLiveStep?>(null) }
    val cameraTestFile = remember {
        File(context.cacheDir, "setup_live_camera_test.jpg")
    }

    val totalSteps = SetupLiveStep.entries.size
    val resolvedCount = remember(refreshTick) { progress.passedOrSkippedCount() }
    val progressFraction = resolvedCount.toFloat() / totalSteps.toFloat()

    fun refresh() {
        refreshTick++
    }

    fun applyState(step: SetupLiveStep, state: SetupStepState) {
        progress.setStepState(step, state)
        refresh()
        scope.launch {
            SetupProgressReporter.report(
                settings = settings,
                step = step,
                status = state.status,
                detail = state.detail,
            )
        }
        if (step == SetupLiveStep.MARK_COMPLETE && state.status == SetupStepStatus.PASSED) {
            onSetupComplete()
        }
    }

    fun runStep(step: SetupLiveStep) {
        if (step == SetupLiveStep.CAMERA) {
            showCameraFlow = true
            expandedStep = step
            return
        }
        runningStep = step
        progress.setStepState(step, SetupStepState(SetupStepStatus.RUNNING, "Running…"))
        refresh()
        scope.launch {
            SetupProgressReporter.report(
                settings = settings,
                step = step,
                status = SetupStepStatus.RUNNING,
                detail = "Running…",
            )
        }
        scope.launch {
            val result = SetupStepRunner.run(
                context = context,
                settings = settings,
                progress = progress,
                step = step,
                cameraTestFile = if (step == SetupLiveStep.CAMERA) cameraTestFile else null,
            )
            applyState(step, result)
            runningStep = null
        }
    }

    fun skipStep(step: SetupLiveStep, reason: String) {
        val trimmed = reason.trim().ifBlank { "Operator skipped" }
        progress.setStepState(
            step,
            SetupStepState(SetupStepStatus.SKIPPED, "Skipped", skipReason = trimmed),
        )
        skipReasonDraft = ""
        refresh()
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Setup & live checklist") },
            actions = {
                IconButton(onClick = onSettings) {
                    Icon(Icons.Default.Settings, contentDescription = "Settings")
                }
            },
        )
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                buildInfo,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "Progress $resolvedCount/$totalSteps",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            LinearProgressIndicator(
                progress = { progressFraction.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "Run each step on the shop floor. Skip only when you accept the risk and log a reason.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            SetupLiveStep.entries.forEach { step ->
                val state = remember(refreshTick) { progress.stepState(step) }
                val expanded = expandedStep == step
                val isRunning = runningStep == step

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { expandedStep = step },
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            StepStatusIcon(state.status, isRunning)
                            Column(Modifier.weight(1f)) {
                                Text(step.title, fontWeight = FontWeight.Medium)
                                Text(
                                    step.summary,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        if (state.detail.isNotBlank()) {
                            Text(
                                state.detail,
                                style = MaterialTheme.typography.bodySmall,
                                color = when (state.status) {
                                    SetupStepStatus.FAILED -> MaterialTheme.colorScheme.error
                                    SetupStepStatus.SKIPPED -> MaterialTheme.colorScheme.tertiary
                                    else -> MaterialTheme.colorScheme.onSurface
                                },
                            )
                        }
                        state.skipReason?.let {
                            Text(
                                "Skip reason: $it",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        if (expanded) {
                            when (step) {
                                SetupLiveStep.LINK_TRANSPORT -> Text(
                                    stringResource(R.string.setup_link_transport_home_hint),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                SetupLiveStep.TIER4_TRIAL -> Tier4TrialGateScreen(
                                    settings = settings,
                                    compact = true,
                                    onTrialAccepted = {
                                        applyState(
                                            step,
                                            SetupStepState(
                                                SetupStepStatus.PASSED,
                                                context.getString(R.string.tier4_trial_step_passed_detail),
                                            ),
                                        )
                                    },
                                )
                                SetupLiveStep.CAMERA -> if (showCameraFlow) {
                                    SessionPhotoCapture(
                                        outputFile = cameraTestFile,
                                        hint = "Capture any test frame to verify camera + FileProvider.",
                                        skipLabel = "Skip camera test",
                                        autoLaunch = false,
                                        onCaptured = { path ->
                                            showCameraFlow = false
                                            applyState(
                                                step,
                                                SetupStepState(
                                                    SetupStepStatus.PASSED,
                                                    "Capture OK at $path",
                                                ),
                                            )
                                        },
                                        onSkip = {
                                            showCameraFlow = false
                                            skipReasonDraft = "Camera test skipped from capture UI"
                                            skipStep(step, skipReasonDraft)
                                        },
                                    )
                                }
                                else -> Unit
                            }

                            if (step != SetupLiveStep.CAMERA || !showCameraFlow) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = { runStep(step) },
                                        enabled = runningStep == null,
                                    ) {
                                        Text(if (isRunning) "Running…" else "Run step")
                                    }
                                    OutlinedButton(
                                        onClick = { skipStep(step, skipReasonDraft) },
                                        enabled = runningStep == null,
                                    ) {
                                        Text("Skip")
                                    }
                                }
                                OutlinedTextField(
                                    value = skipReasonDraft,
                                    onValueChange = { skipReasonDraft = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    label = { Text("Skip reason (optional)") },
                                    singleLine = true,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    aiHelpStep?.let { step ->
        SetupAiHelpSheet(
            step = step,
            stepState = progress.stepState(step),
            settings = settings,
            onDismiss = { aiHelpStep = null },
        )
    }
}

@Composable
private fun StepStatusIcon(status: SetupStepStatus, isRunning: Boolean) {
    when {
        isRunning -> CircularProgressIndicator(modifier = Modifier.padding(4.dp))
        status == SetupStepStatus.PASSED -> Icon(
            Icons.Default.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        status == SetupStepStatus.FAILED -> Icon(
            Icons.Default.Close,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
        )
        status == SetupStepStatus.SKIPPED -> Icon(
            Icons.Default.HourglassEmpty,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.tertiary,
        )
        else -> Icon(
            Icons.Default.RadioButtonUnchecked,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
