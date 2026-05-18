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
import com.caseforge.scanner.vci.CnlaunchAssetIndex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

class App : Application() {

    companion object {
        const val TAG = "X431Agent.App"

        private val X431_PACKAGES = setOf(
            "com.cnlaunch.x431padv",
            "com.cnlaunch.x431pro",
            "com.cnlaunch.x431pro3",
            "com.cnlaunch.x431padv2"
        )
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
        CnlaunchAssetIndex.load(this)
        actionLog = AgentActionLog(this)
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
            if (!settings.overlayOnX431) return@runCatching
            if (FullScreenOverlayService.isRunning) return@runCatching
            if (!isX431ForegroundNow()) return@runCatching
            Log.i(TAG, "Conditions met: restarting overlay.")
            FullScreenOverlayService.start(this)
        }.onFailure { e ->
            Log.w(TAG, "Failed to check/restart overlay: " + e.message, e)
        }
    }

    private fun isX431ForegroundNow(): Boolean = runCatching {
        val usm = getSystemService(USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return@runCatching false
        val now = System.currentTimeMillis()
        val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_BEST, now - 5000, now)
        val foregroundPkg = stats.maxByOrNull { it.lastTimeUsed }?.packageName
        foregroundPkg in X431_PACKAGES
    }.getOrNull() ?: false
}