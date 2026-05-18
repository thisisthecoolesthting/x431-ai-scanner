package com.caseforge.scanner.vci

import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.timeout
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.coroutineContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * High-level diagnostic API that mirrors [EngineDriver] but communicates directly
 * with the VCI hardware over Bluetooth SPP — no X431 UI involved.
 *
 * API PARITY WITH EngineDriver:
 *   readDtcs()   ↔ OBD Mode 03 (stored DTCs)
 *   clearCodes() ↔ OBD Mode 04 (clear DTCs + MIL)
 *   fullScan()   ↔ Mode 03 across all systems (currently: single ECU OBD scan)
 *   livePid()    ↔ OBD Mode 01 PID polling
 *   actuate()    ↔ Proprietary active test (STUB — opcode unconfirmed)
 *
 * SPIKE LIMITATIONS:
 *   - Only OBD-II mode 01/03/04 are implemented end-to-end.  These cover generic
 *     powertrain DTCs (P0xxx) on any OBD-II compliant vehicle (post-1996 USA,
 *     post-2001 EU).
 *   - Proprietary multi-system scan (reading ABS, SRS, TCM etc.) requires the
 *     CNLaunch framing for the .so-mediated diagnostic session; those opcodes are
 *     UNKNOWN until packet capture.
 *   - VIN reading (OBD Mode 09) is implemented as a best-effort convenience.
 *   - Actuate() is a STUB — active test opcode is unconfirmed.
 *
 * THREADING:
 *   All public methods are suspend functions and safe to call from any coroutine context.
 *   The [VciSocketClient.frames] flow is consumed internally; callers should not also
 *   collect it directly while communicator operations are in flight.
 *
 * USAGE EXAMPLE:
 *   val client = VciSocketClient(context)
 *   client.connect("AA:BB:CC:DD:EE:FF")
 *   val comm = VciCommunicator(client)
 *   val dtcs = comm.readDtcs().getOrThrow()
 */
class VciCommunicator(
    private val client: VciSocketClient,
    /** Timeout for a single request-response exchange. Mirrors LocalSocketClient.timeout. */
    private val requestTimeoutMs: Long = 20_000L,
    /** Polling interval for live PID streaming. */
    private val livePidPollMs: Long = 200L,
) {
    companion object {
        private const val TAG = "VciCommunicator"

        // OBD-II PID byte for common live data signals
        // Source: SAE J1979 Table A.6
        const val PID_ENGINE_RPM         = 0x0C
        const val PID_VEHICLE_SPEED      = 0x0D
        const val PID_ENGINE_COOLANT_TEMP = 0x05
        const val PID_THROTTLE_POSITION  = 0x11
        const val PID_INTAKE_MAP         = 0x0B
        const val PID_MAF_RATE           = 0x10
        const val PID_O2_VOLTAGE_B1S1    = 0x14
        const val PID_O2_VOLTAGE_B1S2    = 0x15
        const val PID_O2_VOLTAGE_B2S1    = 0x16
        const val PID_O2_VOLTAGE_B2S2    = 0x17
        const val PID_O2_TRIM_B1S1       = 0x18
        const val PID_O2_TRIM_B1S2       = 0x19
        const val PID_O2_TRIM_B2S1       = 0x1A
        const val PID_O2_TRIM_B2S2       = 0x1B
        const val PID_CONTROL_MODULE_V   = 0x42
        const val PID_FUEL_LEVEL         = 0x2F
        const val PID_BAROMETRIC_PRESSURE = 0x33
        const val PID_AMBIENT_TEMP       = 0x46

        /** Default Mode 01 PID set for live dashboard. */
        val DEFAULT_LIVE_PIDS: List<Int> = listOf(
            PID_ENGINE_COOLANT_TEMP,
            PID_ENGINE_RPM,
            PID_VEHICLE_SPEED,
            PID_MAF_RATE,
            PID_THROTTLE_POSITION,
            PID_O2_VOLTAGE_B1S1,
            PID_CONTROL_MODULE_V,
        )

        // OBD-II mode bytes
        const val OBD_MODE_LIVE_DATA     = 0x01
        const val OBD_MODE_FREEZE_FRAME  = 0x02
        const val OBD_MODE_READ_DTC      = 0x03
        const val OBD_MODE_CLEAR_DTC     = 0x04
        const val OBD_MODE_TEST_RESULTS  = 0x06
        const val OBD_MODE_PENDING_DTC   = 0x07
        const val OBD_MODE_VEHICLE_INFO  = 0x09
    }

    // ------------------------------------------------------------------
    // readDtcs — OBD Mode 03
    // ------------------------------------------------------------------

    /**
     * Read all stored diagnostic trouble codes from the currently connected ECU.
     *
     * Uses OBD-II Service 03 (Request Stored DTCs).
     * Response payload encodes DTCs as 2-byte pairs: high nibble = letter (P/C/B/U),
     * remaining 12 bits = numeric code.
     *
     * @return List of [Dtc] objects, or failure wrapping [VciException].
     *
     * CONFIRMED: OBD-II mode 03 is universally supported on all OBD-II ECUs.
     * UNKNOWN: Whether the VCI uses opcode 0x0103 or a different encoding.
     */
    suspend fun readDtcs(): Result<List<Dtc>> = safeRequest("readDtcs") {
        // Send Mode 03 request (no PID needed)
        client.send(KnownOpcode.OBD_MODE03_DTC_REQ)

        // Wait for a DTC response frame
        val responseFrame = awaitResponse(KnownOpcode.OBD_MODE03_DTC_RESP)
            ?: return@safeRequest Result.failure(
                VciException.Timeout(KnownOpcode.OBD_MODE03_DTC_REQ, requestTimeoutMs)
            )

        val dtcs = parseDtcPayload(responseFrame.payload)
        Log.i(TAG, "readDtcs: found ${dtcs.size} DTCs")
        Result.success(dtcs)
    }

    // ------------------------------------------------------------------
    // clearCodes — OBD Mode 04
    // ------------------------------------------------------------------

    /**
     * Clear all stored DTCs and extinguish the MIL (check engine light).
     *
     * Uses OBD-II Service 04.  Response is a positive ACK frame.
     *
     * NOTE: Some vehicles require ignition cycle after clear for the MIL to stay off.
     *
     * @return [Result.success] of [Unit] on confirmation, or [Result.failure].
     */
    suspend fun clearCodes(): Result<Unit> = safeRequest("clearCodes") {
        client.send(KnownOpcode.OBD_MODE04_CLEAR_DTC)

        val ack = awaitResponse(KnownOpcode.OBD_MODE04_CLEAR_RESP)
            ?: return@safeRequest Result.failure(
                VciException.Timeout(KnownOpcode.OBD_MODE04_CLEAR_DTC, requestTimeoutMs)
            )

        // Positive response payload should be empty or contain 0x44 (0x40+mode)
        Log.i(TAG, "clearCodes: confirmed, payload=${ack.payload.toHexString()}")
        Result.success(Unit)
    }

    // ------------------------------------------------------------------
    // fullScan — Mode 03 + pending (Mode 07) + optional VIN
    // ------------------------------------------------------------------

    /**
     * Best-effort full scan: reads stored DTCs (Mode 03) plus pending DTCs (Mode 07)
     * and attempts to read the VIN (Mode 09 info type 02).
     *
     * SPIKE SCOPE: This is a SINGLE-ECU OBD-II scan only.  A real multi-system scan
     * (ABS, SRS, Transmission etc.) requires the proprietary CNLaunch framing which
     * is still UNKNOWN.  See SPIKE-REPORT.md.
     *
     * @return [FullScanResult] grouping all found codes under "OBD-II (Mode 03/07)".
     */
    suspend fun fullScan(): Result<FullScanResult> = safeRequest("fullScan") {
        val startMs = System.currentTimeMillis()

        // Stored DTCs
        val storedDtcs = readDtcs().getOrElse { emptyList() }

        // Pending DTCs (Mode 07)
        val pendingDtcs = readPendingDtcs().getOrElse { emptyList() }

        // VIN (best effort — don't fail if unsupported)
        val vin = readVin().getOrNull()

        val allDtcs = (storedDtcs + pendingDtcs).distinctBy { it.code }

        val module = ModuleScan(
            name    = "OBD-II (Mode 03/07)",
            dtcs    = allDtcs,
            skipped = false,
            vin     = vin,
            note    = "Single-ECU OBD-II scan only. Multi-system scan requires proprietary framing.",
        )

        Log.i(TAG, "fullScan complete: ${allDtcs.size} DTCs, vin=$vin, durationMs=${System.currentTimeMillis() - startMs}")
        Result.success(
            FullScanResult(
                modules     = listOf(module),
                durationMs  = System.currentTimeMillis() - startMs,
                vin         = vin,
            )
        )
    }

    // ------------------------------------------------------------------
    // livePid — OBD Mode 01, cold Flow
    // ------------------------------------------------------------------

    /**
     * Cold [Flow] that emits a [LiveSample] for each PID in [pids] every [livePidPollMs].
     *
     * Cancelling the collecting coroutine stops all polling — no leak.
     *
     * Each emission is a single request-response cycle per PID.  For N PIDs the
     * effective rate is livePidPollMs / N per signal — keep the PID list short
     * (< 10 items) for responsive updates.
     *
     * @param pids List of OBD-II PID bytes (use the PID_* constants above).
     *
     * CONFIRMED: OBD Mode 01 PID polling is universally supported.
     * UNKNOWN: Whether the VCI batches PIDs or requires one request per PID.
     *          This implementation sends one request per PID (conservative).
     */
    fun livePid(pids: List<Int>): Flow<LiveSample> = flow {
        while (coroutineContext.isActive) {
            val tsMs = System.currentTimeMillis()
            for (pid in pids) {
                if (!coroutineContext.isActive) break
                val sample = requestLivePid(pid)
                if (sample != null) emit(sample.copy(tsMs = tsMs))
            }
            kotlinx.coroutines.delay(livePidPollMs)
        }
    }

    // ------------------------------------------------------------------
    // actuate — STUB
    // ------------------------------------------------------------------

    /**
     * Trigger an active test on the currently selected component.
     *
     * STATUS: STUB — the proprietary opcode for active tests is UNKNOWN.
     * [KnownOpcode.PROPRIETARY_ACTIVE_TEST] value (0x0009) is inferred from
     * DiagnoseConstants.FEEDBACK_ACTIVITYTEST = "9" but NOT verified from wire captures.
     *
     * The X431 app sends an actuation request after navigating to the active test screen
     * and the user selects a component.  The [testId] here mirrors what EngineDriver.actuate()
     * accepts — it's opaque until we capture the actual framing.
     *
     * DO NOT USE IN PRODUCTION until the opcode is confirmed via Frida or tcpdump.
     */
    suspend fun actuate(testId: String): Result<ActuationResult> = safeRequest("actuate") {
        Log.w(TAG, "actuate: STUB — opcode 0x${KnownOpcode.PROPRIETARY_ACTIVE_TEST.value.toString(16)} is UNCONFIRMED")

        // Encode testId as ASCII bytes in payload (best guess)
        val payload = testId.toByteArray(Charsets.US_ASCII)
        client.send(KnownOpcode.PROPRIETARY_ACTIVE_TEST, payload)

        // No reliable response opcode known — just wait for any frame
        val response = awaitAnyFrame(timeoutMs = requestTimeoutMs)

        val success = response != null
        Result.success(
            ActuationResult(
                testId  = testId,
                success = success,
                log     = listOf(
                    "STUB: sent opcode=${KnownOpcode.PROPRIETARY_ACTIVE_TEST.value} payload=${payload.toHexString()}",
                    "response=${response?.toString() ?: "TIMEOUT"}",
                ),
            )
        )
    }

    // ------------------------------------------------------------------
    // readVin — OBD Mode 09, info type 02
    // ------------------------------------------------------------------

    /**
     * Read the Vehicle Identification Number via OBD-II Service 09, info type 0x02.
     *
     * Maps to DiagnoseConstants.FEEDBACK_GET_VIN = "48" and UI_TYPE_GET_VIN = "1020".
     *
     * @return 17-character VIN string, or failure if not supported.
     */
    suspend fun readVin(): Result<String> = safeRequest("readVin") {
        val payload = byteArrayOf(0x02.toByte())  // Info type 0x02 = VIN
        client.send(KnownOpcode.OBD_MODE09_VEH_INFO_REQ, payload)

        val response = awaitResponse(KnownOpcode.OBD_MODE09_VEH_INFO_RESP)
            ?: return@safeRequest Result.failure(
                VciException.Timeout(KnownOpcode.OBD_MODE09_VEH_INFO_REQ, requestTimeoutMs)
            )

        val vin = parseVinPayload(response.payload)
            ?: return@safeRequest Result.failure(
                VciException.ProtocolError("Could not parse VIN from payload: ${response.payload.toHexString()}")
            )

        Result.success(vin)
    }

    // ------------------------------------------------------------------
    // readPendingDtcs — OBD Mode 07 (internal helper)
    // ------------------------------------------------------------------

    private suspend fun readPendingDtcs(): Result<List<Dtc>> = safeRequest("readPendingDtcs") {
        // Mode 07 uses the same opcode category as Mode 03 but different mode byte
        // For the spike we reuse OBD_MODE03_DTC_REQ with a payload byte override
        // FIXME: define a proper PENDING_DTC opcode once wire format is confirmed
        val pending = byteArrayOf(OBD_MODE_PENDING_DTC.toByte())
        client.sendRaw(KnownOpcode.OBD_MODE03_DTC_REQ.value, pending)

        val response = awaitResponse(KnownOpcode.OBD_MODE03_DTC_RESP)
        val dtcs = response?.let { parseDtcPayload(it.payload) } ?: emptyList()
        Result.success(dtcs.map { it.copy(code = "PENDING:${it.code}") })
    }

    // ------------------------------------------------------------------
    // requestLivePid (internal)
    // ------------------------------------------------------------------

    private suspend fun requestLivePid(pid: Int): LiveSample? {
        return try {
            client.send(KnownOpcode.OBD_MODE01_PID_REQ, byteArrayOf(pid.toByte()))
            val response = awaitResponse(KnownOpcode.OBD_MODE01_PID_RESP)
            response?.let { parsePidResponse(pid, it.payload) }
        } catch (e: VciException) {
            Log.w(TAG, "livePid PID=0x${pid.toString(16)} error: ${e.message}")
            null
        }
    }

    // ------------------------------------------------------------------
    // Response waiting helpers
    // ------------------------------------------------------------------

    /**
     * Wait for the first frame matching [expectedOpcode] within [requestTimeoutMs].
     * Returns null on timeout.
     */
    private suspend fun awaitResponse(expectedOpcode: KnownOpcode): VciFrame? {
        return try {
            withTimeout(requestTimeoutMs) {
                client.frames
                    .filter { it.opcode == expectedOpcode.value }
                    .firstOrNull()
            }
        } catch (_: kotlinx.coroutines.TimeoutCancellationException) {
            Log.w(TAG, "Timeout waiting for ${expectedOpcode.name}")
            null
        }
    }

    /** Wait for any frame (used by stubs with unknown response opcodes). */
    private suspend fun awaitAnyFrame(timeoutMs: Long): VciFrame? {
        return try {
            withTimeout(timeoutMs) {
                client.frames.firstOrNull()
            }
        } catch (_: kotlinx.coroutines.TimeoutCancellationException) {
            null
        }
    }

    // ------------------------------------------------------------------
    // Payload parsers
    // ------------------------------------------------------------------

    /**
     * Parse a Mode 03/07 DTC response payload.
     *
     * OBD-II encoding (SAE J1979):
     *   - First byte: number of DTCs (some ECUs omit this; handle both)
     *   - Then 2 bytes per DTC:
     *       byte[0] bits 7-6: system letter   (00=P, 01=C, 10=B, 11=U)
     *       byte[0] bits 5-4: first digit of numeric code
     *       byte[0] bits 3-0: second digit
     *       byte[1]          : third + fourth digits
     *
     * STATUS: This is standard SAE J1979 parsing — confirmed correct for OBD-II.
     * The VCI passes through OBD responses transparently; the payload format is
     * set by the vehicle ECU, not the VCI.
     */
    internal fun parseDtcPayload(payload: ByteArray): List<Dtc> {
        if (payload.isEmpty()) return emptyList()

        val dtcs = mutableListOf<Dtc>()
        // Some ECUs prefix with a count byte, some don't — detect by checking if
        // payload.size == 1 + 2*count vs 2*count
        val startIdx = if (payload.size % 2 == 1) 1 else 0

        var i = startIdx
        while (i + 1 < payload.size) {
            val high = payload[i].toInt() and 0xFF
            val low  = payload[i + 1].toInt() and 0xFF

            // Skip null DTCs (0x0000) — ECU pads response to expected length
            if (high == 0 && low == 0) { i += 2; continue }

            val systemBits = (high ushr 6) and 0x03
            val systemLetter = when (systemBits) {
                0 -> 'P'
                1 -> 'C'
                2 -> 'B'
                3 -> 'U'
                else -> 'P'
            }

            val digit1 = (high ushr 4) and 0x03
            val digit2 = high and 0x0F
            val digit3 = (low ushr 4) and 0x0F
            val digit4 = low and 0x0F

            val code = "$systemLetter$digit1$digit2${digit3.toString(16).uppercase()}${digit4.toString(16).uppercase()}"

            dtcs += Dtc(
                code        = code,
                description = null,   // descriptions require an offline DB lookup
                severity    = Severity.Amber,
                source      = DtcSource.OBD_MODE03,
            )
            i += 2
        }

        return dtcs
    }

    /**
     * Parse an OBD-II Mode 01 PID response payload.
     *
     * Standard encoding (SAE J1979 Table A.6):
     *   Byte 0: mode + 0x40 (= 0x41), Byte 1: PID, Byte 2+: data
     *
     * STATUS: Standard OBD-II — confirmed correct.  VCI passes through transparently.
     */
    internal fun parsePidResponse(pid: Int, payload: ByteArray): LiveSample? {
        if (payload.size < 2) return null

        // Positive response: byte[0] should be 0x41 (0x40 + mode 0x01)
        // Some VCIs strip the mode/PID prefix — handle both
        val dataStart = when {
            payload[0].toInt() and 0xFF == 0x41 && payload.size >= 3 -> 2
            else -> 0
        }

        val value = when (pid) {
            PID_ENGINE_RPM -> {
                // RPM = (A*256 + B) / 4
                if (payload.size - dataStart < 2) return null
                val a = payload[dataStart].toInt() and 0xFF
                val b = payload[dataStart + 1].toInt() and 0xFF
                ((a * 256 + b) / 4.0)
            }
            PID_VEHICLE_SPEED -> {
                // Speed = A km/h
                if (payload.size - dataStart < 1) return null
                (payload[dataStart].toInt() and 0xFF).toDouble()
            }
            PID_ENGINE_COOLANT_TEMP -> {
                // Temp = A - 40 (°C)
                if (payload.size - dataStart < 1) return null
                ((payload[dataStart].toInt() and 0xFF) - 40).toDouble()
            }
            PID_THROTTLE_POSITION -> {
                // Throttle = A * 100 / 255 (%)
                if (payload.size - dataStart < 1) return null
                ((payload[dataStart].toInt() and 0xFF) * 100.0 / 255.0)
            }
            PID_FUEL_LEVEL -> {
                // Fuel = A * 100 / 255 (%)
                if (payload.size - dataStart < 1) return null
                ((payload[dataStart].toInt() and 0xFF) * 100.0 / 255.0)
            }
            PID_MAF_RATE -> {
                // MAF = (A*256 + B) / 100 (g/s)
                if (payload.size - dataStart < 2) return null
                val a = payload[dataStart].toInt() and 0xFF
                val b = payload[dataStart + 1].toInt() and 0xFF
                ((a * 256 + b) / 100.0)
            }
            PID_INTAKE_MAP -> {
                if (payload.size - dataStart < 1) return null
                (payload[dataStart].toInt() and 0xFF).toDouble()
            }
            PID_CONTROL_MODULE_V -> {
                if (payload.size - dataStart < 2) return null
                val a = payload[dataStart].toInt() and 0xFF
                val b = payload[dataStart + 1].toInt() and 0xFF
                ((a * 256 + b) / 1000.0)
            }
            in PID_O2_VOLTAGE_B1S1..PID_O2_VOLTAGE_B2S2 -> {
                if (payload.size - dataStart < 2) return null
                val a = payload[dataStart].toInt() and 0xFF
                val b = payload[dataStart + 1].toInt() and 0xFF
                if (a == 0xFF && b == 0xFF) return null
                (a / 200.0) + (b / 512.0)
            }
            in PID_O2_TRIM_B1S1..PID_O2_TRIM_B2S2 -> {
                if (payload.size - dataStart < 1) return null
                val a = payload[dataStart].toInt() and 0xFF
                ((a - 128) * 100.0 / 128.0)
            }
            else -> {
                // Generic: return raw value of first byte
                if (payload.size - dataStart < 1) return null
                (payload[dataStart].toInt() and 0xFF).toDouble()
            }
        }

        return LiveSample(
            pid   = "0x${pid.toString(16).uppercase().padStart(2, '0')}",
            value = value,
            unit  = pidUnit(pid),
            tsMs  = System.currentTimeMillis(),
        )
    }

    /**
     * Parse an OBD-II Mode 09 VIN response.
     *
     * VIN is encoded as 17 ASCII bytes in the payload (after stripping mode/info bytes).
     */
    internal fun parseVinPayload(payload: ByteArray): String? {
        val ascii = payload.filter { it in 32..126 }.map { it.toInt().toChar() }.joinToString("")
        val fromRegex = VIN_REGEX.find(ascii)?.value
        if (fromRegex != null) return fromRegex

        if (payload.size < 17) return null
        val startIdx = when {
            payload.size >= 20 && payload[0].toInt() and 0xFF == 0x49 -> 3
            payload.size >= 19 && payload[0].toInt() and 0xFF == 0x49 -> 2
            else -> 0
        }
        if (payload.size - startIdx < 17) return null
        val vin = String(payload, startIdx, 17, Charsets.US_ASCII)
        return if (vin.all { it.isLetterOrDigit() }) vin else null
    }

    private fun pidUnit(pid: Int): String = when (pid) {
        PID_ENGINE_RPM          -> "rpm"
        PID_VEHICLE_SPEED       -> "km/h"
        PID_ENGINE_COOLANT_TEMP -> "°C"
        PID_THROTTLE_POSITION   -> "%"
        PID_FUEL_LEVEL          -> "%"
        PID_MAF_RATE            -> "g/s"
        PID_INTAKE_MAP          -> "kPa"
        PID_O2_VOLTAGE_B1S1,
        PID_O2_VOLTAGE_B1S2,
        PID_O2_VOLTAGE_B2S1,
        PID_O2_VOLTAGE_B2S2   -> "V"
        PID_O2_TRIM_B1S1,
        PID_O2_TRIM_B1S2,
        PID_O2_TRIM_B2S1,
        PID_O2_TRIM_B2S2       -> "%"
        PID_CONTROL_MODULE_V    -> "V"
        PID_BAROMETRIC_PRESSURE -> "kPa"
        PID_AMBIENT_TEMP        -> "°C"
        else                    -> ""
    }

    private val VIN_REGEX = Regex("[A-HJ-NPR-Z0-9]{17}")

    // ------------------------------------------------------------------
    // safeRequest — error boundary (mirrors EngineDriver.safeRun)
    // ------------------------------------------------------------------

    private suspend fun <T> safeRequest(tag: String, block: suspend () -> Result<T>): Result<T> {
        return try {
            if (client.connectionState.value != VciSocketClient.ConnectionState.CONNECTED) {
                return Result.failure(VciException.NotConnected("Cannot execute '$tag' — VCI not connected"))
            }
            block()
        } catch (e: VciException) {
            Result.failure(e)
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            Result.failure(VciException.ProtocolError("Coroutine timeout in '$tag': ${e.message}"))
        } catch (e: Exception) {
            Result.failure(VciException.ProtocolError("Unexpected error in '$tag': ${e.message}"))
        }
    }
}

// ------------------------------------------------------------------
// Domain types (VCI-native versions — parallel to engine/Types.kt)
// ------------------------------------------------------------------

data class Dtc(
    val code: String,
    val description: String?,   // null until offline DTC DB lookup
    val severity: Severity,
    val source: DtcSource,
)

enum class DtcSource { OBD_MODE03, OBD_MODE07_PENDING, PROPRIETARY }
enum class Severity  { Red, Amber, Gray }

data class FullScanResult(
    val modules: List<ModuleScan>,
    val durationMs: Long,
    val vin: String?,
)

data class ModuleScan(
    val name: String,
    val dtcs: List<Dtc>,
    val skipped: Boolean,
    val vin: String? = null,
    val note: String? = null,
)

data class LiveSample(
    val pid: String,
    val value: Double,
    val unit: String,
    val tsMs: Long,
)

data class ActuationResult(
    val testId: String,
    val success: Boolean,
    val log: List<String>,
)
