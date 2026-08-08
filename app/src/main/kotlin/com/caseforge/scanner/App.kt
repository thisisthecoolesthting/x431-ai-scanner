package com.caseforge.scanner

import android.app.Application
import android.app.usage.UsageStatsManager
import android.util.Log
import com.caseforge.scanner.agent.AgentActionLog
import com.caseforge.scanner.agent.AgentStatus
import com.caseforge.scanner.agent.AgentTts
import com.caseforge.scanner.data.AppDatabase
import com.caseforge.scanner.data.SettingsRepo
import com.caseforge.scanner.oem.OemDataIndex
import com.caseforge.scanner.oem.OemDecompileBundleLoader
import com.caseforge.scanner.oem.OemEngineFacade
import com.caseforge.scanner.overlay.FullScreenOverlayService
import com.caseforge.scanner.vci.OemVehicleAssetIndex
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

class App : Application() {

    companion object {
        const val TAG = "TcwAgent.App"

        private val OEM_DIAG_PACKAGES = setOf(
            "com.cnlaunch.x431padv",
            "com.cnlaunch.x431pro",
            "com.cnlaunch.x431pro3",
            "com.cnlaunch.x431padv2",
        )

        /** Last reason [isOemDiagForeground] blocked a connect attempt (for diagnostics UI). */
        @Volatile
        var lastOemForegroundBlockReason: String? = null
            private set

        fun isOemDiagForeground(context: android.content.Context): Boolean {
            return runCatching {
                val usm = context.getSystemService(USAGE_STATS_SERVICE) as? UsageStatsManager
                if (usm == null) {
                    lastOemForegroundBlockReason =
                        "UsageStatsManager unavailable — OEM foreground check skipped"
                    Log.w(TAG, "isOemDiagForeground: UsageStatsManager unavailable — treating as not foreground")
                    return@runCatching false
                }
                val now = System.currentTimeMillis()
                val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_BEST, now - 5000, now)
                if (stats.isNullOrEmpty()) {
                    lastOemForegroundBlockReason =
                        "No usage stats (PACKAGE_USAGE_STATS may be denied) — OEM foreground check skipped"
                    Log.i(
                        TAG,
                        "isOemDiagForeground: no usage stats (PACKAGE_USAGE_STATS may be denied) — check skipped",
                    )
                    return@runCatching false
                }
                val foregroundPkg = stats.maxByOrNull { it.lastTimeUsed }?.packageName
                val blocked = foregroundPkg in OEM_DIAG_PACKAGES
                if (blocked) {
                    lastOemForegroundBlockReason =
                        "OEM diagnostic app foreground ($foregroundPkg) — force-stop to free VCI"
                    Log.w(
                        TAG,
                        "isOemDiagForeground: OEM diagnostic app foreground ($foregroundPkg) — connect blocked",
                    )
                    (context.applicationContext as? App)?.settings?.recordOemDiagConnectBlock(
                        lastOemForegroundBlockReason!!,
                    )
                } else {
                    lastOemForegroundBlockReason = null
                }
                blocked
            }.getOrDefault(false)
        }
    }

    lateinit var settings: SettingsRepo
        private set
    lateinit var actionLog: AgentActionLog
        private set
    lateinit var db: AppDatabase
        private set
    lateinit var tts: AgentTts
        private set

    /** Plan B OBD wedge — shared instance for startup + Settings UI. */
    private val oemEngineFacadeLazy by lazy { OemEngineFacade(this, settings) }

    fun oemEngineFacade(): OemEngineFacade = oemEngineFacadeLazy

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val defaultExceptionHandler = Thread.getDefaultUncaughtExceptionHandler()

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "Application.onCreate()")

        settings = SettingsRepo(this)
        OemVehicleAssetIndex.load(this)
        actionLog = AgentActionLog(this)
        AgentStatus.startObservingActionLog(actionLog, scope)
        db = AppDatabase.get(this)
        tts = AgentTts(this)
        com.caseforge.scanner.agent.AcousticTool.attach(this)
        com.caseforge.scanner.agent.CostTracker.loadLifetime(this)

        scope.launch(Dispatchers.IO) {
            OemDecompileBundleLoader.load(this@App)
            OemDataIndex.scanWithBundle(this@App)
        }

        ProcessLifecycleOwner.get().lifecycleScope.launch(Dispatchers.IO) {
            // Tier snapshot for Plan B safety is applied inside [OemEngineFacade] on transport connect.
            if (settings.nativeObdExperimental) {
                oemEngineFacade().refreshSuspend(preserveConnection = false)
            }
        }

        scope.launch {
            AgentStatus.activity.collect { msg ->
                if (settings.speakEnabled && msg.isNotBlank()) tts.speak(msg)
            }
        }

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            recordLastKnownStateBeforeCrash(thread, throwable)
            defaultExceptionHandler?.uncaughtException(thread, throwable)
                ?: throw throwable
        }

        checkAndRestartOverlayIfNeeded()
    }

    private fun recordLastKnownStateBeforeCrash(thread: Thread, throwable: Throwable) {
        runCatching {
            val crashFile = File(cacheDir, "last_crash.txt")
            val stack = throwable.stackTraceToString().take(8_000)
            val crashLog = buildString {
                appendLine("timestamp: ${System.currentTimeMillis()}")
                appendLine("thread: ${thread.name} (id=${thread.id})")
                appendLine("exception: ${throwable.javaClass.name}")
                appendLine("message: ${throwable.message ?: ""}")
                appendLine("service_running: ${FullScreenOverlayService.isRunning}")
                appendLine("stack:")
                append(stack)
            }
            crashFile.writeText(crashLog)
            Log.i(TAG, "Crash state recorded to ${crashFile.absolutePath}")
        }.onFailure { e ->
            Log.w(TAG, "Failed to record crash state: ${e.message}")
        }
    }

    private fun checkAndRestartOverlayIfNeeded() {
        runCatching {
            if (settings.directVciExperimental) return@runCatching
            if (!settings.overlayOnOemDiag) return@runCatching
            if (FullScreenOverlayService.isRunning) return@runCatching
            if (!isOemDiagForegroundNow()) return@runCatching
            Log.i(TAG, "Conditions met: restarting overlay.")
            FullScreenOverlayService.start(this)
        }.onFailure { e ->
            Log.w(TAG, "Failed to check/restart overlay: " + e.message, e)
        }
    }

    private fun isOemDiagForegroundNow(): Boolean = isOemDiagForeground(this)
}
