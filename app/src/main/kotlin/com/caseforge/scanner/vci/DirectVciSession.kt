package com.caseforge.scanner.vci

import android.content.Context
import com.caseforge.scanner.App
import com.caseforge.scanner.data.SettingsRepo
import com.caseforge.scanner.engine.ScrapedDtc
import com.caseforge.scanner.engine.ScreenKind
import com.caseforge.scanner.engine.EngineState
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Single VCI Bluetooth session for standalone overlay + probe flows.
 */
class DirectVciSession(
    private val context: Context,
    private val settings: SettingsRepo,
) {
    private val connectMutex = Mutex()
    private var client: VciSocketClient? = null
    private var communicator: VciCommunicator? = null
    private var adapter: VciDiagnosticAdapter? = null
    private var lastError: String? = null

    val isConnected: Boolean
        get() = client?.connectionState?.value == VciSocketClient.ConnectionState.CONNECTED

    fun lastConnectError(): String? = lastError

    fun adapterOrNull(): VciDiagnosticAdapter? = adapter

    suspend fun ensureConnected(): Result<Unit> = connectMutex.withLock {
        VciProtocolConfig.applyFromSettings(settings)
        lastError = null

        if (!VciConnectionDiagnostics.hasBluetoothConnectPermission(context)) {
            lastError = "BLUETOOTH_CONNECT not granted — enable Direct VCI in Settings"
            return Result.failure(IllegalStateException(lastError))
        }

        if (App.isX431Foreground(context)) {
            lastError = "X431 is in the foreground — force-stop X431 to free the VCI socket"
            return Result.failure(IllegalStateException(lastError))
        }

        val existing = client
        if (existing?.connectionState?.value == VciSocketClient.ConnectionState.CONNECTED) {
            return Result.success(Unit)
        }

        val c = VciSocketClient(context, useHexEncoding = settings.vciUseHexEncoding)
        val bonded = c.findBondedVciDevices()
        val saved = settings.vciSelectedBtAddress
        val allBonded = c.listBondedDevices()

        val target = when {
            saved != null -> {
                val mac = saved
                android.bluetooth.BluetoothAdapter.getDefaultAdapter()
                    ?.getRemoteDevice(mac)
            }
            bonded.isNotEmpty() -> bonded.first()
            allBonded.size == 1 -> {
                android.bluetooth.BluetoothAdapter.getDefaultAdapter()
                    ?.getRemoteDevice(allBonded.first().second)
            }
            else -> null
        }

        if (target == null) {
            lastError = if (allBonded.isEmpty()) {
                "No bonded Bluetooth devices — pair VCI in system settings"
            } else {
                "No VCI prefix match — open Direct VCI diagnostics and pick a device"
            }
            return Result.failure(IllegalStateException(lastError))
        }

        var connect = c.connect(target.address)
        if (connect.isFailure) {
            val ble = VciBleClient(context)
            val bleProbe = ble.probeDevice(target)
            ble.close()
            if (bleProbe.isSuccess) {
                lastError = "SPP failed (${connect.exceptionOrNull()?.message}); BLE GATT present — BLE transport not wired yet"
            } else {
                lastError = connect.exceptionOrNull()?.message ?: "SPP connect failed"
            }
            return Result.failure(IllegalStateException(lastError))
        }

        client = c
        val comm = VciCommunicator(c)
        communicator = comm
        adapter = VciDiagnosticAdapter(comm)
        Result.success(Unit)
    }

    fun disconnect() {
        client?.disconnect()
        client = null
        communicator = null
        adapter = null
    }

    suspend fun readVinOrNull(): String? =
        communicator?.readVin()?.getOrNull()

    fun engineStateFromDtcs(
        dtcs: List<com.caseforge.scanner.vci.Dtc>,
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
