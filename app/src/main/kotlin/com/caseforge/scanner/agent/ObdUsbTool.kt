package com.caseforge.scanner.agent

import android.content.Context
import android.hardware.usb.UsbDevice
import com.caseforge.scanner.vci.transport.UsbSerialTransport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * ELM327 over USB OTG (generic OBD-II cable). Primary workshop transport (task 206).
 */
class ObdUsbTool(private val context: Context) {

    private val transport = UsbSerialTransport(context)
    private var engine: ObdElmEngine? = null

    val isConnected: Boolean get() = transport.isOpen && engine != null
    val deviceLabel: String?
        get() = transport.connectedDevice?.let { "USB ${it.deviceName} (vid=${it.vendorId})" }

    private val elmIo = object : ElmIo {
        override suspend fun sendRaw(cmd: String): String = transport.sendAtCommand(cmd)
    }

    suspend fun connect(usbDevice: UsbDevice? = null): Result<String> = withContext(Dispatchers.IO) {
        disconnect()
        val devResult = if (usbDevice != null) {
            transport.open(usbDevice).map { usbDevice }
        } else {
            transport.openFirstAvailable()
        }
        devResult.fold(
            onSuccess = { dev ->
                val eng = ObdElmEngine(elmIo)
                if (!eng.probeElm327()) {
                    transport.close()
                    return@withContext Result.failure(
                        IllegalStateException("Device on USB is not ELM327 (no ATZ response)"),
                    )
                }
                eng.initialize().fold(
                    onSuccess = { msg ->
                        engine = eng
                        Result.success("USB OBD cable: $msg @ ${dev.deviceName}")
                    },
                    onFailure = {
                        transport.close()
                        Result.failure(it)
                    },
                )
            },
            onFailure = { Result.failure(it) },
        )
    }

    suspend fun probeOnly(usbDevice: UsbDevice): Boolean = withContext(Dispatchers.IO) {
        transport.open(usbDevice).getOrNull() ?: return@withContext false
        val ok = ObdElmEngine(elmIo).probeElm327()
        if (!ok) transport.close()
        ok
    }

    fun engineOrNull(): ObdElmEngine? = engine

    fun disconnect() {
        engine = null
        transport.close()
    }

    fun listDevices(): List<UsbDevice> = transport.listAttachedDevices()

    fun hasPermission(device: UsbDevice): Boolean = transport.hasPermission(device)

    fun requestPermission(device: UsbDevice) = transport.requestPermission(device)
}
