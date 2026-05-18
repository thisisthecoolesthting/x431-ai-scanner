@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.caseforge.scanner

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.caseforge.scanner.agent.AgentRunner
import com.caseforge.scanner.agent.ScannerAccessibilityService
import com.caseforge.scanner.ai.ClaudeClient
import com.caseforge.scanner.ai.Prompts
import com.caseforge.scanner.overlay.OverlayService
import com.caseforge.scanner.overlay.ScreenCaptureService
import com.caseforge.scanner.data.DtcEntity
import com.caseforge.scanner.data.SessionEntity
import com.caseforge.scanner.ui.fullscan.FullScanResultsScreen
import com.caseforge.scanner.ui.history.HistoryScreen
import com.caseforge.scanner.ui.home.HomeScreen
import com.caseforge.scanner.ui.pending.PendingApprovalsScreen
import com.caseforge.scanner.ui.settings.SettingsScreen
import com.caseforge.scanner.ui.theme.CaseForgeTheme
import com.caseforge.scanner.ui.triage.TriageScreen
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    companion object {
        const val EXTRA_REPORT_TEXT = "report_text"
        const val EXTRA_REPORT_SOURCE = "report_source"
    }

    private val app: App by lazy { application as App }

    private val mediaProjectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val svc = Intent(this, ScreenCaptureService::class.java).apply {
                putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, result.resultCode)
                putExtra(ScreenCaptureService.EXTRA_RESULT_DATA, result.data)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(svc)
            else startService(svc)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // If launched via the share-target, jump straight into triage.
        val sharedReport = intent.getStringExtra(EXTRA_REPORT_TEXT)

        // Wire VIN auto-start once.
        ScannerAccessibilityService.onVinDetected = { vin ->
            if (app.settings.autoStartOnVin && !app.settings.killSwitch) {
                lifecycleScope.launch { runAgent(vin = vin, symptom = null) }
            }
        }

        setContent {
            CaseForgeTheme {
                var route by remember { mutableStateOf(if (sharedReport != null) "triage" else "home") }
                var triageInput by remember { mutableStateOf(sharedReport.orEmpty()) }
                var triageOutput by remember { mutableStateOf("") }
                var busy by remember { mutableStateOf(false) }

                Scaffold(topBar = { TopAppBar(title = { Text("CaseForge Scanner AI") }) }) { pad ->
                    Box(Modifier.padding(pad).fillMaxSize()) {
                        when (route) {
                            "home" -> HomeScreen(
                                onOpenSettings = { route = "settings" },
                                onStartBubble = { startBubble() },
                                onGrantCapture = { requestProjection() },
                                onOpenA11y = { openAccessibilitySettings() },
                                onManualTriage = {
                                    route = "triage"
                                    triageInput = ""; triageOutput = ""
                                },
                                onRunAgentNow = {
                                    lifecycleScope.launch { runAgent(vin = null, symptom = null) }
                                },
                                onRunFullScan = {
                                    lifecycleScope.launch {
                                        runFullScan(vin = null)
                                        route = "fullscan_results"
                                    }
                                },
                                onOpenHistory = { route = "history" },
                                onOpenApprovals = { route = "approvals" },
                            )
                            "settings" -> SettingsScreen(
                                settings = app.settings,
                                onBack = { route = "home" },
                            )
                            "history" -> HistoryScreen(db = app.db, onBack = { route = "home" })
                            "approvals" -> PendingApprovalsScreen(onBack = { route = "home" })
                            "fullscan_results" -> FullScanResultsScreen(
                                db = app.db,
                                onBack = { route = "home" },
                            )
                            "triage" -> TriageScreen(
                                initialText = triageInput,
                                output = triageOutput,
                                busy = busy,
                                onRun = { text ->
                                    busy = true
                                    lifecycleScope.launch {
                                        val out = runReportTriage(text)
                                        triageOutput = out
                                        busy = false
                                    }
                                },
                                onBack = { route = "home" },
                            )
                        }
                    }
                }
            }
        }
    }

    private fun startBubble() {
        val canDraw = Settings.canDrawOverlays(this)
        if (!canDraw) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:$packageName")))
            return
        }
        val svc = Intent(this, OverlayService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(svc) else startService(svc)
    }

    private fun requestProjection() {
        val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjectionLauncher.launch(mpm.createScreenCaptureIntent())
    }

    private fun openAccessibilitySettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    private suspend fun runReportTriage(reportText: String): String {
        val key = app.settings.claudeApiKey
        if (key.isBlank()) return "Set a Claude API key in Settings first."
        return withContext(Dispatchers.IO) {
            try {
                val client = ClaudeClient(apiKey = key, model = app.settings.model)
                val resp = client.sendMessages(
                    system = Prompts.DTC_TRIAGE_FROM_REPORT,
                    messages = listOf(ClaudeClient.userText(reportText)),
                    maxTokens = 2048,
                )
                resp.firstText().orEmpty()
            } catch (t: Throwable) {
                "Error: ${t.message}"
            }
        }
    }

    private suspend fun runAgent(vin: String?, symptom: String?) {
        runAgentSession(vin = vin, symptom = symptom, scope = "diagnostic")
    }

    /** One-tap full-scan-all-modules entry point. Pipes a sentinel symptom that
     *  [Prompts.agentGoal] recognises and rewrites into the full-scan goal text. */
    private suspend fun runFullScan(vin: String?) {
        runAgentSession(
            vin = vin,
            symptom = Prompts.FULL_SCAN_SENTINEL,
            scope = "fullscan",
        )
    }

    private suspend fun runAgentSession(vin: String?, symptom: String?, scope: String) {
        val key = app.settings.claudeApiKey
        if (key.isBlank()) return
        if (app.settings.killSwitch) return
        withContext(Dispatchers.IO) {
            val client = ClaudeClient(apiKey = key, model = app.settings.model)
            val runner = AgentRunner(
                claude = client,
                log = app.actionLog,
                screenshot = {
                    val base64 = ScreenCaptureService.captureJpegBase64()
                    if (base64 != null) AgentRunner.ImagePayload("image/jpeg", base64) else null
                },
                requireApproval = app.settings.requireApproval,
            )
            val started = System.currentTimeMillis()
            val outcome = runner.run(vin = vin, symptom = symptom)
            // Persist
            val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
            val summary = outcome.summary
            // Symptom column stores null for the full-scan sentinel — it's not a real symptom.
            val symptomToPersist = if (symptom == Prompts.FULL_SCAN_SENTINEL) null else symptom
            val sessionId = app.db.sessionDao().insertSession(
                SessionEntity(
                    vin = vin,
                    startedAt = started,
                    endedAt = System.currentTimeMillis(),
                    symptom = symptomToPersist,
                    rootCause = jsonStringOrNull(summary, "root_cause"),
                    recommendedRepair = jsonStringOrNull(summary, "recommended_repair"),
                    transcriptJson = json.encodeToString(
                        kotlinx.serialization.builtins.ListSerializer(ClaudeClient.Message.serializer()),
                        outcome.transcript
                    ),
                    scope = scope,
                )
            )
            // Persist DTCs from the finish_session payload so FullScanResultsScreen can show them.
            extractDtcs(summary).forEach { dtc ->
                app.db.sessionDao().insertDtc(dtc.copy(sessionId = sessionId))
            }
            app.actionLog.event("session.persisted", "id=$sessionId scope=$scope reason=${outcome.stoppedReason}")
        }
    }

    /** Read a string field from a Claude finish_session JsonObject, or null if absent/empty. */
    private fun jsonStringOrNull(obj: JsonObject?, key: String): String? {
        val el = obj?.get(key) ?: return null
        val s = (el as? JsonPrimitive)?.contentOrNullSafe ?: return null
        return s.ifBlank { null }
    }

    /** Pulls dtcs_found from the finish_session summary into transient DtcEntities (sessionId=0). */
    private fun extractDtcs(summary: JsonObject?): List<DtcEntity> {
        val arr = (summary?.get("dtcs_found") as? JsonArray) ?: return emptyList()
        return arr.mapNotNull { el ->
            val obj = (el as? JsonObject) ?: return@mapNotNull null
            val code = (obj["code"] as? JsonPrimitive)?.contentOrNullSafe?.takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            DtcEntity(
                sessionId = 0L, // set when inserted
                code = code,
                module = (obj["module"] as? JsonPrimitive)?.contentOrNullSafe,
                description = (obj["description"] as? JsonPrimitive)?.contentOrNullSafe,
                status = (obj["status"] as? JsonPrimitive)?.contentOrNullSafe,
            )
        }
    }
}

private val JsonPrimitive.contentOrNullSafe: String?
    get() = try { if (this is kotlinx.serialization.json.JsonNull) null else content } catch (_: Throwable) { null }
