package com.caseforge.scanner.vci.transport

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.util.Log
import com.caseforge.scanner.vci.UsbSerialInputStream
import com.caseforge.scanner.vci.UsbSerialOutputStream
import com.caseforge.scanner.vci.VciException
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.InputStream
import java.io.OutputStream

/**
 * Low-level USB serial read/write shared by Launch VCI ([com.caseforge.scanner.vci.VciUsbClient])
 * and ELM327 USB-OBD ([com.caseforge.scanner.agent.ObdUsbTool]).
 */
class UsbSerialTransport(
    private val context: Context,
    val baudRate: Int = 115200,
    val socketTimeoutMs: Int = 20_000,
) {
    companion object {
        private const val TAG = "UsbSerialTransport"
        const val ACTION_USB_PERMISSION = "com.caseforge.scanner.USB_PERMISSION"
    }

    private val ioMutex = Mutex()

    var serialPort: UsbSerialPort? = null
        private set
    var inputStream: InputStream? = null
        private set
    var outputStream: OutputStream? = null
        private set
    var connectedDevice: UsbDevice? = null
        private set

    val isOpen: Boolean get() = serialPort != null

    fun listAttachedDevices(): List<UsbDevice> {
        val manager = context.getSystemService(Context.USB_SERVICE) as UsbManager
        return UsbSerialProber.getDefaultProber().findAllDrivers(manager).map { it.device }
    }

    fun hasPermission(device: UsbDevice): Boolean {
        val manager = context.getSystemService(Context.USB_SERVICE) as UsbManager
        return manager.hasPermission(device)
    }

    fun requestPermission(device: UsbDevice) {
        val manager = context.getSystemService(Context.USB_SERVICE) as UsbManager
        val intent = PendingIntent.getBroadcast(
            context,
            0,
            Intent(ACTION_USB_PERMISSION),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        manager.requestPermission(device, intent)
    }

    suspend fun open(device: UsbDevice): Result<Unit> = withContext(Dispatchers.IO) {
        close()
        val manager = context.getSystemService(Context.USB_SERVICE) as UsbManager
        if (!manager.hasPermission(device)) {
            return@withContext Result.failure(
                VciException.ConnectionFailed(device.deviceName, "USB permission not granted"),
            )
        }
        val driver = UsbSerialProber.getDefaultProber().probeDevice(device)
            ?: return@withContext Result.failure(
                VciException.ConnectionFailed(device.deviceName, "No USB serial driver for this device"),
            )
        try {
            val connection = manager.openDevice(device)
                ?: return@withContext Result.failure(
                    VciException.ConnectionFailed(device.deviceName, "openDevice returned null"),
                )
            val port = driver.ports.firstOrNull()
                ?: return@withContext Result.failure(
                    VciException.ConnectionFailed(device.deviceName, "No serial port on device"),
                )
            withTimeout(socketTimeoutMs.toLong()) {
                port.open(connection)
                port.setParameters(baudRate, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
            }
            serialPort = port
            inputStream = UsbSerialInputStream(port)
            outputStream = UsbSerialOutputStream(port)
            connectedDevice = device
            Log.i(TAG, "USB serial open vid=${device.vendorId} pid=${device.productId}")
            Result.success(Unit)
        } catch (e: Exception) {
            close()
            Result.failure(
                VciException.ConnectionFailed(device.deviceName, e.message ?: "USB open failed"),
            )
        }
    }

    suspend fun openFirstAvailable(preferred: UsbDevice? = null): Result<UsbDevice> {
        val candidates = buildList {
            preferred?.let { add(it) }
            listAttachedDevices().forEach { if (it !in this) add(it) }
        }
        if (candidates.isEmpty()) {
            return Result.failure(VciException.ConnectionFailed("usb", "No USB serial device attached"))
        }
        var last: Throwable? = null
        for (dev in candidates) {
            open(dev).fold(
                onSuccess = { return Result.success(dev) },
                onFailure = { last = it },
            )
        }
        return Result.failure(last ?: VciException.ConnectionFailed("usb", "No USB device connected"))
    }

    /** ELM327 / AT command line I/O (ends on `>` prompt). */
    suspend fun sendAtCommand(cmd: String, timeoutMs: Long = 4_000L): String = ioMutex.withLock {
        val out = outputStream ?: throw IllegalStateException("USB serial not open")
        val inp = inputStream ?: throw IllegalStateException("USB serial not open")
        out.write((cmd + "\r").toByteArray(Charsets.US_ASCII))
        out.flush()
        val sb = StringBuilder()
        val deadline = System.currentTimeMillis() + timeoutMs
        val buf = ByteArray(128)
        while (System.currentTimeMillis() < deadline) {
            if (inp.available() > 0) {
                val n = inp.read(buf)
                if (n > 0) {
                    sb.append(String(buf, 0, n, Charsets.US_ASCII))
                    if (sb.indexOf('>') >= 0) break
                }
            } else {
                try {
                    Thread.sleep(15)
                } catch (_: InterruptedException) {
                }
            }
        }
        sb.toString().replace(">", "").trim()
    }

    fun close() {
        inputStream = null
        outputStream = null
        runCatching { serialPort?.close() }
        serialPort = null
        connectedDevice = null
    }
}
