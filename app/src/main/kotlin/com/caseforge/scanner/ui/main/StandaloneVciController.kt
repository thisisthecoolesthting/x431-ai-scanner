package com.caseforge.scanner.ui.main

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import com.caseforge.scanner.data.SettingsRepo
import com.caseforge.scanner.engine.EngineState
import com.caseforge.scanner.engine.ScrapedDtc
import com.caseforge.scanner.engine.ScreenKind
import com.caseforge.scanner.vci.DirectVciSession
import com.caseforge.scanner.vci.VciCommunicator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * In-activity Direct VCI session (no X431 overlay).
 */
class StandaloneVciController(
    context: Context,
    private val settings: SettingsRepo,
) {
    private val appContext = context.applicationContext
    private val session = DirectVciSession(appContext, settings)

    var engineState = mutableStateOf(
        EngineState(screen = ScreenKind.HomeMenu, updatedAtMs = System.currentTimeMillis()),
    )
        private set

    val isConnected: Boolean get() = session.isConnected

    fun lastConnectError(): String? = session.lastConnectError()

    private var liveJob: Job? = null

    suspend fun connect(): Result<Unit> {
        val r = session.ensureConnected()
        if (r.isSuccess) {
            val vin = session.readVinOrNull()
            withContext(Dispatchers.Main) {
                engineState.value = engineState.value.copy(
                    vehicleVin = vin,
                    errorBanner = null,
                )
            }
        } else {
            withContext(Dispatchers.Main) {
                engineState.value = engineState.value.copy(
                    errorBanner = session.lastConnectError() ?: "Connect failed",
                )
            }
        }
        return r
    }

    fun disconnect() {
        liveJob?.cancel()
        liveJob = null
        session.disconnect()
        engineState.value = engineState.value.copy(
            errorBanner = null,
            liveData = emptyMap(),
        )
    }

    fun runFullScan(scope: CoroutineScope, onDone: (Boolean) -> Unit) {
        scope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) {
                engineState.value = engineState.value.copy(busy = true, errorBanner = null)
            }
            if (session.ensureConnected().isFailure) {
                failConnect()
                onDone(false)
                return@launch
            }
            val adapter = session.adapterOrNull()
            if (adapter == null) {
                fail("VCI adapter not ready")
                onDone(false)
                return@launch
            }
            adapter.fullScan().fold(
                onSuccess = { scan ->
                    val vin = session.readVinOrNull()
                    val dtcs = scan.modules.flatMap { m ->
                        m.dtcs.map { d ->
                            ScrapedDtc(
                                code = d.code,
                                description = d.description,
                                module = m.name,
                                status = "current",
                            )
                        }
                    }
                    withContext(Dispatchers.Main) {
                        engineState.value = engineState.value.copy(
                            screen = ScreenKind.FullScanResults,
                            vehicleVin = vin ?: engineState.value.vehicleVin,
                            dtcs = dtcs,
                            busy = false,
                            errorBanner = null,
                            updatedAtMs = System.currentTimeMillis(),
                        )
                        onDone(true)
                    }
                },
                onFailure = {
                    fail(it.message ?: "Scan failed")
                    onDone(false)
                },
            )
        }
    }

    fun startLiveData(scope: CoroutineScope) {
        liveJob?.cancel()
        scope.launch(Dispatchers.IO) {
            if (session.ensureConnected().isFailure) {
                failConnect()
                return@launch
            }
            val adapter = session.adapterOrNull() ?: return@launch
            withContext(Dispatchers.Main) {
                engineState.value = engineState.value.copy(
                    screen = ScreenKind.LiveDataView,
                    liveData = emptyMap(),
                    busy = true,
                    errorBanner = null,
                )
            }
            val hexPids = VciCommunicator.DEFAULT_LIVE_PIDS.map { pid ->
                "0x${pid.toString(16).uppercase().padStart(2, '0')}"
            }
            liveJob = scope.launch(Dispatchers.IO) {
                adapter.liveData(hexPids).collectLatest { sample ->
                    withContext(Dispatchers.Main) {
                        val label = liveLabel(sample.pid)
                        engineState.value = engineState.value.copy(
                            liveData = engineState.value.liveData + (label to sample.value),
                            busy = false,
                            updatedAtMs = System.currentTimeMillis(),
                        )
                    }
                }
            }
        }
    }

    fun stopLiveData() {
        liveJob?.cancel()
        liveJob = null
    }

    private suspend fun failConnect() {
        fail(session.lastConnectError() ?: "Not connected")
    }

    private suspend fun fail(msg: String) {
        withContext(Dispatchers.Main) {
            engineState.value = engineState.value.copy(busy = false, errorBanner = msg)
        }
    }

    private fun liveLabel(pidKey: String): String = when (pidKey.uppercase()) {
        "0X0C" -> "Engine RPM"
        "0X0D" -> "Vehicle Speed"
        "0X05" -> "Coolant Temp"
        else -> pidKey
    }
}
