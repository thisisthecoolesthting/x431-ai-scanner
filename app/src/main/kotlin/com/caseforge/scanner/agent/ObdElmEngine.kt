package com.caseforge.scanner.agent

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Byte transport for ELM327 AT + OBD modes (USB or Bluetooth). */
interface ElmIo {
    suspend fun sendRaw(cmd: String): String
}

/**
 * Shared ELM327 command, init, and response parsing for USB and Bluetooth dongles.
 */
class ObdElmEngine(private val io: ElmIo) {

    private val ioMutex = Mutex()

    suspend fun initialize(): Result<String> = runCatching {
        ioMutex.withLock {
            io.sendRaw("ATZ")
            io.sendRaw("ATE0")
            io.sendRaw("ATL0")
            io.sendRaw("ATS0")
            io.sendRaw("ATH0")
            io.sendRaw("ATSP0")
            val voltage = io.sendRaw("ATRV").trim()
            val ready = io.sendRaw("0100")
            "ELM327 ready (battery $voltage, 0100=$ready)"
        }
    }

    suspend fun probeElm327(): Boolean = runCatching {
        val resp = ioMutex.withLock { io.sendRaw("ATZ") }
        resp.contains("ELM", ignoreCase = true) ||
            resp.contains("OBD", ignoreCase = true) ||
            resp.contains("OK", ignoreCase = true)
    }.getOrDefault(false)

    suspend fun readPid(pidHex: String): String {
        val pid = pidHex.trim().uppercase().removePrefix("0X").padStart(2, '0')
        val raw = ioMutex.withLock { io.sendRaw("01$pid") }
        val bytes = parseObdResponse(raw, expectMode = 0x41, expectPid = pid)
            ?: return "PID $pid: no data (raw=${raw.trim()})"
        return formatPid(pid, bytes)
    }

    suspend fun readDtcsText(): String {
        val stored = ioMutex.withLock { io.sendRaw("03") }
        val pending = runCatching { ioMutex.withLock { io.sendRaw("07") } }.getOrDefault("")
        val storedCodes = parseDtcs(stored, 0x43)
        val pendingCodes = parseDtcs(pending, 0x47)
        return buildString {
            append("Stored: ")
            append(if (storedCodes.isEmpty()) "none" else storedCodes.joinToString(", "))
            append("; Pending: ")
            append(if (pendingCodes.isEmpty()) "none" else pendingCodes.joinToString(", "))
        }
    }

    suspend fun readDtcCodes(): Pair<List<String>, List<String>> {
        val stored = ioMutex.withLock { io.sendRaw("03") }
        val pending = runCatching { ioMutex.withLock { io.sendRaw("07") } }.getOrDefault("")
        return parseDtcs(stored, 0x43) to parseDtcs(pending, 0x47)
    }

    suspend fun clearCodes(): String {
        val raw = ioMutex.withLock { io.sendRaw("04") }
        return if (raw.contains("44", ignoreCase = true)) {
            "OK: stored codes cleared"
        } else {
            "Unclear response: $raw"
        }
    }

    suspend fun readVin(): String? {
        val raw = ioMutex.withLock { io.sendRaw("0902") }
        return parseVin(raw)
    }

    suspend fun readPidBytes(pidHex: String): IntArray? {
        val pid = pidHex.trim().uppercase().removePrefix("0X").padStart(2, '0')
        val raw = ioMutex.withLock { io.sendRaw("01$pid") }
        return parseObdResponse(raw, expectMode = 0x41, expectPid = pid)
    }

    fun parseObdResponse(raw: String, expectMode: Int, expectPid: String): IntArray? {
        val cleaned = raw
            .replace("SEARCHING...", "")
            .replace("NO DATA", "")
            .replace("STOPPED", "")
            .replace("?", "")
            .replace("\r", " ")
            .replace("\n", " ")
            .trim()
        if (cleaned.isEmpty()) return null
        val tokens = cleaned.split(Regex("\\s+"))
            .filter { it.length == 2 && it.all { c -> c.isDigit() || c in 'A'..'F' || c in 'a'..'f' } }
            .map { it.toInt(16) }
        if (tokens.size < 3) return null
        val modeIdx = tokens.indexOf(expectMode)
        if (modeIdx < 0 || modeIdx + 1 >= tokens.size) return null
        val pidVal = expectPid.toInt(16)
        if (tokens[modeIdx + 1] != pidVal) return null
        val data = tokens.subList(modeIdx + 2, tokens.size).toIntArray()
        return if (data.isEmpty()) null else data
    }

    fun formatPid(pid: String, d: IntArray): String = when (pid) {
        "04" -> "%.1f".format(d[0] * 100.0 / 255.0)
        "05" -> "${d[0] - 40}"
        "0C" -> if (d.size >= 2) "${(d[0] * 256 + d[1]) / 4}" else "?"
        "0D" -> "${d[0]}"
        else -> d.joinToString(" ") { "%02X".format(it) }
    }

    fun parsePidValue(pid: String, d: IntArray): Double = when (pid.uppercase()) {
        "0C" -> if (d.size >= 2) ((d[0] * 256 + d[1]) / 4).toDouble() else 0.0
        "0D" -> d[0].toDouble()
        "05" -> (d[0] - 40).toDouble()
        else -> d.firstOrNull()?.toDouble() ?: 0.0
    }

    fun pidUnit(pid: String): String = when (pid.uppercase()) {
        "0C" -> "rpm"
        "0D" -> "km/h"
        "05" -> "°C"
        else -> ""
    }

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
        var start = modeIdx + 1
        if (start < tokens.size && (tokens.size - start) % 2 == 1) start += 1
        val codes = mutableListOf<String>()
        var i = start
        while (i + 1 < tokens.size) {
            val a = tokens[i]
            val b = tokens[i + 1]
            if (a == 0 && b == 0) {
                i += 2
                continue
            }
            val prefix = when ((a ushr 6) and 0x03) {
                0 -> "P"
                1 -> "C"
                2 -> "B"
                else -> "U"
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

    private fun parseVin(raw: String): String? {
        val tokens = raw
            .replace("SEARCHING...", "")
            .replace("\r", " ")
            .replace("\n", " ")
            .split(Regex("\\s+"))
            .filter { it.length == 2 && it.all { c -> c.isDigit() || c in 'A'..'F' || c in 'a'..'f' } }
            .map { it.toInt(16) }
        val idx = tokens.indexOf(0x49)
        if (idx < 0) return null
        val chars = tokens.drop(idx + 2).map { it.toChar() }.filter { it.code in 32..126 }
        val vin = chars.joinToString("").filter { it.isLetterOrDigit() }
        return vin.takeIf { it.length >= 11 }
    }
}
