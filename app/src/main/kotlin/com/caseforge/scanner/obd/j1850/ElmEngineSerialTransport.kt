package com.caseforge.scanner.obd.j1850

import com.caseforge.scanner.agent.ObdElmEngine
import kotlinx.coroutines.runBlocking

/**
 * [ElmSerialTransport] that delegates to the app's existing, already-connected
 * [ObdElmEngine] instead of opening a second, competing serial/USB claim.
 *
 * The app already has a mature, transport-agnostic ELM327 lane: [ObdElmEngine] talks
 * through the [com.caseforge.scanner.agent.ElmIo] abstraction, backed by either
 * [com.caseforge.scanner.vci.transport.UsbSerialTransport] (USB-OTG, via
 * `com.caseforge.scanner.agent.ObdUsbTool`) or [com.caseforge.scanner.agent.ObdBluetoothTool]
 * (Bluetooth SPP) depending on how the user connected — see
 * `com.caseforge.scanner.vci.DiagnosticConnector.ActiveLink.elmEngine`, populated for both
 * `LinkKind.ELM327_USB` and `LinkKind.ELM327_BT`. Reusing it here means the J1850 SKIM read
 * works over whichever transport the user is already connected with, and never fights that
 * connection for the USB interface / Bluetooth socket (Android USB interface claims are
 * exclusive — a second independent open on the same device would fail outright).
 *
 * [ElmSerialTransport] is a synchronous interface ([open]/[sendCommand]/[close] are plain
 * `fun`s, not `suspend`) by design (clean-room core, no coroutines dependency so it unit-tests
 * on a plain JDK). [ObdElmEngine.sendRawCommand] is `suspend`. This class bridges that gap with
 * [runBlocking]. Callers of [SkimVpwReader] (and therefore this transport) are expected to
 * already be running on a background dispatcher — [ImmoInfoService.readStateWithLive] runs
 * inside `withContext(Dispatchers.IO)` in `ImmoInfoScreen` — never call this from the main
 * thread.
 *
 * [open] and [close] are intentionally no-ops: the connection lifecycle (USB permission,
 * device selection, port/socket open+close) is owned by whoever established the [engine]
 * connection ([com.caseforge.scanner.vci.DiagnosticConnector] / `ObdUsbTool` /
 * `ObdBluetoothTool`), not by this transport — closing it here would drop the shared
 * connection out from under the rest of the app (live PID polling, DTC reads, etc.).
 *
 * Residual runtime note (not a compile risk, see RECONCILIATION.md): [SkimVpwReader]'s
 * `elmInitVpw()` bring-up (`ATSP2`/`ATH1`) reconfigures the *shared* adapter's protocol and
 * header mode as a side effect of a successful J1850 SKIM read — for the pre-2008 Stellantis
 * vehicles this path is gated to, J1850 VPW is genuinely that vehicle's OBD-II bus too, so this
 * is normally correct, but it does mean the adapter is left in ATH1/VPW mode afterward rather
 * than restored to whatever ATSP0/ATH0 state [ObdElmEngine.initialize] originally left it in.
 */
class ElmEngineSerialTransport(private val engine: ObdElmEngine) : ElmSerialTransport {

    override fun open() {
        // No-op by design - see class doc. The engine is already connected by the caller.
    }

    override fun sendCommand(cmd: String): String = runBlocking { engine.sendRawCommand(cmd) }

    override fun close() {
        // No-op by design - see class doc. The shared connection stays open for the rest of the app.
    }
}
