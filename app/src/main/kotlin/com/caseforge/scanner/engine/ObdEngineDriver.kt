package com.caseforge.scanner.engine

import com.caseforge.scanner.agent.ObdElmEngine
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * [VciDiagnosticPort] backed by ELM327 (USB or Bluetooth). Standalone Together uses this
 * when a generic OBD cable is connected — same surface as Launch [VciDiagnosticAdapter].
 */
class ObdEngineDriver(
    private val elm: ObdElmEngine,
) : VciDiagnosticPort {

    override suspend fun runCapability(id: String): Result<JsonObject> = when (id) {
        "read_dtcs" -> readDtcs().map { buildJsonObject { put("count", it.size) } }
        "clear_dtcs" -> clearCodes().map { buildJsonObject { put("cleared", true) } }
        "full_scan" -> fullScan().map { buildJsonObject { put("modules", it.modules.size) } }
        "live_data" -> Result.success(buildJsonObject { put("screen", "LiveDataView") })
        "actuation" -> Result.failure(
            UnsupportedOperationException("Bidirectional actuation requires Launch VCI"),
        )
        else -> Result.failure(UnsupportedOperationException("Unknown capability: $id"))
    }

    override suspend fun fullScan(): Result<FullScanResult> = runCatching {
        val start = System.currentTimeMillis()
        val (stored, pending) = elm.readDtcCodes()
        val dtcs = buildList {
            stored.forEach { add(dtc(it, "stored")) }
            pending.forEach { add(dtc("PENDING:$it", "pending")) }
        }
        FullScanResult(
            modules = listOf(
                ModuleScan(
                    name = "OBD-II",
                    dtcs = dtcs,
                    skipped = false,
                    skipReason = null,
                ),
            ),
            durationMs = System.currentTimeMillis() - start,
        )
    }

    override suspend fun readDtcs(module: String?): Result<List<Dtc>> = runCatching {
        val (stored, pending) = elm.readDtcCodes()
        val all = buildList {
            stored.forEach { add(dtc(it, "stored")) }
            pending.forEach { add(dtc(it, "pending")) }
        }
        if (module == null) all else all.filter { it.module.equals(module, ignoreCase = true) }
    }

    override suspend fun clearCodes(): Result<Unit> = runCatching {
        elm.clearCodes()
        Unit
    }

    override fun liveData(pids: List<String>): Flow<LiveSample> = flow {
        val poll = if (pids.isEmpty()) {
            listOf("0C", "0D", "05")
        } else {
            pids.map { it.removePrefix("0x").removePrefix("0X").padStart(2, '0') }
        }
        while (currentCoroutineContext().isActive) {
            val ts = System.currentTimeMillis()
            for (pid in poll) {
                val bytes = elm.readPidBytes(pid) ?: continue
                emit(
                    LiveSample(
                        pid = pid,
                        value = elm.parsePidValue(pid, bytes),
                        unit = elm.pidUnit(pid),
                        tsMs = ts,
                    ),
                )
            }
            delay(500L)
        }
    }

    override suspend fun actuate(testId: String): Result<ActuationResult> =
        Result.failure(UnsupportedOperationException("Actuation not supported on ELM327"))

    private fun dtc(code: String, status: String) = Dtc(
        module = "OBD-II",
        code = code.removePrefix("PENDING:"),
        description = if (status == "pending") "Pending $code" else code,
        severity = Severity.Amber,
        freezeFrame = null,
    )
}
