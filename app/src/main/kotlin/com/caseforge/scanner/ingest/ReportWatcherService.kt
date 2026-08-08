package com.caseforge.scanner.ingest

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.os.FileObserver
import android.os.IBinder
import android.util.Log
import java.io.File

/**
 * Foreground service that watches the OEM diagnostic reports directory and notifies the user when a new
 * PDF report drops. The OEM diagnostic app writes reports here on most versions; we walk a small set of
 * known paths and watch whichever exist.
 *
 * Heads-up: on API 30+ shared storage is locked down. For paths under /Android/data/<oem_pkg>/...
 * you'll likely need either MANAGE_EXTERNAL_STORAGE or the user to use the share-target flow
 * instead. The watcher remains useful for non-scoped paths and devices that pre-date scoped storage.
 */
class ReportWatcherService : Service() {

    companion object {
        private const val CHANNEL_ID = "report_watcher"
        private const val NOTIFICATION_ID = 1001
        private val CANDIDATE_DIRS = listOf(
            "oem-vehicle-db/OEMPRO/Diagnostic",
            "oem-vehicle-db/Diagnostic",
            "oem-vehicle-db/OEMPAD/Diagnostic",
        )
    }

    private val observers = mutableListOf<FileObserver>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, buildNotification("Watching for OEM diagnostic reports…"))
        startObservers()
    }

    override fun onDestroy() {
        observers.forEach { it.stopWatching() }
        observers.clear()
        super.onDestroy()
    }

    private fun startObservers() {
        val storage = Environment.getExternalStorageDirectory()
        for (rel in CANDIDATE_DIRS) {
            val dir = File(storage, rel)
            if (!dir.exists()) continue
            Log.i("ReportWatcher", "Watching ${dir.absolutePath}")
            val obs = recursiveObserver(dir)
            obs.startWatching()
            observers.add(obs)
        }
    }

    private fun recursiveObserver(dir: File): FileObserver {
        @Suppress("DEPRECATION")
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            object : FileObserver(dir, CREATE or CLOSE_WRITE) {
                override fun onEvent(event: Int, path: String?) { handleEvent(dir, path) }
            }
        } else {
            object : FileObserver(dir.absolutePath, CREATE or CLOSE_WRITE) {
                override fun onEvent(event: Int, path: String?) { handleEvent(dir, path) }
            }
        }
    }

    private fun handleEvent(dir: File, path: String?) {
        val name = path ?: return
        if (!name.endsWith(".pdf", ignoreCase = true)) return
        val file = File(dir, name)
        if (!file.exists()) return
        // Post a notification the tech can tap to triage the new report.
        notify("New OEM diagnostic app report", file.name)
    }

    private fun buildNotification(text: String): Notification {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Report watcher", NotificationManager.IMPORTANCE_LOW)
            )
        }
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Together Car Works")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setOngoing(true)
            .build()
    }

    private fun notify(title: String, text: String) {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val n = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setAutoCancel(true)
            .build()
        nm.notify((System.currentTimeMillis() and 0xFFFF).toInt(), n)
    }
}
