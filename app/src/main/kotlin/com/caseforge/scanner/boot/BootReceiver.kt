package com.caseforge.scanner.boot

import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.caseforge.scanner.agent.ScannerAccessibilityService
import com.caseforge.scanner.data.SettingsRepo
import com.caseforge.scanner.overlay.FullScreenOverlayService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.launch

/**
 * Registers for android.intent.action.BOOT_COMPLETED.
 * On boot, if SettingsRepo.overlayOnX431 == true, schedules a one-shot foreground-detection
 * listener that re-launches FullScreenOverlayService within 5s of X431 next coming forward.
 *
 * This ensures the overlay service survives device reboots when auto-launch is enabled.
 */
class BootReceiver : BroadcastReceiver() {
    companion object {
        const val TAG = "X431Agent.BootReceiver"
        private const val X431_PKG = "com.launch.x431"
        private const val CHECK_INTERVAL_MS = 500L
        private const val MAX_WAIT_MS = 5000L
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent?.action != Intent.ACTION_BOOT_COMPLETED) return

        Log.i(TAG, "Boot completed broadcast received")
        val settings = SettingsRepo(context)
        if (settings.overlayOnX431 != true) {
            Log.i(TAG, "overlayOnX431 is false; skipping auto-launch")
            return
        }

        Log.i(TAG, "overlayOnX431 is true; scheduling foreground-detection listener")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        scope.launch {
            waitForX431Foreground(context, maxWaitMs = MAX_WAIT_MS)
        }
    }

    /**
     * Polls UsageStatsManager every CHECK_INTERVAL_MS until X431 is detected as foreground
     * (or timeout). Then immediately launches FullScreenOverlayService.
     */
    private suspend fun waitForX431Foreground(
        context: Context,
        maxWaitMs: Long,
    ) {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
        if (usm == null) {
            Log.w(TAG, "UsageStatsManager not available")
            return
        }

        val startTime = System.currentTimeMillis()
        var lastLogTime = startTime

        while (System.currentTimeMillis() - startTime < maxWaitMs) {
            if (!coroutineContext.isActive) return@launch

            val now = System.currentTimeMillis()
            val stats = usm.queryUsageStats(
                UsageStatsManager.INTERVAL_BEST,
                startTime,
                now
            )
            val foregroundPkg = stats.maxByOrNull { it.lastTimeUsed }?.packageName

            if (foregroundPkg == X431_PKG) {
                Log.i(TAG, "X431 detected as foreground; launching FullScreenOverlayService")
                FullScreenOverlayService.start(context)
                return
            }

            val nowMs = System.currentTimeMillis()
            if (nowMs - lastLogTime > 2000) {
                Log.d(TAG, "X431 not yet foreground, waiting...")
                lastLogTime = nowMs
            }

            delay(CHECK_INTERVAL_MS)
        }

        Log.w(TAG, "Timeout waiting for X431 to come foreground; aborting auto-launch")
    }
}
