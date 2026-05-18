package com.caseforge.scanner.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.caseforge.scanner.App
import com.caseforge.scanner.agent.ScannerAccessibilityService
import com.caseforge.scanner.engine.EngineScraper
import com.caseforge.scanner.engine.EngineState
import com.caseforge.scanner.overlay.compose.OverlayRoot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Full-screen overlay that hides the X431 engine app behind Launch AI's custom UI.
 *
 * Phase-1 architecture:
 *   - X431 stays foreground (Android requires that for accessibility to operate it)
 *   - This service draws a MATCH_PARENT overlay window on top covering the user's view
 *   - Inside the overlay we host a ComposeView showing our own dashboard / scan / live-data screens
 *   - EngineScraper polls the accessibility tree → EngineState → our Compose UI reacts
 *   - User taps in our UI → ScannerAccessibilityService dispatches the corresponding X431 taps
 *
 * Escape hatches:
 *   - "Peek" button fades overlay to 30% opacity so the tech can verify X431 underneath
 *   - "Minimize" collapses the overlay to a bubble (legacy OverlayService bubble)
 *   - Foreground-service notification action "Dismiss overlay" for emergency exit
 */
class FullScreenOverlayService : Service(),
    LifecycleOwner,
    ViewModelStoreOwner,
    SavedStateRegistryOwner {

    companion object {
        const val TAG = "X431Agent.FullOverlay"
        private const val CHANNEL_ID = "fullscreen_overlay"
        private const val NOTIFICATION_ID = 1003

        const val ACTION_START = "com.caseforge.scanner.overlay.START_FULLSCREEN"
        const val ACTION_STOP = "com.caseforge.scanner.overlay.STOP_FULLSCREEN"
        const val ACTION_PEEK = "com.caseforge.scanner.overlay.PEEK"

        fun start(ctx: Context) {
            val i = Intent(ctx, FullScreenOverlayService::class.java).setAction(ACTION_START)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ctx.startForegroundService(i)
            else ctx.startService(i)
        }

        fun stop(ctx: Context) {
            ctx.startService(Intent(ctx, FullScreenOverlayService::class.java).setAction(ACTION_STOP))
        }
    }

    private lateinit var wm: WindowManager
    private var rootView: View? = null
    private var params: WindowManager.LayoutParams? = null

    // Lifecycle plumbing for Compose hosted inside a Service
    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry
    private val vmStore = ViewModelStore()
    override val viewModelStore: ViewModelStore get() = vmStore
    private val savedStateController = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var scraperJob: Job? = null

    private val ui = Handler(Looper.getMainLooper())
    private val engineState = mutableStateOf(EngineState.EMPTY)
    private val peekModeAlpha = mutableStateOf(1.0f) // 1.0 = fully opaque, 0.3 = peek

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        savedStateController.performAttach()
        savedStateController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                Log.i(TAG, "Stopping full-screen overlay")
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_PEEK -> {
                peekModeAlpha.value = if (peekModeAlpha.value > 0.5f) 0.0f else 1.0f
                applyAlpha()
                return START_STICKY
            }
        }

        startForeground(NOTIFICATION_ID, buildNotification())
        if (rootView == null) attachOverlay()
        startScraperLoop()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        return START_STICKY
    }

    override fun onDestroy() {
        scraperJob?.cancel()
        scope.cancel()
        rootView?.let { runCatching { wm.removeView(it) } }
        rootView = null
        params = null
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        super.onDestroy()
    }

    // ---------- overlay window ----------

    private fun attachOverlay() {
        val composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@FullScreenOverlayService)
            setViewTreeViewModelStoreOwner(this@FullScreenOverlayService)
            setViewTreeSavedStateRegistryOwner(this@FullScreenOverlayService)
            setBackgroundColor(Color.argb(245, 12, 14, 18)) // near-black, slightly translucent
            setContent {
                OverlayRoot(
                    engineState = engineState.value,
                    alpha = peekModeAlpha.value,
                    onMinimize = { minimizeToBubble() },
                    onDismiss = { stopSelf() },
                    onPeek = { togglePeek() },
                    onCapability = { id -> dispatchCapability(id) },
                )
            }
        }

        val p = WindowManager.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            // FLAG_NOT_FOCUSABLE keeps IME away unless we explicitly request focus.
            // ALT_FOCUSABLE_IM lets us pop the soft keyboard up over the overlay when an
            // editable field gains focus inside our Compose UI.
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0; y = 0
        }

        wm.addView(composeView, p)
        rootView = composeView
        params = p
        Log.i(TAG, "Overlay attached")
    }

    private fun applyAlpha() {
        rootView?.let { v ->
            v.alpha = peekModeAlpha.value
            // When fully transparent, also disable touches so X431 receives them.
            params?.let { p ->
                if (peekModeAlpha.value < 0.05f) {
                    p.flags = p.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                } else {
                    p.flags = p.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
                }
                runCatching { wm.updateViewLayout(v, p) }
            }
        }
    }

    private fun togglePeek() {
        peekModeAlpha.value = if (peekModeAlpha.value > 0.5f) 0.0f else 1.0f
        applyAlpha()
    }

    private fun minimizeToBubble() {
        // Hand back to the legacy bubble overlay and stop the full-screen one.
        OverlayService.let { /* class ref keeps it imported */ }
        val i = Intent(this, OverlayService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(i) else startService(i)
        stopSelf()
    }

    // ---------- scraper loop ----------

    private fun startScraperLoop() {
        scraperJob?.cancel()
        scraperJob = scope.launch {
            while (isActive) {
                val a11y = ScannerAccessibilityService.instance()
                if (a11y != null) {
                    runCatching {
                        val snap = a11y.readScreen()
                        engineState.value = EngineScraper.scrape(snap)
                    }
                }
                delay(750)
            }
        }
    }

    // ---------- user-triggered capability execution ----------

    private fun dispatchCapability(capabilityId: String) {
        val a11y = ScannerAccessibilityService.instance() ?: run {
            engineState.value = engineState.value.copy(
                errorBanner = "Accessibility service not running. Enable in Setup."
            )
            return
        }
        // Bring X431 forward first (overlay still on top).
        a11y.bringX431ToFront()

        val cap = com.caseforge.scanner.engine.CapabilityMap.byId(capabilityId) ?: return
        val app = applicationContext as App
        app.actionLog.event("overlay.capability", "id=$capabilityId path=${cap.path.joinToString(">")}")

        scope.launch(Dispatchers.IO) {
            try {
                for (step in cap.path) {
                    val ok = a11y.tapByText(step, exact = false)
                    app.actionLog.event("overlay.tap", "step=$step ok=$ok")
                    delay(900)
                }
                cap.doneWhen?.let { marker ->
                    val ok = a11y.waitFor(marker, timeoutMs = cap.timeoutSec * 1000L)
                    app.actionLog.event(
                        "overlay.capability.done",
                        "id=$capabilityId marker_ok=$ok"
                    )
                }
            } catch (t: Throwable) {
                app.actionLog.event("overlay.capability.error", "${cap.id}: ${t.message}")
                engineState.value = engineState.value.copy(errorBanner = "Step failed: ${t.message?.take(120)}")
            }
        }
    }

    // ---------- notification ----------

    private fun buildNotification(): Notification {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Launch AI overlay", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val stopIntent = Intent(this, FullScreenOverlayService::class.java).setAction(ACTION_STOP)
        val pStop = android.app.PendingIntent.getService(
            this, 0, stopIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
        )
        val peekIntent = Intent(this, FullScreenOverlayService::class.java).setAction(ACTION_PEEK)
        val pPeek = android.app.PendingIntent.getService(
            this, 1, peekIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Launch AI overlay")
            .setContentText("Tap Peek to see X431 underneath. Tap Dismiss to exit.")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_view, "Peek", pPeek)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Dismiss", pStop)
            .build()
    }
}
