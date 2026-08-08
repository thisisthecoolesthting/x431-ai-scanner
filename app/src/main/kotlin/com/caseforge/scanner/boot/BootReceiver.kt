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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext

/**
 * Registers for android.intent.action.BOOT_COMPLETED.
 * On boot, if [SettingsRepo.overlayOnOemDiag] is true, schedules a one-shot foreground-detection
 * listener that re-launches [FullScreenOverlayService] when the OEM diagnostic app comes forward.
 */
class BootReceiver : BroadcastReceiver() {
    companion object {
        const val TAG = "TcwAgent.BootReceiver"
        private const val CHECK_INTERVAL_MS = 500L
        private const val MAX_WAIT_MS = 5000L
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent?.action != Intent.ACTION_BOOT_COMPLETED) return

        Log.i(TAG, "Boot completed broadcast received")
        val settings = SettingsRepo(context)
        if (!settings.overlayOnOemDiag) {
            Log.i(TAG, "overlayOnOemDiag is false; skipping auto-launch")
            return
        }

        Log.i(TAG, "overlayOnOemDiag is true; scheduling foreground-detection listener")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        scope.launch {
            waitForOemDiagForeground(context, maxWaitMs = MAX_WAIT_MS)
        }
    }

    private suspend fun waitForOemDiagForeground(
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
        val oemPkgs = ScannerAccessibilityService.OEM_DIAG_PACKAGES

        while (System.currentTimeMillis() - startTime < maxWaitMs) {
            if (!coroutineContext.isActive) return

            val now = System.currentTimeMillis()
            val stats = usm.queryUsageStats(
                UsageStatsManager.INTERVAL_BEST,
                startTime,
                now,
            )
            val foregroundPkg = stats.maxByOrNull { it.lastTimeUsed }?.packageName

            if (foregroundPkg != null && foregroundPkg in oemPkgs) {
                Log.i(TAG, "OEM diagnostic app foreground; launching FullScreenOverlayService")
                FullScreenOverlayService.start(context)
                return
            }

            val nowMs = System.currentTimeMillis()
            if (nowMs - lastLogTime > 2000) {
                Log.d(TAG, "OEM diagnostic app not yet foreground, waiting...")
                lastLogTime = nowMs
            }

            delay(CHECK_INTERVAL_MS)
        }

        Log.w(TAG, "Timeout waiting for OEM diagnostic app foreground; aborting auto-launch")
    }
}
