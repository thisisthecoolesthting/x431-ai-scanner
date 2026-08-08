package com.caseforge.scanner.engine

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonObject

/** JVM-testable surface for Direct VCI when [EngineDataRoute.DIRECT_VCI] is active. */
interface VciDiagnosticPort {
  suspend fun runCapability(id: String): Result<JsonObject>
  suspend fun fullScan(): Result<FullScanResult>
  suspend fun readDtcs(module: String? = null): Result<List<Dtc>>
  suspend fun clearCodes(): Result<Unit>
  fun liveData(pids: List<String>): Flow<LiveSample>
  suspend fun actuate(testId: String): Result<ActuationResult>
}

enum class EngineDataRoute {
  OVERLAY,
  DIRECT_VCI,
}
