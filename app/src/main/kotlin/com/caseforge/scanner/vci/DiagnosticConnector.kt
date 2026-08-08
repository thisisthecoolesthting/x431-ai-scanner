package com.caseforge.scanner.vci

import android.content.Context
import android.hardware.usb.UsbDevice
import android.util.Log
import com.caseforge.scanner.App
import com.caseforge.scanner.agent.ObdBluetoothTool
import com.caseforge.scanner.agent.ObdElmEngine
import com.caseforge.scanner.agent.ObdUsbTool
import com.caseforge.scanner.data.SettingsRepo
import com.caseforge.scanner.engine.ObdEngineDriver
import com.caseforge.scanner.engine.VciDiagnosticPort
import com.caseforge.scanner.vci.transport.UsbSerialTransport
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout

/**
 * Resolves the active vehicle link: ELM327 USB (primary), OEM VCI USB, optional Bluetooth paths.
 */
object DiagnosticConnector {

    private const val TAG = "DiagConnect"

    enum class LinkKind {
        ELM327_USB,
        OEM_USB,
        OEM_BT,
        ELM327_BT,
    }

    data class ActiveLink(
        val kind: LinkKind,
        val port: VciDiagnosticPort,
        val detail: String,
        val disconnect: () -> Unit,
        val readVin: suspend () -> String?,
        /** Populated for ELM327 USB/BT links — used by [com.caseforge.scanner.obd.Elm327ObdTransport]. */
        val elmEngine: ObdElmEngine? = null,
    )

    internal enum class AutoAttempt {
        ELM327_USB,
        OEM_USB,
        ELM327_BT,
        OEM_BT,
    }

    enum class UserTransport {
        AUTO,
        ELM327_USB,
        OEM_USB,
        OEM_BT,
        ELM327_BT,
    }

    fun userTransportFrom(settings: SettingsRepo): UserTransport = when (settings.linkTransport.lowercase()) {
        "elm327_usb", "usb_obd", "usb_cable" -> UserTransport.ELM327_USB
        "oem_usb", "launch_usb", "vci_usb" -> UserTransport.OEM_USB
        "oem_bt", "launch_bt", "vci_bt", "bluetooth" -> UserTransport.OEM_BT
        "elm327_bt", "obd_bt" -> UserTransport.ELM327_BT
        else -> UserTransport.AUTO
    }

    suspend fun connect(
        context: Context,
        settings: SettingsRepo,
        usbDevice: UsbDevice? = null,
    ): Result<ActiveLink> {
        VciProtocolConfig.applyFromSettings(settings)

        if (App.isOemDiagForeground(context)) {
            val reason = App.lastOemForegroundBlockReason
                ?: "OEM diagnostic app is in the foreground — force-stop it to free the adapter"
            settings.recordOemDiagConnectBlock(reason)
            settings.recordConnectAttempt(false, "blocked: $reason")
            return Result.failure(IllegalStateException(reason))
        }

        val mode = userTransportFrom(settings)
        Log.i(
            TAG,
            "connect entry mode=$mode linkTransport=${settings.linkTransport} usb=${usbDevice?.deviceName ?: "auto"}",
        )
        val result = when (mode) {
            UserTransport.ELM327_USB -> connectElm327Usb(context, usbDevice)
            UserTransport.OEM_USB -> connectOemUsb(context, settings, usbDevice)
            UserTransport.OEM_BT -> connectOemBt(context, settings)
            UserTransport.ELM327_BT -> connectElm327Bt(settings)
            UserTransport.AUTO -> connectAuto(context, settings, usbDevice)
        }
        result.fold(
            onSuccess = { link ->
                settings.recordConnectAttempt(
                    success = true,
                    summary = "ok ${link.kind} (${settings.linkTransport}): ${link.detail}",
                )
            },
            onFailure = { err ->
                settings.recordConnectAttempt(
                    success = false,
                    summary = "fail $mode (${settings.linkTransport}): ${err.message ?: err.javaClass.simpleName}",
                )
            },
        )
        return result
    }

    /** True when VIN read or Mode 03 DTC read succeeds on an open link. Returns VIN when read. */
    suspend fun verifyProvenRead(link: ActiveLink): Pair<Boolean, String?> = runCatching {
        val vin = link.readVin()?.trim()?.takeIf { it.isNotBlank() }
        if (vin != null) return true to vin
        val ok = link.port.readDtcs(null).isSuccess
        ok to null
    }.getOrDefault(false to null)

    /** Persist last-known-good transport after [verifyProvenRead] passes. */
    fun persistProvenConnect(settings: SettingsRepo, link: ActiveLink, vin: String? = null) {
        runCatching {
            val transport = transportKeyFor(link.kind, settings.linkTransport)
            settings.recordProvenConnect(
                linkKind = link.kind.name,
                transport = transport,
                usbDeviceId = usbDeviceIdFromDetail(link.detail),
                vin = vin,
                protocolConfirmed = settings.vciProtocolConfirmed ||
                    link.kind == LinkKind.OEM_USB ||
                    link.kind == LinkKind.OEM_BT,
            )
        }.onFailure { err ->
            Log.w(TAG, "persistProvenConnect skipped: ${err.message}")
        }
    }

    internal fun transportKeyFor(kind: LinkKind, currentSetting: String): String = when (kind) {
        LinkKind.ELM327_USB -> "elm327_usb"
        LinkKind.OEM_USB -> "oem_usb"
        LinkKind.OEM_BT -> "oem_bt"
        LinkKind.ELM327_BT -> "elm327_bt"
    }.let { mapped ->
        if (currentSetting.equals("auto", ignoreCase = true)) "auto" else mapped
    }

    internal fun usbDeviceIdFromDetail(detail: String): String? {
        val atIdx = detail.indexOf('@')
        if (atIdx >= 0) {
            return detail.substring(atIdx + 1).trim().takeIf { it.isNotBlank() }
        }
        return detail.substringAfter("device=", "").trim().takeIf { it.isNotBlank() }
    }

    private suspend fun connectAuto(
        context: Context,
        settings: SettingsRepo,
        usbDevice: UsbDevice?,
    ): Result<ActiveLink> {
        val pending = usbDevice ?: VciUsbAttachState.consumePending()
        val usbReady = pending != null || ObdUsbTool(context).listDevices().isNotEmpty()
        if (usbReady) {
            var lastUsbError: Throwable? = null
            var permissionRetried = false
            for (attempt in autoUsbAttemptOrder(settings)) {
                when (attempt) {
                    AutoAttempt.OEM_USB -> connectOemUsb(context, settings, pending).fold(
                        onSuccess = { return Result.success(it) },
                        onFailure = { lastUsbError = it },
                    )
                    AutoAttempt.ELM327_USB -> connectElm327Usb(context, pending).fold(
                        onSuccess = { return Result.success(it) },
                        onFailure = { lastUsbError = it },
                    )
                    else -> Unit
                }
            }
            if (!permissionRetried && isUsbPermissionPending(lastUsbError)) {
                Log.i(TAG, "USB permission pending — awaiting grant for single retry")
                if (VciUsbAttachState.awaitPermissionGrant(5_000L)) {
                    permissionRetried = true
                    delay(300)
                    for (attempt in autoUsbAttemptOrder(settings)) {
                        when (attempt) {
                            AutoAttempt.OEM_USB -> connectOemUsb(context, settings, pending).fold(
                                onSuccess = { return Result.success(it) },
                                onFailure = { lastUsbError = it },
                            )
                            AutoAttempt.ELM327_USB -> connectElm327Usb(context, pending).fold(
                                onSuccess = { return Result.success(it) },
                                onFailure = { lastUsbError = it },
                            )
                            else -> Unit
                        }
                    }
                }
            }
            if (!settings.bluetoothTransportEnabled) {
                return Result.failure(
                    IllegalStateException(
                        "USB failed (OEM VCI + ELM327). Enable Bluetooth in the connection drawer if needed. ${lastUsbError?.message}",
                    ),
                )
            }
        }
        if (!settings.bluetoothTransportEnabled) {
            return Result.failure(
                IllegalStateException(
                    "Plug in a USB OBD cable, or enable Bluetooth in the connection drawer.",
                ),
            )
        }
        connectElm327Bt(settings).fold(
            onSuccess = { return Result.success(it) },
            onFailure = { /* fall through */ },
        )
        return connectOemBt(context, settings)
    }

    internal fun autoUsbAttemptOrder(settings: SettingsRepo): List<AutoAttempt> =
        if (VciProtocolConfig.preferOemVciTransport(settings)) {
            listOf(AutoAttempt.OEM_USB, AutoAttempt.ELM327_USB)
        } else {
            listOf(AutoAttempt.ELM327_USB, AutoAttempt.OEM_USB)
        }

    private fun gateOemVciPath(settings: SettingsRepo): Result<Unit> {
        if (VciProtocolConfig.directVciAllowed(settings)) return Result.success(Unit)
        return Result.failure(IllegalStateException(VciProtocolConfig.oemVciGateMessage()))
    }

    private fun isUsbPermissionPending(error: Throwable?): Boolean {
        val msg = error?.message.orEmpty().lowercase()
        return msg.contains("permission") && (msg.contains("requested") || msg.contains("not granted"))
    }

    private suspend fun connectElm327Usb(context: Context, usbDevice: UsbDevice?): Result<ActiveLink> {
        val tool = ObdUsbTool(context)
        return tool.connect(usbDevice).map { detail ->
            val eng = tool.engineOrNull()!!
            val port = ObdEngineDriver(eng)
            ActiveLink(
                kind = LinkKind.ELM327_USB,
                port = port,
                detail = detail,
                disconnect = { tool.disconnect() },
                readVin = { eng.readVin() },
                elmEngine = eng,
            )
        }
    }

    private suspend fun connectOemUsb(
        context: Context,
        settings: SettingsRepo,
        usbDevice: UsbDevice?,
    ): Result<ActiveLink> {
        gateOemVciPath(settings).getOrElse { return Result.failure(it) }
        val pending = usbDevice ?: VciUsbAttachState.consumePending()
        val usb = OemUsbVciClient(
            context,
            useHexEncoding = settings.vciUseHexEncoding,
            socketTimeoutMs = OemUsbVciClient.socketTimeoutFor(settings.vciProtocolConfirmed),
        )
        val r = if (pending != null) usb.connect(pending) else usb.connectFirstAvailable()
        return r.map {
            val comm = VciCommunicator(usb)
            ActiveLink(
                kind = LinkKind.OEM_USB,
                port = VciDiagnosticAdapter(comm),
                detail = "OEM VCI USB",
                disconnect = { usb.close() },
                readVin = { comm.readVin().getOrNull() },
            )
        }
    }

    private suspend fun connectOemBt(context: Context, settings: SettingsRepo): Result<ActiveLink> {
        gateOemVciPath(settings).getOrElse { return Result.failure(it) }
        if (!settings.bluetoothTransportEnabled) {
            return Result.failure(
                IllegalStateException("Bluetooth is off — enable it in the connection drawer first"),
            )
        }
        return VciConnector.connect(context, settings, modeOverride = VciConnector.Mode.BLUETOOTH).map { r ->
            val comm = VciCommunicator(r.transport)
            ActiveLink(
                kind = LinkKind.OEM_BT,
                port = VciDiagnosticAdapter(comm),
                detail = r.detail,
                disconnect = { r.transport.close() },
                readVin = { comm.readVin().getOrNull() },
            )
        }
    }

    private suspend fun connectElm327Bt(settings: SettingsRepo): Result<ActiveLink> {
        if (!settings.bluetoothTransportEnabled) {
            return Result.failure(
                IllegalStateException("Bluetooth is off — enable it in the connection drawer first"),
            )
        }
        val msg = ObdBluetoothTool.scanAndConnect(settings.vciSelectedBtAddress)
        if (msg.startsWith("Error")) return Result.failure(IllegalStateException(msg))
        val eng = ObdBluetoothTool.engineOrNull()
            ?: return Result.failure(IllegalStateException("ELM327 Bluetooth engine not ready"))
        val port = ObdEngineDriver(eng)
        return Result.success(
            ActiveLink(
                kind = LinkKind.ELM327_BT,
                port = port,
                detail = msg,
                disconnect = { ObdBluetoothTool.disconnect() },
                readVin = { eng.readVin() },
                elmEngine = eng,
            ),
        )
    }

    /** Quick ELM327 vs OEM VCI probe on an open USB serial port (used by attach handler). */
    suspend fun detectUsbKind(context: Context, device: UsbDevice): LinkKind? {
        val elm = ObdUsbTool(context)
        if (elm.probeOnly(device)) {
            elm.disconnect()
            kotlinx.coroutines.delay(500)
            return LinkKind.ELM327_USB
        }
        elm.disconnect()
        kotlinx.coroutines.delay(500)
        return runCatching {
            withTimeout(1_200L) {
                val usb = OemUsbVciClient(
                    context,
                    socketTimeoutMs = OemUsbVciClient.PROBE_SOCKET_TIMEOUT_MS,
                )
                usb.connect(device).getOrThrow()
                usb.close()
                LinkKind.OEM_USB
            }
        }.getOrNull()
    }

}
