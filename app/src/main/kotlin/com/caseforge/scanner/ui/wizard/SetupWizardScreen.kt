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
import com.caseforge.scanner.agent.ScannerAccessibilityService
import com.caseforge.scanner.data.SettingsRepo

private val X431_PACKAGES = listOf(
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
    onFinish: () -> Unit,
) {
    val ctx = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    // All checks live in state so changes trigger recomposition.
    var keyOk by remember { mutableStateOf(settings.claudeApiKey.isNotBlank()) }
    var accessibilityOn by remember { mutableStateOf(ScannerAccessibilityService.instance() != null) }
    var overlayOk by remember { mutableStateOf(Settings.canDrawOverlays(ctx)) }
    var x431Pkg by remember { mutableStateOf(detectX431(ctx)) }
    var captureGranted by remember { mutableStateOf(com.caseforge.scanner.overlay.ScreenCaptureService.isActive()) }
    var keyDraft by remember { mutableStateOf(settings.claudeApiKey) }

    // Re-check every time the activity resumes (after user returns from a system Settings screen).
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                keyOk = settings.claudeApiKey.isNotBlank()
                accessibilityOn = ScannerAccessibilityService.instance() != null
                overlayOk = Settings.canDrawOverlays(ctx)
                x431Pkg = detectX431(ctx)
                captureGranted = com.caseforge.scanner.overlay.ScreenCaptureService.isActive()
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    val readyToContinue = keyOk && accessibilityOn && overlayOk

    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("Welcome to Launch AI") })
        Column(
            Modifier.fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "Quick setup. Each step turns green when granted. The first three are required.",
                style = MaterialTheme.typography.bodyMedium,
            )

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
                subtitle = if (accessibilityOn) "Enabled." else "Lets Launch AI see and operate the X431 app.",
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
                done = x431Pkg != null,
                title = "Launch X431 app",
                subtitle = x431Pkg?.let { "Detected: $it" } ?: "Not detected. Install your X431 diagnostic app first.",
                actionLabel = "Re-check",
                onAction = { x431Pkg = detectX431(ctx) },
            )

            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { if (overlayOk) onStartBubble(); onFinish() },
                    enabled = readyToContinue,
                ) { Text("Continue to dashboard") }
                OutlinedButton(onClick = onFinish) { Text("Skip for now") }
            }
            Text(
                BuildConfig.BUILD_INFO,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

private fun detectX431(ctx: android.content.Context): String? {
    val pm = ctx.packageManager
    for (pkg in X431_PACKAGES) {
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
