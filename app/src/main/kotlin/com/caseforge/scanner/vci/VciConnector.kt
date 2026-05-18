package com.caseforge.scanner.vci

import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.hardware.usb.UsbDevice
import com.caseforge.scanner.App
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
    ): Result<ConnectResult> {
        VciProtocolConfig.applyFromSettings(settings)

        if (App.isX431Foreground(context)) {
            return Result.failure(
                IllegalStateException("X431 is in the foreground — force-stop X431 to free the VCI"),
            )
        }

        return when (modeFrom(settings)) {
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
                val usbTry = connectUsb(context, settings, usbDevice)
                if (usbTry.isSuccess) return usbTry
                val usbErr = usbTry.exceptionOrNull()
                if (!settings.bluetoothTransportEnabled) {
                    return Result.failure(
                        IllegalStateException(
                            "USB failed (${usbErr?.message}). Enable Bluetooth in the connection drawer if needed.",
                        ),
                    )
                }
                val btTry = connectBluetooth(context, settings)
                if (btTry.isSuccess) {
                    return btTry.map {
                        it.copy(detail = "Auto: USB failed (${usbErr?.message}); ${it.detail}")
                    }
                }
                Result.failure(
                    IllegalStateException(
                        "Auto: USB failed (${usbErr?.message}); Bluetooth failed (${btTry.exceptionOrNull()?.message})",
                    ),
                )
            }
        }
    }

    private suspend fun connectUsb(
        context: Context,
        settings: SettingsRepo,
        usbDevice: UsbDevice?,
    ): Result<ConnectResult> {
        val usb = VciUsbClient(context, useHexEncoding = settings.vciUseHexEncoding)
        val r = if (usbDevice != null) usb.connect(usbDevice) else usb.connectFirstAvailable()
        return r.map {
            ConnectResult(usb, "USB serial @ ${usbDevice?.deviceName ?: "first attached"}")
        }
    }

    private suspend fun connectBluetooth(
        context: Context,
        settings: SettingsRepo,
    ): Result<ConnectResult> {
        if (!VciConnectionDiagnostics.hasBluetoothConnectPermission(context)) {
            return Result.failure(
                IllegalStateException("BLUETOOTH_CONNECT not granted"),
            )
        }
        val bt = VciSocketClient(context, useHexEncoding = settings.vciUseHexEncoding)
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
}
