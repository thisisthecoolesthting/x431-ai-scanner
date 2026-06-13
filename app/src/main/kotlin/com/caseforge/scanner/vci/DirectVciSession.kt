package com.caseforge.scanner.vci

import android.content.Context
import android.hardware.usb.UsbDevice
import com.caseforge.scanner.data.SettingsRepo
import com.caseforge.scanner.engine.EngineState
import com.caseforge.scanner.engine.ScrapedDtc
import com.caseforge.scanner.engine.ScreenKind
import com.caseforge.scanner.engine.VciDiagnosticPort
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Single diagnostic session for standalone flows (ELM327 USB primary, OEM VCI USB/BT optional).
 */
class DirectVciSession(
    private val context: Context,
    private val settings: SettingsRepo,
) {
    private val connectMutex = Mutex()
    private var activeLink: DiagnosticConnector.ActiveLink? = null
    private var lastError: String? = null
    // Last USB device used to connect, remembered for reconnect.
    private var lastUsbDevice: UsbDevice? = null

    val isConnected: Boolean get() = activeLink != null

    fun lastConnectError(): String? = lastError

    fun linkKind(): DiagnosticConnector.LinkKind? = activeLink?.kind

    fun adapterOrNull(): VciDiagnosticPort? = activeLink?.port

    /**
     * Returns the [VciTransport.connectionState] flow for the active link when it is an OEM VCI
     * transport (USB or BT), or null for ELM327 paths which do not expose a transport.
     *
     * Callers can use this to reactively detect link drops instead of polling [isLinkLive].
     */
    fun connectionStateFlow(): StateFlow<VciTransport.ConnectionState>? =
        activeLink?.transport?.connectionState

    /**
     * Returns true when the session believes the link is still live.
     *
     * NOTE: DiagnosticConnector.ActiveLink does not expose the underlying VciTransport, so a
     * reactive StateFlow path is not reachable from here without modifying DiagnosticConnector.
     * This is therefore a best-effort synchronous check: it reads [isConnected] (activeLink !=
     * null) and attempts a lightweight VIN ping to confirm the physical link. A failed ping
     * clears [activeLink] so the caller can see the session as disconnected.
     *
     * For reactive drop-detection the UI should poll this via [StandaloneVciController.observeConnection].
     */
    suspend fun isLinkLive(): Boolean {
        if (activeLink == null) return false
        return try {
            // readVin is a lightweight round-trip; failure means the link is gone.
            activeLink!!.readVin.invoke()
            true
        } catch (_: Exception) {
            // Treat any exception as a dead link so the next connect() starts fresh.
            activeLink?.disconnect?.invoke()
            activeLink = null
            false
        }
    }

    suspend fun ensureConnected(usbDevice: UsbDevice? = null): Result<Unit> = connectMutex.withLock {
        lastError = null
        if (activeLink != null) return Result.success(Unit)

        if (usbDevice != null) lastUsbDevice = usbDevice

        val connected = DiagnosticConnector.connect(context, settings, usbDevice ?: lastUsbDevice)
        return connected.fold(
            onSuccess = { link ->
                activeLink = link
                Result.success(Unit)
            },
            onFailure = { e ->
                lastError = e.message
                Result.failure(e)
            },
        )
    }

    /**
     * Tears down the current link (if any) and re-runs [ensureConnected] with the last-known
     * USB device. Safe to call from any coroutine context; protected by the same mutex as
     * [ensureConnected].
     */
    suspend fun reconnect(): Result<Unit> {
        connectMutex.withLock {
            activeLink?.disconnect?.invoke()
            activeLink = null
        }
        return ensureConnected(lastUsbDevice)
    }

    fun disconnect() {
        activeLink?.disconnect?.invoke()
        activeLink = null
    }

    suspend fun readVinOrNull(): String? = activeLink?.readVin?.invoke()
}
