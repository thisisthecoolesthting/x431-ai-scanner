package com.caseforge.scanner.vci

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.util.Log
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.OutputStream

/**
 * USB OTG serial transport (CDC-ACM / FTDI / CH340 / PL2303 / CP21xx via usb-serial-for-android).
 */
class OemUsbVciClient(
    private val context: Context,
    val useHexEncoding: Boolean = false,
    val baudRate: Int = 115200,
    val socketTimeoutMs: Int = 20_000,
    val receiveBufferSize: Int = 32_768,
) : VciTransport {

    companion object {
        private const val TAG = "OemUsbVciClient"
        const val ACTION_USB_PERMISSION = "com.caseforge.scanner.USB_PERMISSION"
    }

    override val label: String = "USB OTG"

    private val _connectionState = MutableStateFlow(VciTransport.ConnectionState.DISCONNECTED)
    override val connectionState: StateFlow<VciTransport.ConnectionState> = _connectionState.asStateFlow()

    private val _frameChannel = Channel<VciFrame>(capacity = Channel.BUFFERED)
    override val frames: Flow<VciFrame> = _frameChannel.receiveAsFlow()

    private var serialPort: UsbSerialPort? = null
    private var usbOut: OutputStream? = null
    private var readerJob: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

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

    suspend fun connect(device: UsbDevice): Result<Unit> = withContext(Dispatchers.IO) {
        if (_connectionState.value == VciTransport.ConnectionState.CLOSED) {
            return@withContext Result.failure(IllegalStateException("Client is closed"))
        }
        _connectionState.value = VciTransport.ConnectionState.CONNECTING
        val manager = context.getSystemService(Context.USB_SERVICE) as UsbManager
        if (!manager.hasPermission(device)) {
            _connectionState.value = VciTransport.ConnectionState.DISCONNECTED
            return@withContext Result.failure(
                VciException.ConnectionFailed(device.deviceName, "USB permission not granted — accept the system prompt"),
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
            usbOut = UsbSerialOutputStream(port)
            _connectionState.value = VciTransport.ConnectionState.CONNECTED
            startReceiveLoop(port)
            Log.i(TAG, "USB connected vid=${device.vendorId} pid=${device.productId}")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "USB connect failed: ${e.message}", e)
            _connectionState.value = VciTransport.ConnectionState.DISCONNECTED
            Result.failure(VciException.ConnectionFailed(device.deviceName, e.message ?: "USB connect failed"))
        }
    }

    suspend fun connectFirstAvailable(preferred: UsbDevice? = null): Result<Unit> {
        val candidates = buildList {
            preferred?.let { add(it) }
            listAttachedDevices().forEach { if (it !in this) add(it) }
        }
        if (candidates.isEmpty()) {
            return Result.failure(VciException.ConnectionFailed("usb", "No USB serial device attached"))
        }
        var last: Throwable? = null
        for (dev in candidates) {
            connect(dev).fold(
                onSuccess = { return Result.success(Unit) },
                onFailure = { last = it },
            )
        }
        return Result.failure(last ?: VciException.ConnectionFailed("usb", "No USB device connected"))
    }

    private fun startReceiveLoop(port: UsbSerialPort) {
        readerJob?.cancel()
        val input = UsbSerialInputStream(port)
        readerJob = VciFramePump.startReceiveJob(
            scope = scope,
            inputStream = input,
            useHexEncoding = useHexEncoding,
            receiveBufferSize = receiveBufferSize,
            isActiveLink = { _connectionState.value == VciTransport.ConnectionState.CONNECTED },
            frameChannel = _frameChannel,
            onDisconnected = {
                if (_connectionState.value == VciTransport.ConnectionState.CONNECTED) {
                    _connectionState.value = VciTransport.ConnectionState.DISCONNECTED
                }
            },
        )
    }

    override fun disconnect() {
        readerJob?.cancel()
        readerJob = null
        usbOut = null
        runCatching { serialPort?.close() }
        serialPort = null
        _connectionState.value = VciTransport.ConnectionState.DISCONNECTED
        Log.i(TAG, "USB disconnected")
    }

    override fun close() {
        disconnect()
        scope.cancel()
        _frameChannel.close()
        _connectionState.value = VciTransport.ConnectionState.CLOSED
    }

    override fun sendFrame(frame: VciFrame) {
        val port = serialPort ?: throw VciException.NotConnected("USB not connected")
        val out = usbOut ?: throw VciException.NotConnected("USB output stream null")
        VciFramePump.sendFrame(out, frame, useHexEncoding) {
            _connectionState.value = VciTransport.ConnectionState.DISCONNECTED
        }
    }

    override fun sendRaw(opcode: Int, payload: ByteArray) = sendFrame(VciFrame.build(opcode, payload))

    override fun send(opcode: KnownOpcode, payload: ByteArray) = sendRaw(opcode.value, payload)
}
