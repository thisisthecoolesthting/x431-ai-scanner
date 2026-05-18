package com.caseforge.scanner.vci

import android.content.Context
import android.hardware.usb.UsbDevice
import com.caseforge.scanner.data.SettingsRepo
import com.caseforge.scanner.engine.ScrapedDtc
import com.caseforge.scanner.engine.ScreenKind
import com.caseforge.scanner.engine.EngineState
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Single VCI session for standalone flows (USB OTG or Bluetooth SPP).
 */
class DirectVciSession(
    private val context: Context,
    private val settings: SettingsRepo,
) {
    private val connectMutex = Mutex()
    private var transport: VciTransport? = null
    private var communicator: VciCommunicator? = null
    private var adapter: VciDiagnosticAdapter? = null
    private var lastError: String? = null

    val isConnected: Boolean
        get() = transport?.connectionState?.value == VciTransport.ConnectionState.CONNECTED

    fun lastConnectError(): String? = lastError

    fun adapterOrNull(): VciDiagnosticAdapter? = adapter

    suspend fun ensureConnected(usbDevice: UsbDevice? = null): Result<Unit> = connectMutex.withLock {
        lastError = null

        val existing = transport
        if (existing?.connectionState?.value == VciTransport.ConnectionState.CONNECTED) {
            return Result.success(Unit)
        }

        val pending = usbDevice ?: VciUsbAttachState.consumePending()
        val connected = VciConnector.connect(context, settings, pending)
        return connected.fold(
            onSuccess = { result ->
                transport = result.transport
                val comm = VciCommunicator(result.transport)
                communicator = comm
                adapter = VciDiagnosticAdapter(comm)
                Result.success(Unit)
            },
            onFailure = { e ->
                lastError = e.message
                Result.failure(e)
            },
        )
    }

    fun disconnect() {
        transport?.disconnect()
        transport = null
        communicator = null
        adapter = null
    }

    suspend fun readVinOrNull(): String? =
        communicator?.readVin()?.getOrNull()

    fun engineStateFromDtcs(
        dtcs: List<Dtc>,
        vin: String?,
        screen: ScreenKind = ScreenKind.FullScanResults,
    ): EngineState = EngineState(
        screen = screen,
        vehicleVin = vin,
        dtcs = dtcs.map {
            ScrapedDtc(
                code = it.code.removePrefix("PENDING:"),
                description = it.description,
                module = "OBD-II",
                status = if (it.code.startsWith("PENDING:")) "pending" else "current",
            )
        },
        updatedAtMs = System.currentTimeMillis(),
    )
}
