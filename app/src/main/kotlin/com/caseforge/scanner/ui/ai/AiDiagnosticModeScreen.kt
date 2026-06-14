@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.caseforge.scanner.ui.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.caseforge.scanner.ai.*
import com.caseforge.scanner.ui.theme.TcwTokens

/**
 * Host for AI Diagnostic Mode. Switches sub-screen on the controller's phase.
 * Camera launches are passed in from MainActivity (which owns the ActivityResult registry).
 */
@Composable
fun AiDiagnosticModeScreen(
    controller: AiDiagnosticController,
    onBack: () -> Unit,
    onCaptureEngineBay: () -> Unit,
    onCaptureVin: () -> Unit,
    onCaptureDash: () -> Unit,
) {
    val ui by controller.ui.collectAsState()

    Column(Modifier.fillMaxSize().background(Color(0xFFF4F4F2))) {
        TopAppBar(
            title = { Text("AI Diagnostic") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = TcwTokens.Ink,
                titleContentColor = TcwTokens.OnInk,
                navigationIconContentColor = TcwTokens.Amber,
            ),
        )
        StepDots(ui.phase)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (ui.phase) {
                AiDiagPhase.SYMPTOMS -> SymptomsStep(controller)
                AiDiagPhase.PHOTO_ENGINE_BAY -> PhotoStep(
                    title = "Engine bay photo",
                    body = "Open the hood and take a photo of the engine bay. The AI looks for leaks, " +
                        "disconnected hoses, and corrosion.",
                    onCapture = onCaptureEngineBay,
                    onSkip = { controller.onEngineBayPhoto(null) },
                )
                AiDiagPhase.PHOTO_DOOR_JAMB -> PhotoStep(
                    title = "Door jamb / VIN",
                    body = "Photograph the VIN sticker on the driver's door jamb. We'll read the VIN automatically.",
                    onCapture = onCaptureVin,
                    onSkip = { controller.onVinPhoto(null) },
                )
                AiDiagPhase.PHOTO_DASH -> PhotoStep(
                    title = "Dashboard photo",
                    body = "With the key on, photograph the dash so the AI can read the mileage and which " +
                        "warning lights are lit.",
                    onCapture = onCaptureDash,
                    onSkip = { controller.onDashPhoto(null) },
                )
                AiDiagPhase.CONNECT -> ConnectStep(controller, ui)
                AiDiagPhase.RUNNING -> RunningStep(controller)
                AiDiagPhase.RESULT -> ResultStep(ui.result, onBack)
                AiDiagPhase.ERROR -> ErrorStep(ui.error, onRetry = { controller.retry() }, onBack = onBack)
            }
        }
    }
}

@Composable
private fun StepDots(phase: AiDiagPhase) {
    val order = listOf(
        AiDiagPhase.SYMPTOMS, AiDiagPhase.PHOTO_ENGINE_BAY, AiDiagPhase.PHOTO_DOOR_JAMB,
        AiDiagPhase.PHOTO_DASH, AiDiagPhase.CONNECT, AiDiagPhase.RUNNING, AiDiagPhase.RESULT,
    )
    val idx = order.indexOf(phase).coerceAtLeast(0)
    Row(
        Modifier.fillMaxWidth().background(TcwTokens.Ink).padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        order.forEachIndexed { i, _ ->
            Box(
                Modifier.weight(1f).height(4.dp)
                    .background(
                        if (i <= idx) TcwTokens.Amber else Color.White.copy(alpha = 0.18f),
                        RoundedCornerShape(2.dp),
                    ),
            )
        }
    }
}

private val COMPLAINT_CHIPS = listOf(
    "Check engine light", "Won't start", "Rough idle", "Stalling", "Overheating",
    "Poor fuel economy", "Hesitation", "Noise", "Vibration", "Warning light",
)

@Composable
private fun SymptomsStep(controller: AiDiagnosticController) {
    var text by remember { mutableStateOf("") }
    val selected = remember { mutableStateListOf<String>() }

    Column(
        Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("What's the complaint?", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = TcwTokens.Ink)
        Text("Describe the problem in your own words, or tap common complaints below.", color = TcwTokens.Muted, style = MaterialTheme.typography.bodyMedium)

        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp),
            placeholder = { Text("e.g. rough idle when cold, check engine light came on yesterday") },
            keyboardOptions = KeyboardOptions.Default,
        )

        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            COMPLAINT_CHIPS.forEach { chip ->
                FilterChip(
                    selected = chip in selected,
                    onClick = { if (chip in selected) selected.remove(chip) else selected.add(chip) },
                    label = { Text(chip) },
                )
            }
        }

        Spacer(Modifier.weight(1f))
        Button(
            onClick = { controller.setSymptoms(text, selected.toList()); controller.onSymptomsNext() },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(TcwTokens.RadiusMedium),
            colors = ButtonDefaults.buttonColors(containerColor = TcwTokens.Amber, contentColor = TcwTokens.OnAmber),
        ) { Text("Next", fontWeight = FontWeight.Bold) }
        TextButton(onClick = { controller.onSymptomsSkip() }, modifier = Modifier.fillMaxWidth()) {
            Text("Skip — no specific symptoms", color = TcwTokens.Muted)
        }
    }
}

@Composable
private fun PhotoStep(title: String, body: String, onCapture: () -> Unit, onSkip: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = TcwTokens.Ink)
        Text(body, color = TcwTokens.Muted, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.weight(1f))
        Button(
            onClick = onCapture,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(TcwTokens.RadiusMedium),
            colors = ButtonDefaults.buttonColors(containerColor = TcwTokens.Amber, contentColor = TcwTokens.OnAmber),
        ) { Text("Take photo", fontWeight = FontWeight.Bold) }
        TextButton(onClick = onSkip, modifier = Modifier.fillMaxWidth()) {
            Text("Skip this step", color = TcwTokens.Muted)
        }
    }
}

@Composable
private fun ConnectStep(controller: AiDiagnosticController, ui: AiDiagUiState) {
    LaunchedEffect(Unit) { controller.startConnect() }
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(color = TcwTokens.Amber)
        Spacer(Modifier.height(20.dp))
        Text("Connecting to the vehicle", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = TcwTokens.Ink)
        Spacer(Modifier.height(8.dp))
        Text(ui.connectProgress ?: "Selecting protocol…", color = TcwTokens.Muted)
    }
}

@Composable
private fun RunningStep(controller: AiDiagnosticController) {
    val feed by controller.thoughtFeed.collectAsState()
    val gauges by controller.gauges.collectAsState()
    val question by controller.pendingQuestion.collectAsState()

    Column(Modifier.fillMaxSize()) {
        if (gauges.isNotEmpty()) {
            Surface(color = TcwTokens.Ink, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("LIVE DATA", color = TcwTokens.Amber, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Surface(color = Color.White, shape = RoundedCornerShape(TcwTokens.RadiusSmall)) {
                        Box(Modifier.padding(8.dp)) { AiLiveGaugeGrid(gauges) }
                    }
                }
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 12.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(Modifier.size(16.dp), color = TcwTokens.Amber, strokeWidth = 2.dp)
                    Text("AI is diagnosing…", color = TcwTokens.Muted, fontWeight = FontWeight.Medium)
                }
            }
            items(feed) { e -> ThoughtRow(e) }
        }

        question?.let { q -> QuestionBar(q, onAnswer = { controller.submitAnswer(it) }) }
    }
}

@Composable
private fun ThoughtRow(e: AiThoughtEvent) {
    val color = when (e.kind) {
        AiThoughtKind.FINDING -> TcwTokens.Green
        AiThoughtKind.TOOL -> TcwTokens.Blue
        AiThoughtKind.QUESTION -> TcwTokens.Amber
        AiThoughtKind.ANSWER -> TcwTokens.Amber
        AiThoughtKind.RESULT -> TcwTokens.Green
        AiThoughtKind.ERROR -> TcwTokens.Red
        else -> TcwTokens.Muted
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.padding(top = 5.dp).size(8.dp).background(color, RoundedCornerShape(4.dp)))
        Column {
            Text(e.title, color = TcwTokens.Ink, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            e.detail?.let { Text(it, color = TcwTokens.Muted, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
private fun QuestionBar(q: PendingUserQuestion, onAnswer: (String) -> Unit) {
    var free by remember(q.id) { mutableStateOf("") }
    Surface(color = TcwTokens.AmberSubtle, shadowElevation = 8.dp, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(q.question, fontWeight = FontWeight.Bold, color = TcwTokens.Ink, style = MaterialTheme.typography.titleSmall)
            if (q.answerChips.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    q.answerChips.forEach { chip ->
                        Button(
                            onClick = { onAnswer(chip) },
                            shape = RoundedCornerShape(TcwTokens.RadiusSmall),
                            colors = ButtonDefaults.buttonColors(containerColor = TcwTokens.Ink, contentColor = TcwTokens.OnInk),
                        ) { Text(chip) }
                    }
                }
            }
            if (q.allowFreeText) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = free, onValueChange = { free = it },
                        modifier = Modifier.weight(1f), singleLine = true,
                        placeholder = { Text("Type an answer…") },
                    )
                    Button(
                        onClick = { if (free.isNotBlank()) onAnswer(free.trim()) },
                        colors = ButtonDefaults.buttonColors(containerColor = TcwTokens.Amber, contentColor = TcwTokens.OnAmber),
                    ) { Text("Send") }
                }
            }
        }
    }
}

@Composable
private fun ResultStep(result: AiDiagnosticResult?, onBack: () -> Unit) {
    if (result == null) { ErrorStep("No result.", {}, onBack); return }
    Column(
        Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Surface(color = TcwTokens.Ink, shape = RoundedCornerShape(TcwTokens.RadiusMedium), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("ROOT CAUSE", color = TcwTokens.Amber, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Text(result.rootCause, color = TcwTokens.OnInk, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("Confidence ${(result.confidence * 100).toInt()}%", color = TcwTokens.OnInk.copy(alpha = 0.7f))
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Recommended repair", fontWeight = FontWeight.Bold, color = TcwTokens.Ink)
                Text(result.recommendedRepair, color = TcwTokens.Ink, style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (result.supportingEvidence.isNotEmpty()) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Evidence", fontWeight = FontWeight.Bold, color = TcwTokens.Ink)
                    result.supportingEvidence.forEach { Text("• $it", color = TcwTokens.Muted, style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
        if (result.warningLights.isNotEmpty() || result.mileage != null) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    result.mileage?.let { Text("Mileage: $it", color = TcwTokens.Ink) }
                    if (result.warningLights.isNotEmpty()) Text("Lights: ${result.warningLights.joinToString(", ")}", color = TcwTokens.Ink)
                    result.vin?.let { Text("VIN: $it", color = TcwTokens.Muted, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
        Text("Saved to history.", color = TcwTokens.Muted, style = MaterialTheme.typography.bodySmall)
        Button(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(TcwTokens.RadiusMedium),
            colors = ButtonDefaults.buttonColors(containerColor = TcwTokens.Ink, contentColor = TcwTokens.OnInk),
        ) { Text("Done", fontWeight = FontWeight.Bold) }
    }
}

@Composable
private fun ErrorStep(error: String?, onRetry: () -> Unit, onBack: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Couldn't finish", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = TcwTokens.Ink)
        Spacer(Modifier.height(8.dp))
        Text(error ?: "Something went wrong.", color = TcwTokens.Muted, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = onRetry,
            colors = ButtonDefaults.buttonColors(containerColor = TcwTokens.Amber, contentColor = TcwTokens.OnAmber),
            modifier = Modifier.fillMaxWidth().height(50.dp),
        ) { Text("Try connecting again", fontWeight = FontWeight.Bold) }
        TextButton(onClick = onBack) { Text("Back to home", color = TcwTokens.Muted) }
    }
}
