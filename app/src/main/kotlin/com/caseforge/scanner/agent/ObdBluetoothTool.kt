package com.caseforge.scanner.agent

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

/**
 * Bluetooth ELM327 dongle — opt-in only (see Connection drawer Bluetooth toggle).
 */
@SuppressLint("MissingPermission")
object ObdBluetoothTool {

    private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private val ioMutex = Mutex()

    @Volatile private var socket: BluetoothSocket? = null
    @Volatile private var input: InputStream? = null
    @Volatile private var output: OutputStream? = null
    @Volatile private var deviceName: String? = null
    @Volatile private var engine: ObdElmEngine? = null

    private val elmIo = object : ElmIo {
        override suspend fun sendRaw(cmd: String): String = sendRawLocked(cmd)
    }

    suspend fun scanAndConnect(targetAddress: String? = null): String = withContext(Dispatchers.IO) {
        disconnect()
        val adapter = BluetoothAdapter.getDefaultAdapter()
            ?: return@withContext "Error: no Bluetooth adapter on this device"
        if (!adapter.isEnabled) return@withContext "Error: Bluetooth is disabled"

        val bonded: Set<BluetoothDevice> = try {
            adapter.bondedDevices ?: emptySet()
        } catch (se: SecurityException) {
            return@withContext "Error: missing BLUETOOTH_CONNECT permission (${se.message})"
        }
        if (bonded.isEmpty()) {
            return@withContext "Error: no paired devices — pair your OBD dongle in Android Bluetooth Settings first."
        }

        val target = when {
            targetAddress != null -> bonded.firstOrNull { it.address == targetAddress }
            else -> bonded.firstOrNull { d ->
                val n = (d.name ?: "").uppercase()
                n.contains("OBD") || n.contains("ELM327") || n.contains("VLINK") || n.contains("VEEPEAK")
            }
        } ?: return@withContext "Error: no paired ELM327/OBD device. Paired: " +
            bonded.joinToString { it.name ?: it.address }

        try {
            adapter.cancelDiscovery()
            connectSocket(target)
        } catch (e: Exception) {
            try {
                val m = target.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
                val sock = m.invoke(target, 1) as BluetoothSocket
                sock.connect()
                socket = sock
                input = sock.inputStream
                output = sock.outputStream
                deviceName = target.name ?: target.address
            } catch (e2: Exception) {
                disconnect()
                return@withContext "Error: connect failed to ${target.name}: ${e.message} / ${e2.message}"
            }
        }

        val eng = ObdElmEngine(elmIo)
        eng.initialize().fold(
            onSuccess = { msg ->
                engine = eng
                "Connected to ${deviceName} ($msg)"
            },
            onFailure = {
                disconnect()
                "Error: ELM327 init failed: ${it.message}"
            },
        )
    }

    fun engineOrNull(): ObdElmEngine? = engine

    suspend fun readPid(pidHex: String): String {
        val eng = engine ?: return "Error: not connected"
        return eng.readPid(pidHex)
    }

    suspend fun clearCodes(): String {
        val eng = engine ?: return "Error: not connected"
        return eng.clearCodes()
    }

    suspend fun readDtcs(): String {
        val eng = engine ?: return "Error: not connected"
        return eng.readDtcsText()
    }

    fun isConnected(): Boolean = socket != null && engine != null
    fun connectedDeviceName(): String? = deviceName

    fun listBondedObdDevices(): List<Pair<String, String>> {
        val adapter = BluetoothAdapter.getDefaultAdapter() ?: return emptyList()
        return try {
            (adapter.bondedDevices ?: emptySet()).map { (it.name ?: "Unknown") to it.address }
        } catch (_: SecurityException) {
            emptyList()
        }
    }

    fun disconnect() {
        engine = null
        try { input?.close() } catch (_: Exception) {}
        try { output?.close() } catch (_: Exception) {}
        try { socket?.close() } catch (_: Exception) {}
        input = null
        output = null
        socket = null
        deviceName = null
    }

    private fun connectSocket(target: BluetoothDevice) {
        val sock = target.createRfcommSocketToServiceRecord(SPP_UUID)
        sock.connect()
        socket = sock
        input = sock.inputStream
        output = sock.outputStream
        deviceName = target.name ?: target.address
    }

    private suspend fun sendRawLocked(cmd: String): String = ioMutex.withLock {
        val out = output ?: throw IllegalStateException("not connected")
        val inp = input ?: throw IllegalStateException("not connected")
        out.write((cmd + "\r").toByteArray(Charsets.US_ASCII))
        out.flush()
        val sb = StringBuilder()
        val deadline = System.currentTimeMillis() + 4000L
        val buf = ByteArray(64)
        while (System.currentTimeMillis() < deadline) {
            if (inp.available() > 0) {
                val n = inp.read(buf)
                if (n > 0) {
                    sb.append(String(buf, 0, n, Charsets.US_ASCII))
                    if (sb.indexOf('>') >= 0) break
                }
            } else {
                try { Thread.sleep(15) } catch (_: InterruptedException) {}
            }
        }
        sb.toString().replace(">", "").trim()
    }
}
