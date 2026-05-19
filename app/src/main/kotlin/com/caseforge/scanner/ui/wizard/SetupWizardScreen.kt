@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.caseforge.scanner.ui.wizard

import android.content.pm.PackageManager
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.caseforge.scanner.BuildConfig
import com.caseforge.scanner.R
import androidx.compose.ui.res.stringResource
import com.caseforge.scanner.agent.ScannerAccessibilityService
import com.caseforge.scanner.data.SettingsRepo

private val OEM_DIAG_PACKAGES = listOf(
    "com.cnlaunch.x431padv",
    "com.cnlaunch.x431padv2",
    "com.cnlaunch.diagnose.x431pro",
    "com.cnlaunch.diagnosemodule",
    "com.cnlaunch.x431pro",
    "com.x431.diagnose",
)

@Composable
fun SetupWizardScreen(
    settings: SettingsRepo,
    onOpenA11y: () -> Unit,
    onGrantOverlay: () -> Unit,
    onGrantCapture: () -> Unit,
    onStartBubble: () -> Unit,
    onOpenDataExport: () -> Unit = {},
    onFinish: () -> Unit,
) {
    val ctx = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    // All checks live in state so changes trigger recomposition.
    var keyOk by remember { mutableStateOf(settings.claudeApiKey.isNotBlank()) }
    var accessibilityOn by remember { mutableStateOf(ScannerAccessibilityService.instance() != null) }
    var overlayOk by remember { mutableStateOf(Settings.canDrawOverlays(ctx)) }
    var oemPkg by remember { mutableStateOf(detectOemDiagApp(ctx)) }
    var captureGranted by remember { mutableStateOf(com.caseforge.scanner.overlay.ScreenCaptureService.isActive()) }
    var keyDraft by remember { mutableStateOf(settings.claudeApiKey) }

    // Re-check every time the activity resumes (after user returns from a system Settings screen).
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                keyOk = settings.claudeApiKey.isNotBlank()
                accessibilityOn = ScannerAccessibilityService.instance() != null
                overlayOk = Settings.canDrawOverlays(ctx)
                oemPkg = detectOemDiagApp(ctx)
                captureGranted = com.caseforge.scanner.overlay.ScreenCaptureService.isActive()
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    val readyToContinue = keyOk && accessibilityOn && overlayOk

    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("Welcome to Together Car Works") })
        Column(
            Modifier.fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "Quick setup. Each step turns green when granted. Only the first three are required.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "How Together Car Works works",
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        "You live here, in Together Car Works. When you tap Full Scan or Start Agent, " +
                            "Together Car Works opens the OEM diagnostic app in the background and drives it for you " +
                            "via accessibility — you'll see the OEM diagnostic app on screen while the agent works, " +
                            "then results come back here. The OEM diagnostic app is the agent's hands; " +
                            "Together Car Works is your dashboard.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            WizStep(
                done = keyOk,
                title = "Claude API key",
                subtitle = if (keyOk) "Configured." else "Paste your Anthropic key (sk-ant-…).",
                actionLabel = null,
                onAction = {},
            ) {
                OutlinedTextField(
                    value = keyDraft,
                    onValueChange = {
                        keyDraft = it
                        settings.claudeApiKey = it
                        keyOk = it.isNotBlank()
                    },
                    placeholder = { Text("sk-ant-api03-…") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            WizStep(
                done = accessibilityOn,
                title = "Accessibility service",
                subtitle = if (accessibilityOn) "Enabled." else "Lets Together Car Works see and operate the OEM diagnostic app.",
                actionLabel = if (accessibilityOn) "Re-check" else "Open Accessibility Settings",
                onAction = {
                    if (accessibilityOn) {
                        // Force re-check
                        accessibilityOn = ScannerAccessibilityService.instance() != null
                    } else {
                        onOpenA11y()
                    }
                },
            )

            WizStep(
                done = overlayOk,
                title = "Display over other apps",
                subtitle = if (overlayOk) "Granted." else "Required for the floating bubble and ticker.",
                actionLabel = if (overlayOk) "Re-check" else "Grant overlay permission",
                onAction = {
                    if (overlayOk) overlayOk = Settings.canDrawOverlays(ctx)
                    else onGrantOverlay()
                },
            )

            WizStep(
                done = captureGranted,
                title = "Screen capture (optional)",
                subtitle = "Lets the agent send screenshots when accessibility text is ambiguous.",
                actionLabel = "Grant",
                onAction = { onGrantCapture() },
                optional = true,
            )

            WizStep(
                done = false,
                title = stringResource(R.string.export_wizard_title),
                subtitle = stringResource(R.string.export_wizard_subtitle),
                actionLabel = stringResource(R.string.export_wizard_action),
                onAction = onOpenDataExport,
                optional = true,
            )

            WizStep(
                done = oemPkg != null,
                title = "OEM diagnostic app installed",
                subtitle = oemPkg?.let { "Installed: $it (Together Car Works will drive this for you)." }
                    ?: "Install the OEM diagnostic app from your tablet's app store. " +
                        "Together Car Works cannot operate the VCI without it.",
                actionLabel = "Re-check",
                onAction = { oemPkg = detectOemDiagApp(ctx) },
                optional = true,
            )

            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { if (overlayOk) onStartBubble(); onFinish() },
                    enabled = readyToContinue,
                ) { Text("Open Together Car Works dashboard") }
                OutlinedButton(onClick = onFinish) { Text("Skip wizard") }
            }
            Text(
                "You won't see this wizard again. Re-open it from Setup if you need to.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )
            Text(
                BuildConfig.BUILD_INFO,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

private fun detectOemDiagApp(ctx: android.content.Context): String? {
    val pm = ctx.packageManager
    for (pkg in OEM_DIAG_PACKAGES) {
        try { pm.getPackageInfo(pkg, 0); return pkg } catch (_: PackageManager.NameNotFoundException) {}
    }
    return null
}

@Composable
private fun WizStep(
    done: Boolean,
    title: String,
    subtitle: String,
    actionLabel: String?,
    onAction: () -> Unit,
    optional: Boolean = false,
    inline: @Composable (() -> Unit)? = null,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(
                    if (done) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                    contentDescription = null,
                    tint = if (done) Color(0xFF2E7D32) else MaterialTheme.colorScheme.outline,
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        title + if (optional) "  (optional)" else "",
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(subtitle, style = MaterialTheme.typography.bodySmall)
                }
                if (actionLabel != null) {
                    OutlinedButton(onClick = onAction) { Text(actionLabel) }
                }
            }
            if (inline != null) inline()
        }
    }
}
