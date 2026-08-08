@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.caseforge.scanner.ui.session

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.caseforge.scanner.data.session.CustomerSessionRepository
import com.caseforge.scanner.transfer.SessionEventLogger

@Composable
fun DashboardPhotoScreen(
    sessionId: String,
    copy: WizardCopyRoot,
    onDone: (photoPath: String?) -> Unit,
) {
    val context = LocalContext.current
    val repo = remember { CustomerSessionRepository(context) }
    val file = remember(sessionId) { repo.photoFile(sessionId, "dashboard.jpg") }
    var showCamera by remember { mutableStateOf(false) }

    if (!showCamera) {
        Column(
            Modifier.fillMaxSize().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(copy.dashboard.title, style = MaterialTheme.typography.headlineSmall)
            copy.dashboard.instructions.forEachIndexed { i, line ->
                Text("${i + 1}. $line", style = MaterialTheme.typography.bodyLarge)
            }
            Text(
                copy.dashboard.skip_note,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            Button(
                onClick = { showCamera = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Open camera")
            }
            OutlinedButton(
                onClick = {
                    SessionEventLogger.log(context, sessionId, "wizard_dashboard", detail = "skipped")
                    onDone(null)
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Skip")
            }
        }
    } else {
        SessionPhotoCapture(
            outputFile = file,
            hint = "Capture warning lights and odometer.",
            autoLaunch = true,
            onCaptured = { path ->
                SessionEventLogger.log(context, sessionId, "wizard_dashboard", detail = path ?: "skipped")
                onDone(path)
            },
            onSkip = {
                SessionEventLogger.log(context, sessionId, "wizard_dashboard", detail = "skipped")
                onDone(null)
            },
        )
    }
}
