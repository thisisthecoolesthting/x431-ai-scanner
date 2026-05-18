package com.caseforge.scanner

import android.app.Application
import android.app.UsageStatsManager
import android.util.Log
import com.caseforge.scanner.data.SettingsRepo
import com.caseforge.scanner.overlay.FullScreenOverlayService
import java.io.File

/**
 * Application class entry point.
 *
 * Responsibilities:
 * 1. Register an uncaught exception handler that records overlay state before crash propagation.
 * 2. On startup, check: if overlayOnX431==true AND X431 is foreground AND service not running,
 *    relaunch FullScreenOverlayService.
 * 3. Instantiate app-level singletons (SettingsRepo, action logs, etc.)
 */
class App : Application() {
    companion object {
        const val TAG = "X431Agent.App"

        // Action log singleton; shared with FullScreenOverlayService for event logging.
        private var _actionLog: ActionLog? = null
        val actionLog: ActionLog
            get() = _actionLog ?: error("ActionLog not initialized")
    }

    private val defaultExceptionHandler = Thread.getDefaultUncaughtExceptionHandler()

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "Application.onCreate()")

        // Initialize app-level singletons.
        _actionLog = ActionLog()

        // Register uncaught exception handler.
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            recordLastKnownStateBeforeCrash(throwable)
            defaultExceptionHandler?.uncaughtException(thread, throwable)
                ?: throw throwable
        }

        // Check if overlay should be re-launched after startup.
        checkAndRestartOverlayIfNeeded()
    }

    /**
     * Records overlay state before crash propagation.
     * Writes a minimal crash state file to cacheDir so D2's BootReceiver can inspect it if needed.
     * Never throws.
     */
    private fun recordLastKnownStateBeforeCrash(throwable: Throwable) {
        runCatching {
            val crashFile = File(cacheDir, "last_crash.txt")
            val crashLog = """
                |timestamp: ${System.currentTimeMillis()}
                |exception: ${throwable.javaClass.simpleName}
                |message: ${throwable.message}
                |service_running: ${FullScreenOverlayService.isRunning}
            """.trimMargin()
            crashFile.writeText(crashLog)
            Log.i(TAG, "Crash state recorded to ${crashFile.absolutePath}")
        }.onFailure { e ->
            Log.w(TAG, "Failed to record crash state: ${e.message}")
        }
    }

    /**
     * On startup, if overlayOnX431==true, X431 is foreground, and the service is not running,
     * relaunch FullScreenOverlayService immediately.
     * Uses UsageStatsManager as a zero-permission fallback for foreground detection.
     * Never throws.
     */
    private fun checkAndRestartOverlayIfNeeded() {
        runCatching {
            val settings = SettingsRepo(this)
            if (!settings.overlayOnX431) {
                Log.d(TAG, "overlayOnX431 is false; skipping auto-restart check")
                return@runCatching
            }

            if (FullScreenOverlayService.isRunning) {
                Log.d(TAG, "FullScreenOverlayService already running; skipping auto-restart")
                return@runCatching
            }

            // Check if X431 is currently foreground using UsageStatsManager.
            val isX431Foreground = isX431ForegroundNow()
            if (!isX431Foreground) {
                Log.d(TAG, "X431 is not foreground; skipping auto-restart")
                return@runCatching
            }

            Log.i(TAG, "Conditions met: overlayOnX431=true, X431 foreground, service not running. Restarting overlay.")
            FullScreenOverlayService.start(this)
        }.onFailure { e ->
            Log.w(TAG, "Failed to check/restart overlay: ${e.message}", e)
        }
    }

    /**
     * Quick check via UsageStatsManager to see if X431 is in the foreground right now.
     * Returns false if UsageStatsManager is unavailable or the check fails.
     * Never throws.
     */
    private fun isX431ForegroundNow(): Boolean = runCatching {
        val usm = getSystemService(USAGE_STATS_SERVICE) as? UsageStatsManager ?: return@runCatching false
        val now = System.currentTimeMillis()
        val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_BEST, now - 5000, now)
        val foregroundPkg = stats.maxByOrNull { it.lastTimeUsed }?.packageName
        foregroundPkg == "com.launch.x431"
    }.getOrNull() ?: false
}

/**
 * Placeholder for action logging infrastructure.
 * In a full implementation, this would write structured events to SharedPreferences or a database.
 * For D2, we just provide the interface so dispatchCapability() can call event().
 */
class ActionLog {
    fun event(action: String, details: String) {
        Log.d("X431Agent.ActionLog", "$action: $details")
    }
}
