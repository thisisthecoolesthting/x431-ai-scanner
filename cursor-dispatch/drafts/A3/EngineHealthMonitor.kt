package com.caseforge.scanner.engine

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import com.caseforge.scanner.agent.ScannerAccessibilityService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Watches the four runtime invariants the overlay depends on:
 *   1. X431 is in the foreground (via ScannerAccessibilityService event hook)
 *   2. Accessibility service is alive
 *   3. Bluetooth adapter is enabled
 *   4. A recognised VCI dongle is bonded (informational — not a health gate)
 *
 * ## Foreground detection
 * The monitor exposes [notifyForegroundPackage] — a lightweight hook that
 * [com.caseforge.scanner.overlay.FullScreenOverlayService] calls from its scraper loop
 * whenever it reads a new [com.caseforge.scanner.agent.ScreenSnapshot] (the accessibility
 * service has already parsed the foreground package by that point). This avoids any
 * manifest permission additions (PACKAGE_USAGE_STATS is intentionally not requested)
 * and keeps [ScannerAccessibilityService] read-only.
 *
 * If no package notification has arrived yet the monitor reads the last snapshot's
 * package from [ScannerAccessibilityService.instance()?.readScreen()?.pkg], which is
 * zero-permission and synchronous.
 *
 * @param context      Application context. Used only for BluetoothManager/BluetoothAdapter.
 * @param a11yLiveness Lambda returning true when accessibility service is connected.
 *                     Inject as `{ ScannerAccessibilityService.instance() != null }`.
 * @param scope        Coroutine scope that drives the poll loop. Caller owns cancellation.
 * @param pollMs       Poll interval in milliseconds (default 1500).
 */
class EngineHealthMonitor(
    private val context: Context,
    private val a11yLiveness: () -> Boolean,
    private val scope: CoroutineScope,
    val pollMs: Long = 1500L,
) {

    // -----------------------------------------------------------------------
    //  State
    // -----------------------------------------------------------------------

    private val _state = MutableStateFlow(
        HealthState(
            x431Foreground       = false,
            accessibilityGranted = false,
            bluetoothOn          = false,
            vciConnected         = false,
            lastError            = null,
            tsMs                 = System.currentTimeMillis(),
        )
    )
    val state: StateFlow<HealthState> = _state.asStateFlow()

    private var pollJob: Job? = null

    /** Last package pushed via [notifyForegroundPackage] by FullScreenOverlayService. */
    @Volatile private var lastPushedPackage: String? = null

    // -----------------------------------------------------------------------
    //  Public API
    // -----------------------------------------------------------------------

    /**
     * Called by [com.caseforge.scanner.overlay.FullScreenOverlayService] each time it reads
     * a screen snapshot (i.e. every ~750 ms scraper tick). Prefer this over any
     * permission-gated API.
     */
    fun notifyForegroundPackage(pkg: String?) {
        lastPushedPackage = pkg
    }

    /**
     * Start the polling loop. Safe to call multiple times — re-entrant calls are no-ops
     * if the loop is already active.
     */
    fun start() {
        if (pollJob?.isActive == true) return
        pollJob = scope.launch {
            while (isActive) {
                tick()
                delay(pollMs)
            }
        }
    }

    /** Stop polling. The last emitted [HealthState] remains available on [state]. */
    fun stop() {
        pollJob?.cancel()
        pollJob = null
    }

    // -----------------------------------------------------------------------
    //  Internal evaluation (internal visibility for unit tests)
    // -----------------------------------------------------------------------

    internal fun tick() {
        val a11yAlive = a11yLiveness()
        val btOn      = checkBluetooth()
        val vciConn   = checkVci()
        val x431Fg    = checkX431Foreground(a11yAlive)

        val error = when {
            !a11yAlive -> "Accessibility revoked — re-enable in Settings"
            !x431Fg    -> "X431 not foreground — open X431 to continue"
            !btOn      -> "Bluetooth off — turn on Bluetooth"
            !vciConn   -> "VCI dongle not connected"
            else       -> null
        }

        _state.value = HealthState(
            x431Foreground       = x431Fg,
            accessibilityGranted = a11yAlive,
            bluetoothOn          = btOn,
            vciConnected         = vciConn,
            lastError            = error,
            tsMs                 = System.currentTimeMillis(),
        )
    }

    /**
     * Foreground check.
     * Priority 1: use the package pushed by [notifyForegroundPackage] (comes from
     *              accessibility ScreenSnapshot.pkg, zero-permission).
     * Priority 2: if a11y is alive, ask the instance directly for the root window pkg.
     * If a11y isn't alive we can't know the foreground app — return false.
     */
    internal fun checkX431Foreground(a11yAlive: Boolean): Boolean {
        if (!a11yAlive) return false
        // Prefer the last value pushed from the scraper loop.
        val pushed = lastPushedPackage
        if (pushed != null) return pushed in ScannerAccessibilityService.X431_PACKAGES
        // Fallback: read directly from the running instance (synchronous, main-thread safe).
        val pkg = ScannerAccessibilityService.instance()?.readScreen()?.pkg ?: return false
        return pkg in ScannerAccessibilityService.X431_PACKAGES
    }

    internal fun checkBluetooth(): Boolean =
        try { bluetoothAdapter?.isEnabled == true } catch (_: SecurityException) { false }

    internal fun checkVci(): Boolean =
        try {
            val adapter = bluetoothAdapter ?: return false
            if (!adapter.isEnabled) return false
            @Suppress("MissingPermission")
            adapter.bondedDevices
                ?.any { dev ->
                    val name = dev.name ?: return@any false
                    VCI_PREFIXES.any { prefix -> name.startsWith(prefix, ignoreCase = true) }
                } == true
        } catch (_: SecurityException) {
            false
        }

    // -----------------------------------------------------------------------
    //  Private helpers
    // -----------------------------------------------------------------------

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
            ?: @Suppress("DEPRECATION") BluetoothAdapter.getDefaultAdapter()
    }

    internal companion object {
        /** VCI dongle name prefixes (case-insensitive). */
        val VCI_PREFIXES = listOf("VCI-", "Launch-", "X431-")
    }
}

// ---------------------------------------------------------------------------
//  HealthState
// ---------------------------------------------------------------------------

/**
 * Immutable snapshot of the overlay's runtime health.
 *
 * [isHealthy] requires all three mandatory conditions:
 *   - [x431Foreground]: X431 is the current foreground app
 *   - [accessibilityGranted]: ScannerAccessibilityService is running and connected
 *   - [bluetoothOn]: Bluetooth adapter is enabled
 *
 * [vciConnected] is tracked and surfaced in the banner but is **not** a health gate —
 * VCI dongles reconnect automatically and blocking the overlay on every brief drop
 * would be too disruptive in a busy workshop.
 *
 * Banner copy priority (worst = most urgent wins):
 *   1. "Accessibility revoked — re-enable in Settings"
 *   2. "X431 not foreground — open X431 to continue"
 *   3. "Bluetooth off — turn on Bluetooth"
 *   4. "VCI dongle not connected"
 */
data class HealthState(
    val x431Foreground: Boolean,
    val accessibilityGranted: Boolean,
    val bluetoothOn: Boolean,
    val vciConnected: Boolean,
    val lastError: String?,
    val tsMs: Long,
) {
    val isHealthy: Boolean
        get() = x431Foreground && accessibilityGranted && bluetoothOn
}
