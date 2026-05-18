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
import android.widget.Toast
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.caseforge.scanner.agent.NextTestSuggester
import com.caseforge.scanner.agent.RecallFlagger
import com.caseforge.scanner.agent.ScannerAccessibilityService
import com.caseforge.scanner.engine.ScreenKind
import com.caseforge.scanner.ai.ClaudeClient
import com.caseforge.scanner.data.SettingsRepo
import com.caseforge.scanner.engine.EngineScraper
import com.caseforge.scanner.engine.EngineHealthMonitor
import com.caseforge.scanner.engine.EngineState
import com.caseforge.scanner.engine.ScreenKind
import com.caseforge.scanner.overlay.compose.OverlayRoot
import com.caseforge.scanner.overlay.compose.screens.UiAction
import com.caseforge.scanner.voice.VoiceCommander
import com.caseforge.scanner.voice.VoiceMode
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
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
 * ## A3 additions
 *   - [EngineHealthMonitor] is created in [onCreate], started immediately, and stopped in [onDestroy].
 *   - The scraper loop calls [EngineHealthMonitor.notifyForegroundPackage] on every tick
 *     so the monitor receives zero-permission foreground data from the running a11y service.
 *   - [OverlayRoot] receives `healthState` from [monitor.state.collectAsState()` so the
 *     error banner re-renders reactively on each poll tick without any additional wiring.
 *
 * ## D2 additions
 *   - On every screen transition, state is persisted to cacheDir/overlay_state.json via [OverlayStatePersistence].
 *   - On [onCreate], if state file exists, it is restored into [engineState].
 *   - On [onDestroy] (graceful shutdown), the state file is cleared.
 *   - Crashes or OS kills lose the state file naturally (BootReceiver + SettingsRepo handle re-launch).
 *
 * ## D1 additions
 *   - [requestEmergencyDismiss] provides an escape hatch: 3-second press-and-hold on dead space
 *     dismisses the overlay with a Toast confirmation. Called from OverlayRoot's pointerInput handler.
 *
 * Escape hatches:
 *   - "Peek" button fades overlay to 30% opacity so the tech can verify X431 underneath
 *   - "Minimize" collapses the overlay to a bubble (legacy OverlayService bubble)
 *   - Foreground-service notification action "Dismiss overlay" for emergency exit
 *   - 3-second long-press on dead space (D1) for emergency dismissal
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
        const val ACTION_STOP  = "com.caseforge.scanner.overlay.STOP_FULLSCREEN"
        const val ACTION_PEEK  = "com.caseforge.scanner.overlay.PEEK"

        /**
         * Idempotency guard consumed by the A6 ScannerAccessibilityService auto-launch path.
         * Set to true in [onStartCommand] and false in [onDestroy].
         */
        @Volatile var isRunning: Boolean = false
            private set

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
    private val engineState   = mutableStateOf(EngineState.EMPTY)
    private val peekModeAlpha = mutableStateOf(1.0f) // 1.0 = fully opaque, 0.0 = peek
    private var lastPostScanSignature: String? = null
    private val voiceUiState = mutableStateOf(VoiceMode.State.IDLE)
    private val voiceLastPhrase = mutableStateOf("")
    private var voiceCommander: VoiceCommander? = null

    // A3: health monitor — created in onCreate, started/stopped alongside the service.
    private lateinit var monitor: EngineHealthMonitor
    
    // SettingsRepo for C2 onboarding gate
    private lateinit var settingsRepo: SettingsRepo

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        savedStateController.performAttach()
        savedStateController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        wm = getSystemService(WINDOW_SERVICE) as WindowManager

        // Initialize SettingsRepo for C2 onboarding gate
        settingsRepo = SettingsRepo(applicationContext)

        // D2: restore persisted state if available
        val restoredState = OverlayStatePersistence.load(applicationContext)
        if (restoredState != null) {
            engineState.value = restoredState
            Log.i(TAG, "Restored persisted state: screen=${restoredState.screen}")
        }

        // A3: instantiate and start the health monitor.
        // a11yLiveness delegates to the ScannerAccessibilityService companion accessor —
        // no additional import or reflection needed.
        monitor = EngineHealthMonitor(
            context      = applicationContext,
            a11yLiveness = { ScannerAccessibilityService.instance() != null },
            scope        = scope,
            pollMs       = 1500L,
        )
        monitor.start()
        Log.i(TAG, "EngineHealthMonitor started")
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

        isRunning = true
        startForeground(NOTIFICATION_ID, buildNotification())
        if (rootView == null) attachOverlay()
        syncVoiceCommander()
        startScraperLoop()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        return START_STICKY
    }

    override fun onDestroy() {
        // D2: clear persisted state on graceful shutdown
        OverlayStatePersistence.clear(applicationContext)
        Log.i(TAG, "Persisted state cleared on shutdown")

        // A3: stop the health monitor before cancelling the scope it runs on.
        monitor.stop()
        Log.i(TAG, "EngineHealthMonitor stopped")

        voiceCommander?.stop()
        voiceCommander = null

        scraperJob?.cancel()
        scope.cancel()
        rootView?.let { runCatching { wm.removeView(it) } }
        rootView = null
        params = null
        isRunning = false
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
                // A3: collect health state reactively so the banner updates on each poll tick.
                val healthState by monitor.state.collectAsState()

                OverlayRoot(
                    engineState  = engineState.value,
                    alpha        = peekModeAlpha.value,
                    settingsRepo = settingsRepo,
                    onMinimize   = { minimizeToBubble() },
                    onDismiss    = { stopSelf() },
                    onPeek       = { togglePeek() },
                    onCapability = { id -> dispatchCapability(id) },
                    onUiAction = { handleUiAction(it) },
                    onEmergencyDismiss = { requestEmergencyDismiss() },
                    healthState  = healthState,
                    voiceEnabled = settingsRepo.voiceEnabled && hasRecordAudioPermission(),
                    voiceState = voiceUiState.value,
                    voiceLastPhrase = voiceLastPhrase.value,
                    onVoicePressStart = { voiceCommander?.startPushToTalk() },
                    onVoicePressEnd = { voiceCommander?.stopPushToTalk() },
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

    // ---------- D1: emergency dismiss via 3s long-press ----------

    /**
     * Called from OverlayRoot's pointerInput handler when a 3-second press-and-hold
     * is detected on dead space (non-interactive area). Stops the service and shows
     * a Toast confirming that the overlay is dismissed and X431 is now visible.
     */
    fun requestEmergencyDismiss() {
        Log.i(TAG, "Emergency dismiss triggered via 3-second long-press")
        Toast.makeText(
            this,
            "Overlay dismissed. X431 is now visible.",
            Toast.LENGTH_SHORT
        ).show()
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
                        // A3: feed the foreground package to the health monitor so it can
                        // evaluate X431 foreground state without any manifest permissions.
                        monitor.notifyForegroundPackage(snap.pkg)
                        val prev = engineState.value
                        val newState = EngineScraper.scrape(snap)
                        engineState.value = newState

                        if (newState.screen is ScreenKind.FullScanResults) {
                            maybeRunPostScanAnalysis(prev, newState)
                        } else {
                            lastPostScanSignature = null
                        }

                        // D2: persist state on every screen transition
                        if (newState.screen != prev.screen) {
                            OverlayStatePersistence.save(applicationContext, newState)
                        }
                    }
                }
                delay(750)
            }
        }
    }

    private fun maybeFetchRecalls(prev: EngineState, newState: EngineState) {
        if (newState.screen !is ScreenKind.FullScanResults) return
        if (newState.dtcs.isEmpty()) return
        val vin = newState.vehicleVin ?: return
        val signature = "$vin|${newState.dtcs.joinToString { it.code }}"
        if (signature == lastRecallSignature && newState.recallMatches.isNotEmpty()) return
        lastRecallSignature = signature

        scope.launch(Dispatchers.IO) {
            val matches = recallFlagger.flagRecalls(vin, newState.dtcs)
            if (matches.isNotEmpty()) {
                engineState.value = engineState.value.copy(recallMatches = matches)
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
        app.actionLog.event("overlay.capability", "id=$capabilityId path=${cap.path.joinToString(">")}\"")

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
