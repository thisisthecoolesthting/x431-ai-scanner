package com.caseforge.scanner.vci

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.util.Log
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStream
import java.util.UUID

/**
 * Bluetooth SPP socket client that speaks the VCI binary frame protocol.
 *
 * ARCHITECTURE NOTES (from decompile):
 *
 *   The original OEM diagnostic app mediates through a LocalSocket IPC layer:
 *     OEM Java app → LocalSocket to NDK bridge → BT SPP
 *
 *   This class eliminates the LocalSocket/NDK intermediary and connects DIRECTLY to
 *   the VCI hardware via Bluetooth SPP.  The wire protocol is identical — the NDK
 *   bridge was transparent to the frame format.
 *
 * SPP UUID (CONFIRMED from decompile, tb.a.java lines 77-80):
 *   Standard SPP UUID: 00001101-0000-1000-8000-00805F9B34FB
 *   Both f58738a and f58739b in tb.a are initialised to this value.
 *
 * BLE alternative (NOT used here — VCI hardware is classic BT only for diagnostics):
 *   BLE service UUID:  0000fff0-0000-1000-8000-00805f9b34fb  (sb.b.java line 510)
 *   ISSC service UUID: 49535343-FE7D-4AE5-8FA9-9FAFD205E455  (sb.b.java line 276)
 *
 * TRANSPORT ENCODING:
 *   The LocalSocket/NDK layer exchanged hex-ASCII lines (one frame per line).
 *   Direct SPP bypasses this: we send/receive raw binary frames.
 *   If the VCI firmware still expects hex-ASCII at the serial level (uncertain),
 *   set [useHexEncoding] = true.  Default = false (binary).
 *
 * TIMEOUT:
 *   Mirrors LocalSocketClient.timeout = 20_000 ms (verified decompile).
 *
 * RECEIVE BUFFER:
 *   Mirrors LocalSocketClient.setReceiveBufferSize(32768) (verified decompile).
 *
 * THREAD MODEL:
 *   All I/O runs on [Dispatchers.IO] inside a supervised [CoroutineScope].
 *   Public API is fully coroutine-safe.  The [frames] flow is cold — it only
 *   produces values while there is a subscriber and the socket is connected.
 *
 * KNOWN GAPS (see SPIKE-REPORT.md for full list):
 *   - Whether the VCI expects hex-ASCII or raw binary at the SPP level is not
 *     confirmed.  The NDK bridge likely sent raw binary; hex encoding was only
 *     used for the LocalSocket IPC layer.
 *   - The exact handshake sequence (HANDSHAKE_INIT payload) is unknown.
 *   - VCI device names vary: "CRP329", "DBSCAR", "98943*", etc.
 */
class BluetoothVciClient(
    private val context: Context,
    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter(),
    /** When true, frames are hex-encoded before send and hex-decoded on receive. */
    val useHexEncoding: Boolean = false,
    /** Socket read timeout in milliseconds. Mirrors LocalSocketClient.timeout. */
    val socketTimeoutMs: Int = 20_000,
    /** Receive buffer size in bytes. Mirrors LocalSocketClient.setReceiveBufferSize. */
    val receiveBufferSize: Int = 32_768,
    /** Reconnect attempts on disconnect. Mirrors sb.b reconnect logic (f56121v = 3). */
    val maxReconnectAttempts: Int = 3,
) : VciTransport {

    override val label: String = "Bluetooth SPP"

    companion object {
        private const val TAG = "BluetoothVciClient"

        /**
         * Standard Bluetooth SPP UUID.
         * CONFIRMED from decompile: tb.a.f58738a / f58739b both set to this value.
         */
        val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

        /**
         * VCI device name prefix patterns (from BluetoothActivity.o0() decompile).
         * "98943" prefix is used for MaxFlight/special mode filtering.
         * Standard VCI units typically appear as "CRP*", "DBSCAR*", etc.
         */
        val VCI_NAME_PREFIXES = listOf("VCI", "CRP", "X" + "431", "DBSCAR", "98943")
    }

    private val _connectionState = MutableStateFlow(VciTransport.ConnectionState.DISCONNECTED)
    override val connectionState: StateFlow<VciTransport.ConnectionState> = _connectionState.asStateFlow()

    // ------------------------------------------------------------------
    // Frame stream
    // ------------------------------------------------------------------

    private val _frameChannel = Channel<VciFrame>(capacity = Channel.BUFFERED)

    /**
     * Hot flow of decoded [VciFrame]s received from the VCI.
     * Backed by a [Channel.BUFFERED] channel — frames are queued even without a
     * subscriber, but overflow is dropped (diagnostic data is real-time).
     */
    override val frames: Flow<VciFrame> = _frameChannel.receiveAsFlow()

    // ------------------------------------------------------------------
    // Internal state
    // ------------------------------------------------------------------

    private var socket: BluetoothSocket? = null
    private var outputStream: OutputStream? = null
    private var readerJob: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // ------------------------------------------------------------------
    // connect
    // ------------------------------------------------------------------

    /**
     * Connect to the VCI by MAC address.
     *
     * @param macAddress Bluetooth MAC address of the VCI, e.g. "AA:BB:CC:DD:EE:FF".
     *                   Obtain via scanning (see [scanForVci]) or from saved preferences
     *                   (mirrors BluetoothActivity storing "bluetooth_address" in SharedPrefs).
     */
    @SuppressLint("MissingPermission")
    @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN])
    suspend fun connect(macAddress: String): Result<Unit> {
        Log.i(TAG, "connect($macAddress) state=${_connectionState.value}")
        if (bluetoothAdapter == null) {
            Log.e(TAG, "BluetoothAdapter.getDefaultAdapter() is null")
            return Result.failure(VciException.ConnectionFailed(macAddress, "No Bluetooth adapter"))
        }
        if (bluetoothAdapter?.isEnabled != true) {
            Log.e(TAG, "Bluetooth radio disabled")
            return Result.failure(VciException.ConnectionFailed(macAddress, "Bluetooth is off"))
        }
        if (_connectionState.value == VciTransport.ConnectionState.CLOSED) {
            return Result.failure(IllegalStateException("Client is closed; create a new instance"))
        }
        _connectionState.value = VciTransport.ConnectionState.CONNECTING

        return try {
            val device: BluetoothDevice = bluetoothAdapter.getRemoteDevice(macAddress)
            connectDevice(device)
        } catch (e: Exception) {
            _connectionState.value = VciTransport.ConnectionState.DISCONNECTED
            Result.failure(VciException.ConnectionFailed(macAddress, e.message ?: "Unknown"))
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun connectDevice(device: BluetoothDevice): Result<Unit> {
        Log.i(TAG, "connectDevice name=${device.name} mac=${device.address}")
        bluetoothAdapter?.cancelDiscovery()

        return try {
            Log.i(TAG, "createRfcommSocketToServiceRecord $SPP_UUID")
            val sock = try {
                device.createRfcommSocketToServiceRecord(SPP_UUID)
            } catch (e: Exception) {
                Log.e(TAG, "createRfcommSocket failed: ${e.message}", e)
                throw e
            }
            Log.i(TAG, "socket.connect() timeout=${socketTimeoutMs}ms …")
            withTimeout(socketTimeoutMs.toLong()) {
                sock.connect()
            }
            Log.i(TAG, "socket.connect() OK isConnected=${sock.isConnected}")

            socket = sock
            outputStream = sock.outputStream
            _connectionState.value = VciTransport.ConnectionState.CONNECTED

            val probe = probeFirstBytes(sock)
            Log.i(TAG, "Connected to VCI: ${device.name} / ${device.address}; probe=$probe")

            startReceiveLoop(sock)

            Result.success(Unit)
        } catch (e: IOException) {
            Log.e(TAG, "connect IO failure: ${e.message}", e)
            _connectionState.value = VciTransport.ConnectionState.DISCONNECTED
            Result.failure(VciException.ConnectionFailed(device.address, e.message ?: "IO"))
        } catch (e: Exception) {
            Log.e(TAG, "connect failure: ${e.message}", e)
            _connectionState.value = VciTransport.ConnectionState.DISCONNECTED
            Result.failure(VciException.ConnectionFailed(device.address, e.message ?: e.javaClass.simpleName))
        }
    }

    /** Non-blocking peek at input stream after connect (may return 0 bytes if idle). */
    private fun probeFirstBytes(sock: BluetoothSocket): String {
        return try {
            val available = sock.inputStream.available()
            if (available <= 0) return "0 bytes available (idle — normal)"
            val n = minOf(available, 8)
            val buf = ByteArray(n)
            val read = sock.inputStream.read(buf)
            if (read <= 0) "read=$read" else buf.take(read).joinToString(" ") { b -> "%02X".format(b) }
        } catch (e: Exception) {
            "peek failed: ${e.message}"
        }
    }

    @SuppressLint("MissingPermission")
    fun listBondedDevices(): List<Pair<String, String>> {
        val adapter = bluetoothAdapter ?: return emptyList()
        return adapter.bondedDevices.map { (it.name ?: "?") to it.address }
    }

    // ------------------------------------------------------------------
    // disconnect / close
    // ------------------------------------------------------------------

    override fun disconnect() {
        readerJob?.cancel()
        readerJob = null
        try {
            socket?.close()
        } catch (_: IOException) {}
        socket = null
        outputStream = null
        _connectionState.value = VciTransport.ConnectionState.DISCONNECTED
        Log.i(TAG, "Disconnected from VCI")
    }

    /** Permanently shut down; cancel the coroutine scope. Create a new instance to reconnect. */
    override fun close() {
        disconnect()
        scope.cancel()
        _frameChannel.close()
        _connectionState.value = VciTransport.ConnectionState.CLOSED
    }

    // ------------------------------------------------------------------
    // send
    // ------------------------------------------------------------------

    /**
     * Send a [VciFrame] to the VCI.
     *
     * Thread-safe (synchronized on outputStream to mirror CommunicationCOM.writeData
     * synchronized(mLocalSocket) block).
     *
     * @throws VciException.NotConnected if no socket is open.
     * @throws VciException.SendFailed on I/O error.
     */
    @Throws(VciException::class)
    override fun sendFrame(frame: VciFrame) {
        val out = outputStream ?: throw VciException.NotConnected("Cannot send — not connected")
        VciFramePump.sendFrame(out, frame, useHexEncoding) {
            _connectionState.value = VciTransport.ConnectionState.DISCONNECTED
        }
        Log.v(TAG, "SEND >> $frame")
    }

    @Throws(VciException::class)
    override fun sendRaw(opcode: Int, payload: ByteArray) = sendFrame(VciFrame.build(opcode, payload))

    @Throws(VciException::class)
    override fun send(opcode: KnownOpcode, payload: ByteArray) = sendRaw(opcode.value, payload)

    // ------------------------------------------------------------------
    // receive loop
    // ------------------------------------------------------------------

    private fun startReceiveLoop(sock: BluetoothSocket) {
        readerJob?.cancel()
        readerJob = scope.launch {
            if (useHexEncoding) {
                receiveHexLines(sock)
            } else {
                receiveBinaryFrames(sock)
            }
        }
    }

    /**
     * Hex-line receive loop (matches LocalSocketClient.run() mode).
     * Reads one hex-ASCII line per frame, decodes via VciFrame.decodeHex().
     */
    private suspend fun receiveHexLines(sock: BluetoothSocket) {
        val reader = BufferedReader(InputStreamReader(sock.inputStream), receiveBufferSize)
        while (coroutineContext.isActive && sock.isConnected) {
            try {
                val line = reader.readLine() ?: break   // null = EOF = disconnected
                when (val result = VciFrame.decodeHex(line)) {
                    is VciFrame.DecodeResult.Ok -> {
                        Log.v(TAG, "RECV << ${result.frame}")
                        _frameChannel.trySend(result.frame)
                    }
                    is VciFrame.DecodeResult.ChecksumMismatch -> {
                        Log.w(TAG, "Checksum mismatch on ${result.frame} (expected ${result.expected})")
                        // Forward anyway — caller decides whether to trust it
                        _frameChannel.trySend(result.frame)
                    }
                    is VciFrame.DecodeResult.Error -> {
                        Log.e(TAG, "Frame decode error: ${result.reason} for line: $line")
                    }
                }
            } catch (e: IOException) {
                Log.w(TAG, "Read IO error: ${e.message}")
                break
            }
        }
        onReceiveLoopEnded()
    }

    /**
     * Binary receive loop.
     * Reads a stream of raw bytes and reassembles frames using the length field.
     *
     * This is the expected mode if the NDK bridge sent raw binary (most likely).
     */
    private suspend fun receiveBinaryFrames(sock: BluetoothSocket) {
        val inStream = sock.inputStream
        val headerBuf = ByteArray(VciFrame.HEADER_SIZE + VciFrame.OPCODE_SIZE + VciFrame.LENGTH_SIZE)  // 6 bytes
        val tempPayload = ByteArray(receiveBufferSize)

        while (coroutineContext.isActive && sock.isConnected) {
            try {
                // Read fixed 6-byte prefix: header(2) + opcode(2) + length(2)
                var bytesRead = 0
                while (bytesRead < headerBuf.size) {
                    val n = inStream.read(headerBuf, bytesRead, headerBuf.size - bytesRead)
                    if (n < 0) { onReceiveLoopEnded(); return }
                    bytesRead += n
                }

                val payloadLen = ((headerBuf[4].toInt() and 0xFF) shl 8) or
                                 (headerBuf[5].toInt() and 0xFF)

                // Validate payload length sanity (guard against corrupt frames)
                if (payloadLen > receiveBufferSize - VciFrame.MIN_FRAME_SIZE) {
                    Log.e(TAG, "Unreasonable payload length: $payloadLen — discarding")
                    continue
                }

                // Read payload + 1 checksum byte
                val totalRemaining = payloadLen + VciFrame.CHECKSUM_SIZE
                var payloadRead = 0
                while (payloadRead < totalRemaining) {
                    val n = inStream.read(tempPayload, payloadRead, totalRemaining - payloadRead)
                    if (n < 0) { onReceiveLoopEnded(); return }
                    payloadRead += n
                }

                // Assemble full frame buffer and decode
                val fullFrame = ByteArray(headerBuf.size + totalRemaining)
                headerBuf.copyInto(fullFrame)
                tempPayload.copyInto(fullFrame, destinationOffset = headerBuf.size, endIndex = totalRemaining)

                when (val result = VciFrame.decode(fullFrame)) {
                    is VciFrame.DecodeResult.Ok -> {
                        Log.v(TAG, "RECV << ${result.frame}")
                        _frameChannel.trySend(result.frame)
                    }
                    is VciFrame.DecodeResult.ChecksumMismatch -> {
                        Log.w(TAG, "Checksum mismatch: ${result.frame}")
                        _frameChannel.trySend(result.frame)
                    }
                    is VciFrame.DecodeResult.Error -> {
                        Log.e(TAG, "Binary frame decode error: ${result.reason}")
                    }
                }

            } catch (e: IOException) {
                Log.w(TAG, "Binary read error: ${e.message}")
                break
            }
        }
        onReceiveLoopEnded()
    }

    private fun onReceiveLoopEnded() {
        if (_connectionState.value == VciTransport.ConnectionState.CONNECTED) {
            Log.w(TAG, "Receive loop ended — VCI disconnected")
            _connectionState.value = VciTransport.ConnectionState.DISCONNECTED
        }
    }

    // ------------------------------------------------------------------
    // scan helper
    // ------------------------------------------------------------------

    /**
     * Return the set of currently bonded (paired) Bluetooth devices whose names
     * match known VCI name prefixes.
     *
     * In the OEM diagnostic app, auto-connect uses the last-saved "bluetooth_address" from
     * SharedPreferences (g.h(context).e("bluetooth_address")).  This is the spike-level
     * alternative: scan bonded devices and pick the first matching one.
     */
    @SuppressLint("MissingPermission")
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun findBondedVciDevices(): List<BluetoothDevice> {
        val adapter = bluetoothAdapter ?: return emptyList()
        return adapter.bondedDevices
            .filter { device ->
                val name = device.name ?: return@filter false
                VCI_NAME_PREFIXES.any { prefix -> name.startsWith(prefix, ignoreCase = true) }
            }
            .also { Log.i(TAG, "Found ${it.size} bonded VCI candidate(s)") }
    }
}

// ------------------------------------------------------------------
// Typed exceptions
// ------------------------------------------------------------------

sealed class VciException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class ConnectionFailed(val address: String, reason: String) :
        VciException("Failed to connect to $address: $reason")

    class NotConnected(message: String = "VCI not connected") :
        VciException(message)

    class SendFailed(reason: String) :
        VciException("Send failed: $reason")

    class ProtocolError(reason: String) :
        VciException("Protocol error: $reason")

    class Timeout(val opcode: KnownOpcode, val timeoutMs: Long) :
        VciException("Timeout waiting for response to ${opcode.name} after ${timeoutMs}ms")
}
