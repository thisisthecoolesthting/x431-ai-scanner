package com.caseforge.scanner.ui.about

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * About / build info screen.
 * Wire into navigation as route `"about"` when the shell exposes it.
 */
@Composable
fun AboutScreen(
    versionName: String,
    versionCode: Int,
    buildSha: String,
    onOpenTransferLog: () -> Unit,
    onOpenGithub: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        Text(
            text = "TOGETHER CAR WORKS",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 4.sp,
            ),
        )
        Text("Version $versionName ($versionCode)", style = MaterialTheme.typography.bodyLarge)
        Text("Build $buildSha", style = MaterialTheme.typography.bodyMedium)
        Text(
            "© 2026 · MIT-licensed dependencies",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onOpenTransferLog,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Send diagnostics")
        }
        OutlinedButton(
            onClick = onOpenGithub,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("View on GitHub")
        }
    }
}
