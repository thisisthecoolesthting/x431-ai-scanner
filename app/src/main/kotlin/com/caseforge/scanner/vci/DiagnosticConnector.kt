package com.caseforge.scanner.vci

import android.content.Context
import android.hardware.usb.UsbDevice
import com.caseforge.scanner.App
import com.caseforge.scanner.agent.ObdBluetoothTool
import com.caseforge.scanner.agent.ObdUsbTool
import com.caseforge.scanner.data.SettingsRepo
import com.caseforge.scanner.engine.ObdEngineDriver
import com.caseforge.scanner.engine.VciDiagnosticPort
import com.caseforge.scanner.vci.transport.UsbSerialTransport
import kotlinx.coroutines.withTimeout

/**
 * Resolves the active vehicle link: ELM327 USB (primary), OEM VCI USB, optional Bluetooth paths.
 */
object DiagnosticConnector {

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
    )

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
            return Result.failure(
                IllegalStateException("OEM diagnostic app is in the foreground — force-stop it to free the adapter"),
            )
        }

        val mode = userTransportFrom(settings)
        return when (mode) {
            UserTransport.ELM327_USB -> connectElm327Usb(context, usbDevice)
            UserTransport.OEM_USB -> connectOemUsb(context, settings, usbDevice)
            UserTransport.OEM_BT -> connectOemBt(context, settings)
            UserTransport.ELM327_BT -> connectElm327Bt(settings)
            UserTransport.AUTO -> connectAuto(context, settings, usbDevice)
        }
    }

    private suspend fun connectAuto(
        context: Context,
        settings: SettingsRepo,
        usbDevice: UsbDevice?,
    ): Result<ActiveLink> {
        val pending = usbDevice ?: VciUsbAttachState.consumePending()
        if (pending != null || ObdUsbTool(context).listDevices().isNotEmpty()) {
            connectElm327Usb(context, pending).fold(
                onSuccess = { return Result.success(it) },
                onFailure = { /* try OEM USB next */ },
            )
            connectOemUsb(context, settings, pending).fold(
                onSuccess = { return Result.success(it) },
                onFailure = { usbErr ->
                    if (!settings.bluetoothTransportEnabled) {
                        return Result.failure(
                            IllegalStateException(
                                "USB failed (ELM327 + OEM VCI). Enable Bluetooth in the connection drawer if needed. ${usbErr.message}",
                            ),
                        )
                    }
                },
            )
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
            )
        }
    }

    private suspend fun connectOemUsb(
        context: Context,
        settings: SettingsRepo,
        usbDevice: UsbDevice?,
    ): Result<ActiveLink> {
        val pending = usbDevice ?: VciUsbAttachState.consumePending()
        val usb = OemUsbVciClient(context, useHexEncoding = settings.vciUseHexEncoding)
        val r = if (pending != null) usb.connect(pending) else usb.connectFirstAvailable()
        return r.map {
            val comm = VciCommunicator(usb)
            ActiveLink(
                kind = LinkKind.OEM_USB,
                port = VciDiagnosticAdapter(comm),
                detail = "OEM VCI USB",
                disconnect = { usb.disconnect() },
                readVin = { comm.readVin().getOrNull() },
            )
        }
    }

    private suspend fun connectOemBt(context: Context, settings: SettingsRepo): Result<ActiveLink> {
        if (!settings.bluetoothTransportEnabled) {
            return Result.failure(
                IllegalStateException("Bluetooth is off — enable it in the connection drawer first"),
            )
        }
        val prev = settings.vciTransportMode
        settings.vciTransportMode = "bluetooth"
        val result = VciConnector.connect(context, settings).map { r ->
            val comm = VciCommunicator(r.transport)
            ActiveLink(
                kind = LinkKind.OEM_BT,
                port = VciDiagnosticAdapter(comm),
                detail = r.detail,
                disconnect = { r.transport.disconnect() },
                readVin = { comm.readVin().getOrNull() },
            )
        }
        settings.vciTransportMode = prev
        return result
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
            ),
        )
    }

    /** Quick ELM327 vs OEM VCI probe on an open USB serial port (used by attach handler). */
    suspend fun detectUsbKind(context: Context, device: UsbDevice): LinkKind? {
        val elm = ObdUsbTool(context)
        if (elm.probeOnly(device)) {
            elm.disconnect()
            return LinkKind.ELM327_USB
        }
        elm.disconnect()
        return runCatching {
            withTimeout(1_200L) {
                val usb = OemUsbVciClient(context)
                usb.connect(device).getOrThrow()
                usb.disconnect()
                LinkKind.OEM_USB
            }
        }.getOrNull()
    }

}
