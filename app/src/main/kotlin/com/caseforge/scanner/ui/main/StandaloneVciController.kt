package com.caseforge.scanner.ui.main



import android.content.Context

import androidx.compose.runtime.mutableStateOf

import com.caseforge.scanner.data.SettingsRepo

import com.caseforge.scanner.engine.EngineState

import com.caseforge.scanner.engine.ScrapedDtc

import com.caseforge.scanner.engine.ScreenKind

import com.caseforge.scanner.engine.VciDiagnosticPort

import com.caseforge.scanner.vci.DiagnosticConnector

import com.caseforge.scanner.vci.DirectVciSession

import kotlinx.coroutines.CoroutineScope

import kotlinx.coroutines.Dispatchers

import kotlinx.coroutines.Job

import kotlinx.coroutines.delay

import kotlinx.coroutines.flow.collectLatest

import kotlinx.coroutines.isActive

import kotlinx.coroutines.launch

import kotlinx.coroutines.withContext



/**

 * In-activity diagnostic session (ELM327 USB primary, OEM VCI optional, BT opt-in).

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



    fun linkKind(): DiagnosticConnector.LinkKind? = session.linkKind()



    private var liveJob: Job? = null

    // Polling job that watches for mid-session link drops.
    private var linkWatchJob: Job? = null

    // True while an intentional disconnect() is in progress; suppresses the drop banner.
    @Volatile private var intentionalDisconnect = false

    /**
     * Starts a polling loop (every [intervalMs] ms) that calls [DirectVciSession.isLinkLive].
     * On the first failed poll while we believed we were connected, it sets the error banner
     * and stops the live-data stream.
     *
     * Design note: a fully reactive path via [VciTransport.connectionState] is not reachable
     * from [DirectVciSession] without modifying [DiagnosticConnector] (which is out of scope).
     * Polling [isLinkLive] is therefore the correct approach here.  The 3-second default keeps
     * latency low while avoiding OBD bus spam.
     *
     * Call this at the end of a successful [connect] — [connect] does this automatically.
     * Cancels any prior watch job before starting a new one.
     */
    fun observeConnection(scope: CoroutineScope, intervalMs: Long = 3_000L) {
        linkWatchJob?.cancel()
        linkWatchJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(intervalMs)
                // Only fire when we believe we are connected and this isn't an intentional teardown.
                if (!intentionalDisconnect && session.isConnected) {
                    val live = session.isLinkLive()
                    if (!live && !intentionalDisconnect) {
                        liveJob?.cancel()
                        liveJob = null
                        withContext(Dispatchers.Main) {
                            engineState.value = engineState.value.copy(
                                busy = false,
                                liveData = emptyMap(),
                                errorBanner = "Connection lost — tap Reconnect",
                            )
                        }
                        // Stop polling; session is now in a disconnected state.
                        break
                    }
                }
            }
        }
    }

    /**
     * Tears down the current link and re-establishes it via [DirectVciSession.reconnect].
     * Clears the error banner on success; sets it on failure.  Mirrors the structure of
     * [connect] and calls [observeConnection] again on success so the watch loop resumes.
     */
    fun reconnect(scope: CoroutineScope, onDone: (Boolean) -> Unit) {
        linkWatchJob?.cancel()
        linkWatchJob = null
        scope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) {
                engineState.value = engineState.value.copy(busy = true, errorBanner = null)
            }
            val r = session.reconnect()
            if (r.isSuccess) {
                val vin = session.readVinOrNull()
                withContext(Dispatchers.Main) {
                    engineState.value = engineState.value.copy(
                        vehicleVin = vin ?: engineState.value.vehicleVin,
                        busy = false,
                        errorBanner = null,
                    )
                }
                observeConnection(scope)
                onDone(true)
            } else {
                withContext(Dispatchers.Main) {
                    engineState.value = engineState.value.copy(
                        busy = false,
                        errorBanner = session.lastConnectError() ?: "Reconnect failed",
                    )
                }
                onDone(false)
            }
        }
    }



    /**
     * Connects (or no-ops if already connected).
     *
     * Pass a [watchScope] to automatically start the link-drop watch loop on success.  The
     * ViewModel's viewModelScope is the intended owner; it is automatically cancelled when the
     * ViewModel is cleared, so the watch job never leaks.
     *
     * Callers that do NOT have a convenient scope (e.g. [MainActivity] using the old zero-arg
     * call) may omit it — drop-detection via [observeConnection] is then inactive for that
     * call site until a follow-up migration passes a scope.  The [reconnect] path always
     * restarts the watch loop.
     */
    suspend fun connect(watchScope: CoroutineScope? = null): Result<Unit> {

        val r = session.ensureConnected()

        if (r.isSuccess) {

            val vin = session.readVinOrNull()

            withContext(Dispatchers.Main) {

                engineState.value = engineState.value.copy(

                    vehicleVin = vin,

                    errorBanner = null,

                )

            }

            if (watchScope != null) observeConnection(watchScope)

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

        intentionalDisconnect = true

        linkWatchJob?.cancel()

        linkWatchJob = null

        liveJob?.cancel()

        liveJob = null

        session.disconnect()

        engineState.value = engineState.value.copy(

            errorBanner = null,

            liveData = emptyMap(),

        )

        intentionalDisconnect = false

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

            val port = session.adapterOrNull()

            if (port == null) {

                fail("Diagnostic adapter not ready")

                onDone(false)

                return@launch

            }

            port.fullScan().fold(

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

            val port = session.adapterOrNull() ?: return@launch

            withContext(Dispatchers.Main) {

                engineState.value = engineState.value.copy(

                    screen = ScreenKind.LiveDataView,

                    liveData = emptyMap(),

                    busy = true,

                    errorBanner = null,

                )

            }

            val pids = listOf("0C", "0D", "05")

            liveJob = scope.launch(Dispatchers.IO) {

                port.liveData(pids).collectLatest { sample ->

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

        "0C" -> "Engine RPM"

        "0D" -> "Vehicle Speed"

        "05" -> "Coolant Temp"

        else -> pidKey

    }

}

