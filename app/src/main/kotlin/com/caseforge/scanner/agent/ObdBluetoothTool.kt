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
 * Bluetooth OBD-II live-data tool for cheap ELM327-compatible dongles.
 *
 * Connects via RFCOMM/SPP and speaks the standard ELM327 AT + Mode 01/03/07
 * command set. Output is plain text so the agent LLM can read it directly.
 *
 * Assumes BLUETOOTH_CONNECT / BLUETOOTH_SCAN runtime perms are already granted
 * by the caller; we suppress the lint here.
 */
@SuppressLint("MissingPermission")
object ObdBluetoothTool {

    private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private val ioMutex = Mutex()

    @Volatile private var socket: BluetoothSocket? = null
    @Volatile private var input: InputStream? = null
    @Volatile private var output: OutputStream? = null
    @Volatile private var deviceName: String? = null

    // ---------- public API ----------

    suspend fun scanAndConnect(): String = withContext(Dispatchers.IO) {
        disconnect()
        val adapter = BluetoothAdapter.getDefaultAdapter()
            ?: return@withContext "Error: no Bluetooth adapter on this device"
        if (!adapter.isEnabled) return@withContext "Error: Bluetooth is disabled"

        val bonded: Set<BluetoothDevice> = try {
            adapter.bondedDevices ?: emptySet()
        } catch (se: SecurityException) {
            return@withContext "Error: missing BLUETOOTH_CONNECT permission (${se.message})"
        }
        if (bonded.isEmpty()) return@withContext "Error: no paired Bluetooth devices. Pair the OBD dongle in Android Settings first."

        val target = bonded.firstOrNull { d ->
            val n = (d.name ?: "").uppercase()
            n.contains("OBD") || n.contains("ELM327") || n.contains("VLINK") || n.contains("VEEPEAK")
        } ?: return@withContext "Error: no paired device with name containing OBD/ELM327. Paired devices: " +
                bonded.joinToString { it.name ?: it.address }

        try {
            adapter.cancelDiscovery()
            val sock = target.createRfcommSocketToServiceRecord(SPP_UUID)
            sock.connect()
            socket = sock
            input = sock.inputStream
            output = sock.outputStream
            deviceName = target.name ?: target.address
        } catch (e: Exception) {
            // Fallback: some clones need the reflective channel-1 socket
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
                return@withContext "Error: connect failed to ${target.name}: ${e.message} / fallback: ${e2.message}"
            }
        }

        // Initialize ELM327
        try {
            sendRaw("ATZ")        // reset
            sendRaw("ATE0")       // echo off
            sendRaw("ATL0")       // linefeeds off
            sendRaw("ATS0")       // spaces off
            sendRaw("ATH0")       // headers off
            sendRaw("ATSP0")      // auto protocol
            val voltage = sendRaw("ATRV").trim()
            "Connected to ${deviceName} (battery ${voltage})"
        } catch (e: Exception) {
            disconnect()
            "Error: ELM327 init failed: ${e.message}"
        }
    }

    suspend fun readPid(pidHex: String): String = withContext(Dispatchers.IO) {
        if (socket == null) return@withContext "Error: not connected. Call connect first."
        val pid = pidHex.trim().uppercase().removePrefix("0X").padStart(2, '0')
        val raw = try {
            sendRaw("01$pid")
        } catch (e: Exception) {
            return@withContext "Error: PID $pid read failed: ${e.message}"
        }
        val bytes = parseObdResponse(raw, expectMode = 0x41, expectPid = pid)
            ?: return@withContext "PID $pid: no data (raw=${raw.trim()})"
        formatPid(pid, bytes)
    }

    /** Clear stored DTCs (SAE Mode 04). Engine should be off and KOEO. */
    suspend fun clearCodes(): String = withContext(Dispatchers.IO) {
        if (socket == null) return@withContext "Error: not connected. Call connect first."
        val raw = try { sendRaw("04") } catch (e: Exception) { return@withContext "Error: Mode 04 failed: ${e.message}" }
        if (raw.contains("44", ignoreCase = true)) "OK: stored codes cleared (Mode 04 acknowledged)"
        else "Unclear response: $raw"
    }

    /** Whether we currently have an open RFCOMM socket to a dongle. */
    fun isConnected(): Boolean = socket != null
    fun connectedDeviceName(): String? = deviceName

    suspend fun readDtcs(): String = withContext(Dispatchers.IO) {
        if (socket == null) return@withContext "Error: not connected. Call connect first."
        val stored = try { sendRaw("03") } catch (e: Exception) { return@withContext "Error: Mode 03 failed: ${e.message}" }
        val pending = try { sendRaw("07") } catch (e: Exception) { "" }
        val storedCodes = parseDtcs(stored, 0x43)
        val pendingCodes = parseDtcs(pending, 0x47)
        buildString {
            append("Stored DTCs (Mode 03): ")
            append(if (storedCodes.isEmpty()) "none" else storedCodes.joinToString(", "))
            append("\nPending DTCs (Mode 07): ")
            append(if (pendingCodes.isEmpty()) "none" else pendingCodes.joinToString(", "))
        }
    }

    fun disconnect() {
        try { input?.close() } catch (_: Exception) {}
        try { output?.close() } catch (_: Exception) {}
        try { socket?.close() } catch (_: Exception) {}
        input = null; output = null; socket = null; deviceName = null
    }

    // ---------- I/O ----------

    private suspend fun sendRaw(cmd: String): String = ioMutex.withLock {
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

    // ---------- parsing ----------

    /**
     * Returns the data-byte list (excluding mode + pid echo) for a Mode 01 reply,
     * or null if the response is NO DATA / unparseable.
     */
    private fun parseObdResponse(raw: String, expectMode: Int, expectPid: String): IntArray? {
        val cleaned = raw
            .replace("SEARCHING...", "")
            .replace("NO DATA", "")
            .replace("STOPPED", "")
            .replace("?", "")
            .replace("\r", " ")
            .replace("\n", " ")
            .trim()
        if (cleaned.isEmpty()) return null

        // CAN multi-frame replies start with "0:" / "1:" line indices; strip them.
        val tokens = cleaned.split(Regex("\\s+"))
            .filter { it.length == 2 && it.all { c -> c.isDigit() || c in 'A'..'F' || c in 'a'..'f' } }
            .map { it.toInt(16) }
        if (tokens.size < 3) return null

        // find mode byte
        val modeIdx = tokens.indexOf(expectMode)
        if (modeIdx < 0 || modeIdx + 1 >= tokens.size) return null
        val pidVal = expectPid.toInt(16)
        if (tokens[modeIdx + 1] != pidVal) return null
        val data = tokens.subList(modeIdx + 2, tokens.size).toIntArray()
        return if (data.isEmpty()) null else data
    }

    private fun formatPid(pid: String, d: IntArray): String = when (pid) {
        "04" -> "Engine load: ${"%.1f".format(d[0] * 100.0 / 255.0)}%"
        "05" -> "Coolant: ${d[0] - 40}°C"
        "0B" -> "MAP: ${d[0]} kPa"
        "0C" -> if (d.size >= 2) "RPM: ${((d[0] * 256 + d[1]) / 4)}" else "RPM: parse error"
        "0D" -> "Vehicle speed: ${d[0]} km/h"
        "0E" -> "Timing advance: ${"%.1f".format(d[0] / 2.0 - 64.0)}°"
        "0F" -> "Intake air: ${d[0] - 40}°C"
        "10" -> if (d.size >= 2) "MAF: ${"%.2f".format((d[0] * 256 + d[1]) / 100.0)} g/s" else "MAF: parse error"
        "11" -> "Throttle: ${"%.1f".format(d[0] * 100.0 / 255.0)}%"
        "2F" -> "Fuel level: ${"%.1f".format(d[0] * 100.0 / 255.0)}%"
        "33" -> "Barometric: ${d[0]} kPa"
        "42" -> if (d.size >= 2) "Control module voltage: ${"%.3f".format((d[0] * 256 + d[1]) / 1000.0)} V" else "Voltage: parse error"
        "5C" -> "Engine oil: ${d[0] - 40}°C"
        else -> "PID $pid raw: ${d.joinToString(" ") { "%02X".format(it) }}"
    }

    /**
     * Parse Mode 03/07 reply into SAE J2012 codes (P0xxx, C, B, U).
     */
    private fun parseDtcs(raw: String, expectMode: Int): List<String> {
        val cleaned = raw
            .replace("NO DATA", "")
            .replace("SEARCHING...", "")
            .replace("\r", " ")
            .replace("\n", " ")
            .trim()
        if (cleaned.isEmpty()) return emptyList()
        val tokens = cleaned.split(Regex("\\s+"))
            .filter { it.length == 2 && it.all { c -> c.isDigit() || c in 'A'..'F' || c in 'a'..'f' } }
            .map { it.toInt(16) }
        val modeIdx = tokens.indexOf(expectMode)
        if (modeIdx < 0) return emptyList()
        // After mode byte, ISO 15031: count byte (number of codes) then code pairs.
        // Some clones omit the count; handle both by just walking pairs.
        var start = modeIdx + 1
        if (start < tokens.size && (tokens.size - start) % 2 == 1) start += 1
        val codes = mutableListOf<String>()
        var i = start
        while (i + 1 < tokens.size) {
            val a = tokens[i]; val b = tokens[i + 1]
            if (a == 0 && b == 0) { i += 2; continue }
            val prefix = when ((a ushr 6) and 0x03) {
                0 -> "P"; 1 -> "C"; 2 -> "B"; else -> "U"
            }
            val d1 = (a ushr 4) and 0x03
            val d2 = a and 0x0F
            val d3 = (b ushr 4) and 0x0F
            val d4 = b and 0x0F
            codes += "$prefix%d%X%X%X".format(d1, d2, d3, d4)
            i += 2
        }
        return codes
    }
}
