package com.caseforge.scanner.vci

import android.content.Context
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

  val isConnected: Boolean
    get() = client?.connectionState?.value == VciSocketClient.ConnectionState.CONNECTED

  fun adapterOrNull(): VciDiagnosticAdapter? = adapter

  suspend fun ensureConnected(): Result<Unit> = connectMutex.withLock {
    VciProtocolConfig.applyFromSettings(settings)
    val existing = client
    if (existing?.connectionState?.value == VciSocketClient.ConnectionState.CONNECTED) {
      return Result.success(Unit)
    }
    val c = VciSocketClient(context, useHexEncoding = settings.vciUseHexEncoding)
    val devices = c.findBondedVciDevices()
    if (devices.isEmpty()) {
      return Result.failure(IllegalStateException("No bonded VCI — pair dongle in Bluetooth settings"))
    }
    val target = devices.first()
    val connect = c.connect(target.address)
    if (connect.isFailure) return Result.failure(connect.exceptionOrNull()!!)
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
