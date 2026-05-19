@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.caseforge.scanner.ui.updates

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.roundToInt

@Composable
fun UpdateCenterScreen(
    versionName: String,
    versionCode: Int,
    buildSha: String,
    onCheckNow: () -> Unit,
    onInstall: (url: String) -> Unit,
    onOpenPermissionSettings: () -> Unit,
    onRestartApp: () -> Unit,
    phaseFlow: StateFlow<UpdaterPhase>,
) {
    val phase by phaseFlow.collectAsState()
    val context = LocalContext.current
    val history = remember { UpdateHistory.load(context) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Update Center", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Current build $versionName ($versionCode) · $buildSha",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        UpdateStatusCard(
            phase = phase,
            onCheckNow = onCheckNow,
            onInstall = onInstall,
            onOpenPermissionSettings = onOpenPermissionSettings,
            onRestartApp = onRestartApp,
            onCopyLog = { message, hint ->
                copyToClipboard(context, "$message\n$hint")
            },
        )

        if (history.isNotEmpty()) {
            Text("Update history", style = MaterialTheme.typography.titleSmall)
            history.forEach { entry ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            "${entry.versionName} (${entry.sha})",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            "${entry.formattedTime()} · ${formatMb(entry.downloadBytes)} MB",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun UpdateStatusCard(
    phase: UpdaterPhase,
    onCheckNow: () -> Unit,
    onInstall: (url: String) -> Unit,
    onOpenPermissionSettings: () -> Unit,
    onRestartApp: () -> Unit,
    onCopyLog: (message: String, hint: String) -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (phase) {
                UpdaterPhase.Idle, UpdaterPhase.NoUpdate -> {
                    Text("You're on the latest build.")
                    Button(onClick = onCheckNow, modifier = Modifier.fillMaxWidth()) {
                        Text("Check now")
                    }
                }

                UpdaterPhase.Checking -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(12.dp))
                        Text("Checking for updates…")
                    }
                }

                is UpdaterPhase.UpdateAvailable -> {
                    var notesExpanded by remember(phase.notes) { mutableStateOf(false) }
                    Text(
                        "Update available: ${phase.versionName}",
                        fontWeight = FontWeight.SemiBold,
                    )
                    TextButton(onClick = { notesExpanded = !notesExpanded }) {
                        Text(if (notesExpanded) "Hide what's new" else "What's new")
                    }
                    if (notesExpanded && phase.notes.isNotBlank()) {
                        Text(
                            phase.notes.take(2000),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Button(
                        onClick = { onInstall(phase.downloadUrl) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Install")
                    }
                }

                is UpdaterPhase.Downloading -> {
                    val total = phase.total.coerceAtLeast(1L)
                    val progress = (phase.bytesRead.toFloat() / total).coerceIn(0f, 1f)
                    val mbRead = formatMb(phase.bytesRead)
                    val mbTotal = formatMb(phase.total)
                    val pct = (progress * 100).roundToInt()
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                    Text("$mbRead MB / $mbTotal MB · $pct % · ${phase.urlOrName}")
                }

                UpdaterPhase.Installing -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        DotPulse()
                        Spacer(Modifier.width(12.dp))
                        Text("Installing on device…")
                    }
                }

                is UpdaterPhase.Installed -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Installed ${phase.versionName} (${phase.sha}). Restart to apply.",
                            fontWeight = FontWeight.Medium,
                        )
                    }
                    Button(onClick = onRestartApp, modifier = Modifier.fillMaxWidth()) {
                        Text("Restart")
                    }
                }

                is UpdaterPhase.Failed -> {
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(
                            Icons.Default.Error,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(24.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(phase.message, fontWeight = FontWeight.Bold)
                            Text(phase.hint, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onCheckNow, modifier = Modifier.weight(1f)) {
                            Text("Retry")
                        }
                        OutlinedButton(
                            onClick = { onCopyLog(phase.message, phase.hint) },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("Copy log")
                        }
                    }
                }

                UpdaterPhase.PermissionRequired -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(24.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Together Car Works needs permission to install updates.")
                    }
                    Button(onClick = onOpenPermissionSettings, modifier = Modifier.fillMaxWidth()) {
                        Text("Open settings")
                    }
                }
            }
        }
    }
}

@Composable
private fun DotPulse() {
    val transition = rememberInfiniteTransition(label = "dotPulse")
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(3) { index ->
            val alpha by transition.animateFloat(
                initialValue = 0.3f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(600, delayMillis = index * 150),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "dot$index",
            )
            Box(
                Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .alpha(alpha)
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
    }
}

private fun formatMb(bytes: Long): String =
    "%.1f".format(bytes / 1_048_576.0)

private fun copyToClipboard(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("update log", text))
}
