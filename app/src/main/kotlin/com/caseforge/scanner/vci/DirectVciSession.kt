package com.caseforge.scanner.vci

import android.content.Context
import android.hardware.usb.UsbDevice
import com.caseforge.scanner.data.SettingsRepo
import com.caseforge.scanner.engine.EngineState
import com.caseforge.scanner.engine.ScrapedDtc
import com.caseforge.scanner.engine.ScreenKind
import com.caseforge.scanner.engine.VciDiagnosticPort
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Single diagnostic session for standalone flows (ELM327 USB primary, Launch VCI USB/BT optional).
 */
class DirectVciSession(
    private val context: Context,
    private val settings: SettingsRepo,
) {
    private val connectMutex = Mutex()
    private var activeLink: DiagnosticConnector.ActiveLink? = null
    private var lastError: String? = null

    val isConnected: Boolean get() = activeLink != null

    fun lastConnectError(): String? = lastError

    fun linkKind(): DiagnosticConnector.LinkKind? = activeLink?.kind

    fun adapterOrNull(): VciDiagnosticPort? = activeLink?.port

    suspend fun ensureConnected(usbDevice: UsbDevice? = null): Result<Unit> = connectMutex.withLock {
        lastError = null
        if (activeLink != null) return Result.success(Unit)

        val connected = DiagnosticConnector.connect(context, settings, usbDevice)
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

    fun disconnect() {
        activeLink?.disconnect?.invoke()
        activeLink = null
    }

    suspend fun readVinOrNull(): String? = activeLink?.readVin?.invoke()
}
