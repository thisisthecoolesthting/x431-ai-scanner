package com.caseforge.scanner

import android.app.Application
import android.app.usage.UsageStatsManager
import android.util.Log
import com.caseforge.scanner.agent.AgentActionLog
import com.caseforge.scanner.agent.AgentStatus
import com.caseforge.scanner.agent.AgentTts
import com.caseforge.scanner.data.AppDatabase
import com.caseforge.scanner.data.SettingsRepo
import com.caseforge.scanner.overlay.FullScreenOverlayService
import com.caseforge.scanner.vci.OemVehicleAssetIndex
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

        fun isOemDiagForeground(context: android.content.Context): Boolean {
            return runCatching {
                val usm = context.getSystemService(USAGE_STATS_SERVICE) as? UsageStatsManager
                    ?: return@runCatching false
                val now = System.currentTimeMillis()
                val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_BEST, now - 5000, now)
                val foregroundPkg = stats.maxByOrNull { it.lastTimeUsed }?.packageName
                foregroundPkg in OEM_DIAG_PACKAGES
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

        scope.launch {
            AgentStatus.activity.collect { msg ->
                if (settings.speakEnabled && msg.isNotBlank()) tts.speak(msg)
            }
        }

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            recordLastKnownStateBeforeCrash(throwable)
            defaultExceptionHandler?.uncaughtException(thread, throwable)
                ?: throw throwable
        }

        checkAndRestartOverlayIfNeeded()
    }

    private fun recordLastKnownStateBeforeCrash(throwable: Throwable) {
        runCatching {
            val crashFile = File(cacheDir, "last_crash.txt")
            val crashLog = "timestamp: " + System.currentTimeMillis() + "\n" +
                "exception: " + throwable.javaClass.simpleName + "\n" +
                "message: " + (throwable.message ?: "") + "\n" +
                "service_running: " + FullScreenOverlayService.isRunning + "\n"
            crashFile.writeText(crashLog)
            Log.i(TAG, "Crash state recorded to " + crashFile.absolutePath)
        }.onFailure { e ->
            Log.w(TAG, "Failed to record crash state: " + e.message)
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
