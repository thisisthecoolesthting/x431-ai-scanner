package com.caseforge.scanner.overlay.compose.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.caseforge.scanner.engine.sequences.Prompt
import com.caseforge.scanner.engine.sequences.SequenceResult
import com.caseforge.scanner.engine.sequences.SequenceRunner
import com.caseforge.scanner.engine.sequences.StepResult
import com.caseforge.scanner.engine.sequences.TestSequence
import com.caseforge.scanner.overlay.compose.Spacing
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

@Composable
fun SequenceRunnerScreen(
    sequence: TestSequence,
    runner: SequenceRunner,
    onComplete: (SequenceResult) -> Unit,
    modifier: Modifier = Modifier,
) {
    val rows = remember { mutableStateListOf<StepResult>() }
    var running by remember { mutableStateOf(true) }
    var done by remember { mutableStateOf<SequenceResult?>(null) }
    var prompt by remember { mutableStateOf<Prompt?>(null) }
    var ack by remember { mutableStateOf<(() -> Unit)?>(null) }

    LaunchedEffect(sequence.id) {
        val r = runner.run(sequence, onPrompt = { p ->
            suspendCancellableCoroutine { c ->
                prompt = p
                ack = { prompt = null; ack = null; c.resume(Unit) }
            }
        }, onStepFinished = { _, _, s -> rows.add(s) })
        done = r
        running = false
        onComplete(r)
    }

    prompt?.let { p ->
        AlertDialog(
            onDismissRequest = { ack?.invoke() },
            title = { Text(p.label) },
            text = { Text(p.message) },
            confirmButton = { TextButton(onClick = { ack?.invoke() }) { Text("Continue") } },
        )
    }

    Column(modifier.fillMaxSize().padding(Spacing.Space16)) {
        Text(sequence.label, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("Step ${rows.size} of ${sequence.steps.size}", style = MaterialTheme.typography.labelLarge)
        LinearProgressIndicator({ if (done != null) 1f else rows.size.toFloat() / sequence.steps.size.coerceAtLeast(1) }, Modifier.fillMaxWidth())
        LazyColumn(Modifier.weight(1f)) {
            itemsIndexed(rows) { _, r ->
                ListItem(
                    headlineContent = { Text(r.step.label) },
                    supportingContent = { Text(r.output.take(60)) },
                    leadingContent = {
                        Icon(
                            if (r.passed) Icons.Default.CheckCircle else Icons.Default.Error,
                            null,
                            tint = if (r.passed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        )
                    },
                )
            }
        }
        done?.let { Text(it.summary, style = MaterialTheme.typography.titleSmall) }
    }
}
