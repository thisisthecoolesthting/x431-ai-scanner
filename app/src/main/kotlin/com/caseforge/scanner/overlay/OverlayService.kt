package com.caseforge.scanner.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.Toast
import com.caseforge.scanner.App
import com.caseforge.scanner.agent.AgentRunner
import com.caseforge.scanner.ai.ClaudeClient
import com.caseforge.scanner.data.SessionEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Foreground service that shows a draggable bubble over the X431 app.
 * Tap = start an agent session against whatever the X431 app is showing right now.
 * Tap again while the agent is running = stop it (kill-switch).
 */
class OverlayService : Service() {

    companion object {
        private const val CHANNEL_ID = "overlay"
        private const val NOTIFICATION_ID = 1002
    }

    private lateinit var wm: WindowManager
    private var bubble: ImageView? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var currentJob: Job? = null
    private val ui = Handler(Looper.getMainLooper())

    private val app: App get() = applicationContext as App

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, notif("CaseForge bubble running — tap to triage"))
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        addBubble()
    }

    override fun onDestroy() {
        currentJob?.cancel()
        scope.cancel()
        bubble?.let { runCatching { wm.removeView(it) } }
        bubble = null
        super.onDestroy()
    }

    private fun addBubble() {
        val iv = ImageView(this).apply {
            setImageResource(android.R.drawable.ic_menu_search)
            setBackgroundResource(android.R.drawable.dialog_holo_light_frame)
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 40
            y = 240
        }

        var downX = 0f; var downY = 0f
        var startX = 0; var startY = 0
        iv.setOnTouchListener { v, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX; downY = e.rawY
                    startX = params.x; startY = params.y
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = startX + (e.rawX - downX).toInt()
                    params.y = startY + (e.rawY - downY).toInt()
                    wm.updateViewLayout(v, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val moved = kotlin.math.hypot(
                        (e.rawX - downX).toDouble(),
                        (e.rawY - downY).toDouble()
                    )
                    if (moved < 10) onBubbleTap()
                    true
                }
                else -> false
            }
        }

        wm.addView(iv, params)
        bubble = iv
    }

    /** Click handler: start agent if idle, stop it if already running. */
    private fun onBubbleTap() {
        val running = currentJob?.isActive == true
        if (running) {
            currentJob?.cancel()
            currentJob = null
            setBubbleIdle()
            toast("Agent stopped.")
            app.actionLog.event("bubble.stop", "user tapped bubble while running")
            return
        }

        val settings = app.settings
        val key = settings.claudeApiKey
        if (key.isBlank()) {
            toast("Set a Claude API key in Settings first.")
            return
        }
        if (settings.killSwitch) {
            toast("Kill switch is on — disable it in Settings.")
            return
        }

        setBubbleRunning()
        toast("Agent starting…")
        app.actionLog.event("bubble.start", "user tapped bubble")

        currentJob = scope.launch {
            try {
                val client = ClaudeClient(apiKey = key, model = settings.model)
                val runner = AgentRunner(
                    claude = client,
                    log = app.actionLog,
                    screenshot = {
                        val base64 = ScreenCaptureService.captureJpegBase64()
                        if (base64 != null) AgentRunner.ImagePayload("image/jpeg", base64) else null
                    },
                    requireApproval = settings.requireApproval,
                )
                val started = System.currentTimeMillis()
                val outcome = runner.run(vin = null, symptom = null)
                val summary = outcome.summary
                app.db.sessionDao().insertSession(
                    SessionEntity(
                        vin = null,
                        startedAt = started,
                        endedAt = System.currentTimeMillis(),
                        symptom = null,
                        rootCause = summary?.get("root_cause")?.toString()?.trim('"'),
                        recommendedRepair = summary?.get("recommended_repair")?.toString()?.trim('"'),
                        transcriptJson = "",
                    )
                )
                ui.post {
                    setBubbleIdle()
                    toast(
                        if (outcome.finished) "Agent finished — see History."
                        else "Agent stopped: ${outcome.stoppedReason}"
                    )
                }
                app.actionLog.event("bubble.done", "reason=${outcome.stoppedReason}")
            } catch (t: Throwable) {
                app.actionLog.event("bubble.error", t.message.orEmpty())
                ui.post {
                    setBubbleIdle()
                    toast("Agent error: ${t.message?.take(80) ?: "unknown"}")
                }
            }
        }
    }

    private fun setBubbleRunning() {
        ui.post { bubble?.setImageResource(android.R.drawable.ic_popup_sync) }
    }

    private fun setBubbleIdle() {
        ui.post { bubble?.setImageResource(android.R.drawable.ic_menu_search) }
    }

    private fun toast(msg: String) {
        ui.post { Toast.makeText(this, msg, Toast.LENGTH_SHORT).show() }
    }

    private fun notif(text: String): Notification {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Overlay bubble", NotificationManager.IMPORTANCE_MIN)
            )
        }
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("CaseForge Scanner AI")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_search)
            .setOngoing(true)
            .build()
    }
}
