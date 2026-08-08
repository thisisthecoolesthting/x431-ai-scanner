package com.caseforge.scanner.overlay.compose

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.caseforge.scanner.engine.sequences.*
import kotlinx.coroutines.launch

/**
 * Compose UI for real-time sequence execution monitoring.
 * Displays current step, progress bar, live results, and pass/fail status per step.
 */
@Composable
fun SequenceRunnerScreen(
    sequence: TestSequence,
    runner: SequenceRunner,
    onComplete: (SequenceResult) -> Unit,
    modifier: Modifier = Modifier
) {
    var result: SequenceResult? by remember { mutableStateOf(null) }
    var isRunning by remember { mutableStateOf(true) }
    var currentStepIndex by remember { mutableStateOf(0) }

    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        scope.launch {
            val execResult = runner.run(sequence)
            result = execResult
            isRunning = false
            onComplete(execResult)
        }
    }

    val stepResults = result?.stepResults ?: emptyList()
    val progress = if (result != null) 1.0f else {
        if (sequence.steps.isEmpty()) 0f else stepResults.size / sequence.steps.size.toFloat()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF1A1A1A))
            .padding(16.dp)
    ) {
        // Header
        SequenceHeader(
            sequenceName = sequence.label,
            status = if (isRunning) "Running..." else if (result?.passed == true) "PASS" else "FAIL",
            statusColor = when {
                isRunning -> Color(0xFFFFA500)  // Orange
                result?.passed == true -> Color(0xFF00AA00)  // Green
                else -> Color(0xFFDD0000)  // Red
            }
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Progress bar
        LinearProgressIndicator(
            progress = progress,
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .background(Color(0xFF333333), RoundedCornerShape(4.dp)),
            color = Color(0xFF00AA00),
            trackColor = Color(0xFF333333)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Current step indicator
        if (isRunning && currentStepIndex < sequence.steps.size) {
            CurrentStepCard(sequence.steps[currentStepIndex])
            Spacer(modifier = Modifier.height(12.dp))
        }

        // Step results list
        Text(
            text = "Step Results (${stepResults.size}/${sequence.steps.size})",
            color = Color(0xFFCCCCCC),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color(0xFF2A2A2A), RoundedCornerShape(8.dp))
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(stepResults) { stepResult ->
                StepResultCard(stepResult)
            }

            if (isRunning && stepResults.size < sequence.steps.size) {
                items(sequence.steps.drop(stepResults.size).take(2)) { step ->
                    PendingStepCard(step)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Footer summary
        if (result != null) {
            SummaryFooter(result!!)
        }
    }
}

@Composable
private fun SequenceHeader(
    sequenceName: String,
    status: String,
    statusColor: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF252525), RoundedCornerShape(8.dp))
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = sequenceName,
                color = Color(0xFFFFFFFF),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Surface(
            color = statusColor,
            shape = RoundedCornerShape(4.dp)
        ) {
            Text(
                text = status,
                color = Color.Black,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun CurrentStepCard(step: Step) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFF3A3A3A),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = Color(0xFFFFA500)
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Now: ${step.label}",
                    color = Color(0xFFFFFFFF),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Text(
                    text = when (step) {
                        is RunCapability -> "Running capability: ${step.capabilityId}"
                        is Wait -> "Waiting ${step.seconds}s"
                        is CapturePid -> "Reading PID ${step.pid}"
                        is Prompt -> step.message.take(40) + if (step.message.length > 40) "..." else ""
                        is Branch -> "Evaluating: ${step.condition}"
                    },
                    color = Color(0xFFBBBBBB),
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
private fun StepResultCard(result: StepResult) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = if (result.passed) Color(0xFF1A3A1A) else Color(0xFF3A1A1A),
        shape = RoundedCornerShape(6.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(10.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = if (result.passed) Icons.Default.CheckCircle else Icons.Default.Error,
                contentDescription = if (result.passed) "Pass" else "Fail",
                tint = if (result.passed) Color(0xFF00DD00) else Color(0xFFDD3333),
                modifier = Modifier.size(18.dp)
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = result.step.label,
                    color = Color(0xFFFFFFFF),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )

                if (result.output.isNotBlank()) {
                    Text(
                        text = result.output.take(60) + if (result.output.length > 60) "..." else "",
                        color = Color(0xFFAAAAAA),
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                if (result.capturedValues.isNotEmpty()) {
                    Text(
                        text = "Captured: ${result.capturedValues.keys.joinToString(", ")}",
                        color = Color(0xFF88DDFF),
                        fontSize = 9.sp
                    )
                }

                Text(
                    text = "${result.duration}ms",
                    color = Color(0xFF999999),
                    fontSize = 9.sp
                )
            }
        }
    }
}

@Composable
private fun PendingStepCard(step: Step) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFF262626),
        shape = RoundedCornerShape(6.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(10.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.HourglassEmpty,
                contentDescription = "Pending",
                tint = Color(0xFF999999),
                modifier = Modifier.size(18.dp)
            )

            Text(
                text = step.label,
                color = Color(0xFF888888),
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun SummaryFooter(result: SequenceResult) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = if (result.passed) Color(0xFF1A3A1A) else Color(0xFF3A1A1A),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = result.summary,
                    color = if (result.passed) Color(0xFF00DD00) else Color(0xFFDD3333),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "${result.totalDuration}ms total",
                    color = Color(0xFFAAAAAA),
                    fontSize = 11.sp
                )
            }

            if (result.errorMessage != null) {
                Text(
                    text = "Error: ${result.errorMessage}",
                    color = Color(0xFFFF8888),
                    fontSize = 10.sp
                )
            }

            Text(
                text = "${result.stepResults.size}/${result.stepResults.size} steps executed",
                color = Color(0xFFBBBBBB),
                fontSize = 10.sp
            )
        }
    }
}
