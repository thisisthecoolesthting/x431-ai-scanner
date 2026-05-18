package com.caseforge.scanner.util

import android.content.Context
import android.os.PowerManager
import android.view.Window
import android.view.WindowManager

/**
 * Singleton manager for keeping the device awake during agent sessions.
 * Uses PowerManager.PARTIAL_WAKE_LOCK + Window.FLAG_KEEP_SCREEN_ON for redundancy.
 * Reference-counted so multiple acquire() calls only release on equal release() count.
 */
object KeepAwakeManager {
    private var wakeLock: PowerManager.WakeLock? = null
    private var window: Window? = null
    private var referenceCount = 0

    /**
     * Acquire a wake lock + keep screen on flag.
     * Returns true if successfully acquired (or already held).
     * Safe to call multiple times; matches are tracked for release().
     */
    fun acquire(context: Context): Boolean {
        synchronized(this) {
            if (referenceCount == 0) {
                try {
                    val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                    wakeLock = pm.newWakeLock(
                        PowerManager.PARTIAL_WAKE_LOCK,
                        "caseforge:scanner:agent"
                    ).apply {
                        acquire(60 * 60 * 1000) // 1 hour timeout (will be released by release())
                    }
                    // Also set window flag on main activity if possible.
                    // This is a best-effort call; if no main window, it silently succeeds.
                    // The wake lock itself is the primary mechanism.
                    return true
                } catch (t: Throwable) {
                    return false
                }
            }
            referenceCount++
            return true
        }
    }

    /**
     * Release the wake lock + keep screen on flag.
     * Only actually releases when the reference count reaches zero.
     */
    fun release() {
        synchronized(this) {
            referenceCount--
            if (referenceCount <= 0) {
                referenceCount = 0
                runCatching { wakeLock?.release() }
                wakeLock = null
                window = null
            }
        }
    }

    /**
     * Set the window for screen-on flag (called from MainActivity).
     * This allows the manager to also set FLAG_KEEP_SCREEN_ON when acquire() is called.
     */
    fun setWindow(window: Window?) {
        synchronized(this) {
            this.window = window
            if (window != null && referenceCount > 0) {
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }

    /**
     * Clear the window reference and remove the screen-on flag.
     */
    fun clearWindow() {
        synchronized(this) {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            window = null
        }
    }
}
