package com.caseforge.scanner.vci

import com.caseforge.scanner.engine.ActuationResult
import com.caseforge.scanner.engine.Dtc
import com.caseforge.scanner.engine.FullScanResult
import com.caseforge.scanner.engine.LiveSample
import com.caseforge.scanner.engine.ModuleScan
import com.caseforge.scanner.engine.Severity
import com.caseforge.scanner.engine.VciDiagnosticPort
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Maps [VciCommunicator] results into [com.caseforge.scanner.engine] types for [EngineDriver]
 * and the standalone overlay.
 */
class VciDiagnosticAdapter(
  private val communicator: VciCommunicator,
) : VciDiagnosticPort {

  override suspend fun runCapability(id: String): Result<JsonObject> = when (id) {
    "read_dtcs" -> readDtcs().map { buildJsonObject { put("count", it.size) } }
    "clear_dtcs" -> clearCodes().map { buildJsonObject { put("cleared", true) } }
    "full_scan" -> fullScan().map { buildJsonObject { put("modules", it.modules.size) } }
    "live_data" -> Result.success(buildJsonObject { put("screen", "LiveDataView") })
    "actuation" -> Result.failure(
      UnsupportedOperationException("OEM actuation requires OEM diagnostic overlay mode"),
    )
    else -> Result.failure(UnsupportedOperationException("Unknown capability: $id"))
  }

  override suspend fun fullScan(): Result<FullScanResult> =
    communicator.fullScan().map { vciResult ->
      FullScanResult(
        modules = vciResult.modules.map { m ->
          ModuleScan(
            name = m.name,
            dtcs = m.dtcs.map { toEngineDtc(it, m.name) },
            skipped = m.skipped,
            skipReason = m.note,
          )
        },
        durationMs = vciResult.durationMs,
      )
    }

  override suspend fun readDtcs(module: String?): Result<List<Dtc>> =
    communicator.readDtcs().map { list ->
      list
        .filter { module == null || it.code.startsWith(module, ignoreCase = true) }
        .map { toEngineDtc(it, "OBD-II") }
    }

  override suspend fun clearCodes(): Result<Unit> = communicator.clearCodes()

  override fun liveData(pids: List<String>): Flow<LiveSample> {
    val pidBytes = pids.mapNotNull { parsePidByte(it) }
    val defaults = listOf(
      VciCommunicator.PID_ENGINE_RPM,
      VciCommunicator.PID_VEHICLE_SPEED,
      VciCommunicator.PID_ENGINE_COOLANT_TEMP,
    )
    val poll = if (pidBytes.isEmpty()) defaults else pidBytes
    return communicator.livePid(poll).map { sample ->
      LiveSample(
        pid = sample.pid,
        value = sample.value,
        unit = sample.unit,
        tsMs = sample.tsMs,
      )
    }
  }

  override suspend fun actuate(testId: String): Result<ActuationResult> =
    communicator.actuate(testId).map { r ->
      ActuationResult(testId = r.testId, success = r.success, log = r.log)
    }

  private fun toEngineDtc(d: com.caseforge.scanner.vci.Dtc, module: String) = Dtc(
    module = module,
    code = d.code.removePrefix("PENDING:"),
    description = d.description ?: d.code,
    severity = when (d.severity) {
      com.caseforge.scanner.vci.Severity.Red -> Severity.Red
      com.caseforge.scanner.vci.Severity.Gray -> Severity.Gray
      com.caseforge.scanner.vci.Severity.Amber -> Severity.Amber
    },
    freezeFrame = null,
  )

  private fun parsePidByte(label: String): Int? {
    val s = label.removePrefix("0x").removePrefix("PID_").trim()
  return s.toIntOrNull(16)
      ?: when (s.uppercase()) {
        "RPM", "0C" -> VciCommunicator.PID_ENGINE_RPM
        "SPEED", "0D" -> VciCommunicator.PID_VEHICLE_SPEED
        "COOLANT", "05" -> VciCommunicator.PID_ENGINE_COOLANT_TEMP
        else -> null
      }
  }
}
