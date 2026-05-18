package com.caseforge.scanner.boot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

/**
 * BroadcastReceiver that starts the OverlayService after device boot.
 * Listens for ACTION_BOOT_COMPLETED and launches the floating bubble.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val serviceIntent = Intent(context, Class.forName("com.caseforge.scanner.overlay.OverlayService"))
            ContextCompat.startForegroundService(context, serviceIntent)
        }
    }
}
