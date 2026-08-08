package com.caseforge.scanner.vci

import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.hardware.usb.UsbDevice
import com.caseforge.scanner.App
import com.caseforge.scanner.agent.ObdUsbTool
import com.caseforge.scanner.data.SettingsRepo

/**
 * Resolves transport mode and connects (Auto = USB first, then Bluetooth).
 */
object VciConnector {

    enum class Mode { AUTO, USB, BLUETOOTH }

    fun modeFrom(settings: SettingsRepo): Mode = when (settings.vciTransportMode.lowercase()) {
        "usb" -> Mode.USB
        "bluetooth", "bt" -> Mode.BLUETOOTH
        else -> Mode.AUTO
    }

    data class ConnectResult(
        val transport: VciTransport,
        val detail: String,
    )

    suspend fun connect(
        context: Context,
        settings: SettingsRepo,
        usbDevice: UsbDevice? = null,
        modeOverride: Mode? = null,
    ): Result<ConnectResult> {
        VciProtocolConfig.applyFromSettings(settings)
        android.util.Log.i(
            "DiagConnect",
            "VciConnector.connect mode=${modeOverride ?: modeFrom(settings)} usb=${usbDevice?.deviceName ?: "auto"}",
        )

        if (App.isOemDiagForeground(context)) {
            val reason = App.lastOemForegroundBlockReason
                ?: "OEM diagnostic app is in the foreground — force-stop the OEM diagnostic app to free the VCI"
            settings.recordOemDiagConnectBlock(reason)
            return Result.failure(IllegalStateException(reason))
        }

        return when (modeOverride ?: modeFrom(settings)) {
            Mode.USB -> connectUsb(context, settings, usbDevice)
            Mode.BLUETOOTH -> {
                if (!settings.bluetoothTransportEnabled) {
                    return Result.failure(
                        IllegalStateException("Bluetooth is off — enable it in the connection drawer"),
                    )
                }
                connectBluetooth(context, settings)
            }
            Mode.AUTO -> {
                val preferOemUsb = VciProtocolConfig.preferOemVciTransport(settings)
                var lastUsbError: Throwable? = null

                suspend fun finishAuto(): Result<ConnectResult> {
                    if (!settings.bluetoothTransportEnabled) {
                        return Result.failure(
                            IllegalStateException(
                                "USB failed (${lastUsbError?.message}). Enable Bluetooth in the connection drawer if needed.",
                            ),
                        )
                    }
                    val btTry = connectBluetooth(context, settings)
                    if (btTry.isSuccess) {
                        return btTry.map {
                            it.copy(detail = "Auto: USB failed (${lastUsbError?.message}); ${it.detail}")
                        }
                    }
                    return Result.failure(
                        IllegalStateException(
                            "Auto: USB failed (${lastUsbError?.message}); Bluetooth failed (${btTry.exceptionOrNull()?.message})",
                        ),
                    )
                }

                suspend fun elm327FailureIfConnected(): Result<ConnectResult>? {
                    val elmTry = tryElm327Usb(context, usbDevice)
                    if (elmTry.isSuccess) {
                        return Result.failure(
                            IllegalStateException(
                                "ELM327 USB connected — set link transport to elm327_usb (VCI stack does not serve ELM327)",
                            ),
                        )
                    }
                    lastUsbError = elmTry.exceptionOrNull()
                    return null
                }

                if (preferOemUsb) {
                    connectUsb(context, settings, usbDevice).fold(
                        onSuccess = { return Result.success(it) },
                        onFailure = { lastUsbError = it },
                    )
                    elm327FailureIfConnected()?.let { return it }
                } else {
                    elm327FailureIfConnected()?.let { return it }
                    connectUsb(context, settings, usbDevice).fold(
                        onSuccess = { return Result.success(it) },
                        onFailure = { lastUsbError = it },
                    )
                }

                return finishAuto()
            }
        }
    }

    private suspend fun connectUsb(
        context: Context,
        settings: SettingsRepo,
        usbDevice: UsbDevice?,
    ): Result<ConnectResult> {
        if (!VciProtocolConfig.directVciAllowed(settings)) {
            return Result.failure(IllegalStateException(VciProtocolConfig.oemVciGateMessage()))
        }
        val usb = OemUsbVciClient(
            context,
            useHexEncoding = settings.vciUseHexEncoding,
            socketTimeoutMs = OemUsbVciClient.socketTimeoutFor(settings.vciProtocolConfirmed),
        )
        val r = if (usbDevice != null) usb.connect(usbDevice) else usb.connectFirstAvailable()
        return r.map {
            ConnectResult(usb, "USB serial @ ${usbDevice?.deviceName ?: "first attached"}")
        }
    }

    private suspend fun connectBluetooth(
        context: Context,
        settings: SettingsRepo,
    ): Result<ConnectResult> {
        if (!VciProtocolConfig.directVciAllowed(settings)) {
            return Result.failure(IllegalStateException(VciProtocolConfig.oemVciGateMessage()))
        }
        if (!VciConnectionDiagnostics.hasBluetoothConnectPermission(context)) {
            return Result.failure(
                IllegalStateException("BLUETOOTH_CONNECT not granted"),
            )
        }
        val bt = BluetoothVciClient(context, useHexEncoding = settings.vciUseHexEncoding)
        val bonded = bt.findBondedVciDevices()
        val saved = settings.vciSelectedBtAddress
        val allBonded = bt.listBondedDevices()
        val target = when {
            saved != null -> BluetoothAdapter.getDefaultAdapter()?.getRemoteDevice(saved)
            bonded.isNotEmpty() -> bonded.first()
            allBonded.size == 1 -> BluetoothAdapter.getDefaultAdapter()?.getRemoteDevice(allBonded.first().second)
            else -> null
        } ?: return Result.failure(
            IllegalStateException(
                if (allBonded.isEmpty()) "No bonded Bluetooth devices — pair VCI or use USB OTG"
                else "No VCI match — pick a device in diagnostics",
            ),
        )
        return bt.connect(target.address).map {
            ConnectResult(bt, "Bluetooth SPP ${target.name ?: target.address}")
        }
    }

    private suspend fun tryElm327Usb(context: Context, usbDevice: UsbDevice?): Result<Unit> {
        val tool = ObdUsbTool(context)
        val device = usbDevice ?: tool.listDevices().firstOrNull()
            ?: return Result.failure(IllegalStateException("No USB OBD device attached"))
        return runCatching {
            if (!tool.probeOnly(device)) {
                tool.disconnect()
                error("Not ELM327 on ${device.deviceName}")
            }
            tool.disconnect()
        }
    }
}
