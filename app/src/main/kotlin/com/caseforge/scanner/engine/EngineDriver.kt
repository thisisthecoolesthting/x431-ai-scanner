package com.caseforge.scanner.engine

import com.caseforge.scanner.agent.AgentActionLog
import com.caseforge.scanner.agent.ScannerAccessibilityService
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlin.system.measureTimeMillis

/**
 * High-level orchestration layer that walks [CapabilityMap] paths via
 * [ScannerAccessibilityService], polls [EngineScraper] for state changes,
 * and exposes a typed public API consumed by the overlay UI.
 *
 * All failures are returned as [Result.failure] wrapping a typed [EngineException];
 * nothing is ever thrown to the caller. No [kotlinx.coroutines.runBlocking] is used
 * in any production code path.
 *
 * Thread / coroutine safety: all public methods are suspend functions or return cold
 * [Flow]s. The [state] flow is a [MutableStateFlow] and updated atomically.
 */
class EngineDriver(
    private val a11y: ScannerAccessibilityService,
    private val capabilities: CapabilityRegistry,
    private val scraper: EngineScraper,
    private val state: MutableStateFlow<EngineState>,
    private val actionLog: AgentActionLog,
    private val interStepDelayMs: Long = 900L,
    private val dataRoute: EngineDataRoute = EngineDataRoute.OVERLAY,
    private val vciPort: VciDiagnosticPort? = null,
) {

    companion object {
        /** Polling interval for live-data PIDs. */
        const val LIVE_DATA_POLL_MS = 200L

        private val _activeOps = MutableStateFlow(0)
        private val _workActive = MutableStateFlow(false)
        val workActive: StateFlow<Boolean> = _workActive.asStateFlow()

        private fun enterWork() {
            _activeOps.update { it + 1 }
            _workActive.value = true
        }

        private fun exitWork() {
            _activeOps.update { (it - 1).coerceAtLeast(0) }
            _workActive.value = _activeOps.value > 0
        }
    }

    // ------------------------------------------------------------------
    // Public API
    // ------------------------------------------------------------------

    /**
     * Navigate to and execute the capability identified by [id].
     *
     * Steps:
     *  1. Resolve the capability (or return [EngineException.CapabilityNotFound]).
     *  2. For each path element, call [ScannerAccessibilityService.tapByText].
     *  3. After each tap, delay [interStepDelayMs] then poll [EngineScraper] until
     *     the [CapabilityMap.Capability.doneWhen] marker appears or
     *     [CapabilityMap.Capability.timeoutSec] elapses.
     *  4. Log every step to [AgentActionLog].
     *
     * @return [Result.success] containing a [JsonObject] built from the final
     *   [EngineState], or [Result.failure] wrapping an [EngineException].
     */
    suspend fun runCapability(id: String): Result<JsonObject> {
        if (dataRoute == EngineDataRoute.DIRECT_VCI) {
            val port = vciPort ?: return Result.failure(
                EngineException.StepFailed(id, -1, "Direct VCI not connected"),
            )
            return safeRun(id) { port.runCapability(id).getOrThrow() }
        }

        val cap = capabilities.find(id)
            ?: return Result.failure(EngineException.CapabilityNotFound(id))

        return safeRun(cap.id) {
            withTimeout(cap.timeoutSec * 1_000L) {
                walkPath(cap)
                pollUntilDone(cap, startStepIdx = cap.path.size)
            }.let { finalState -> stateToJsonObject(finalState) }
        }
    }

    /**
     * Run a full vehicle scan.
     *
     * Drives the "full_scan" capability then parses the resulting [EngineState]
     * into a [FullScanResult]. Module-level grouping is derived from whatever
     * DTCs the scraper surfaced during the scan.
     */
    suspend fun fullScan(): Result<FullScanResult> {
        if (dataRoute == EngineDataRoute.DIRECT_VCI) {
            val port = vciPort ?: return Result.failure(
                EngineException.StepFailed("full_scan", -1, "Direct VCI not connected"),
            )
            return safeRun("full_scan") { port.fullScan().getOrThrow() }
        }

        val cap = capabilities.find("full_scan")
            ?: return Result.failure(EngineException.CapabilityNotFound("full_scan"))

        return safeRun(cap.id) {
            val startMs = System.currentTimeMillis()

            val finalState = withTimeout(cap.timeoutSec * 1_000L) {
                walkPath(cap)
                pollUntilDone(cap, startStepIdx = cap.path.size)
            }

            val durationMs = System.currentTimeMillis() - startMs

            // Group raw EngineState DTCs by module.
            val byModule = finalState.dtcs.groupBy { it.module ?: "Unknown" }
            val modules = byModule.map { (moduleName, rawDtcs) ->
                ModuleScan(
                    name = moduleName,
                    dtcs = rawDtcs.map { raw ->
                        Dtc(
                            module = moduleName,
                            code = raw.code,
                            description = raw.description ?: raw.code,
                            severity = Severity.Amber,
                            freezeFrame = null,
                        )
                    },
                    skipped = false,
                    skipReason = null,
                )
            }

            FullScanResult(modules = modules, durationMs = durationMs)
        }
    }

    /**
     * Read diagnostic trouble codes, optionally filtered to a single [module].
     */
    suspend fun readDtcs(module: String? = null): Result<List<Dtc>> {
        if (dataRoute == EngineDataRoute.DIRECT_VCI) {
            val port = vciPort ?: return Result.failure(
                EngineException.StepFailed("read_dtcs", -1, "Direct VCI not connected"),
            )
            return safeRun("read_dtcs") { port.readDtcs(module).getOrThrow() }
        }

        val cap = capabilities.find("read_dtcs")
            ?: return Result.failure(EngineException.CapabilityNotFound("read_dtcs"))

        return safeRun(cap.id) {
            val finalState = withTimeout(cap.timeoutSec * 1_000L) {
                walkPath(cap)
                pollUntilDone(cap, startStepIdx = cap.path.size)
            }

            finalState.dtcs
                .filter { raw ->
                    module == null || raw.module?.equals(module, ignoreCase = true) == true
                }
                .map { raw ->
                    Dtc(
                        module = raw.module ?: "Unknown",
                        code = raw.code,
                        description = raw.description ?: raw.code,
                        severity = Severity.Amber,
                        freezeFrame = null,
                    )
                }
        }
    }

    /**
     * Clear all stored DTCs. Returns [Result.success] of [Unit] when the OEM diagnostic app
     * confirms "clear successfully", or [Result.failure] on timeout/navigation error.
     */
    suspend fun clearCodes(): Result<Unit> {
        if (dataRoute == EngineDataRoute.DIRECT_VCI) {
            val port = vciPort ?: return Result.failure(
                EngineException.StepFailed("clear_dtcs", -1, "Direct VCI not connected"),
            )
            return safeRun("clear_dtcs") { port.clearCodes().getOrThrow() }
        }

        val cap = capabilities.find("clear_dtcs")
            ?: return Result.failure(EngineException.CapabilityNotFound("clear_dtcs"))

        return safeRun(cap.id) {
            withTimeout(cap.timeoutSec * 1_000L) {
                walkPath(cap)
                pollUntilDone(cap, startStepIdx = cap.path.size)
            }
            Unit
        }
    }

    /**
     * Cold [Flow] that emits a [LiveSample] for each PID in [pids] every 200 ms.
     * Cancelling the collecting coroutine stops all polling — no leak.
     *
     * The flow enters the OEM diagnostic app live-data screen once on first collection, then
     * ticks at [LIVE_DATA_POLL_MS] reading the current [EngineState] values.
     */
    fun liveData(pids: List<String>): Flow<LiveSample> {
        if (dataRoute == EngineDataRoute.DIRECT_VCI) {
            val port = vciPort ?: return flow { }
            return port.liveData(pids)
        }
        return liveDataOverlay(pids)
    }

    private fun liveDataOverlay(pids: List<String>): Flow<LiveSample> = flow {
        val cap = capabilities.find("live_data")
        if (cap != null) {
            // Best-effort navigation — ignore errors so the caller still gets data
            runCatching {
                withTimeout(cap.timeoutSec * 1_000L) { walkPath(cap) }
            }
        }

        while (currentCoroutineContext().isActive) {
            val snapshot = state.value
            val tsMs = System.currentTimeMillis()

            for (pid in pids) {
                val value = snapshot.liveData[pid] ?: continue
                emit(LiveSample(pid = pid, value = value, unit = "", tsMs = tsMs))
            }

            delay(LIVE_DATA_POLL_MS)
        }
    }

    /**
     * Trigger a bidirectional actuation test identified by [testId].
     */
    suspend fun runCapabilityForSequence(id: String, params: Map<String, String> = emptyMap()): CapabilityResult {
        val resolved = when (id) {
            "injector_cutout", "injector_restore", "evap_vent_close" -> "actuation"
            "clear_dtc" -> "clear_dtcs"
            else -> id
        }
        val cap = capabilities.find(resolved) ?: return CapabilityResult(false, "missing $id")
        actionLog.event("sequence.capability", "$id->$resolved")
        val r = runCapability(cap.id)
        return CapabilityResult(r.isSuccess, r.fold({ it.toString() }, { it.message ?: "fail" }))
    }

    suspend fun queryPid(pid: String): String? {
        refreshState()
        val k = pid.removePrefix("0x").lowercase()
        return state.value.liveData[k]?.toString() ?: state.value.liveData[pid]?.toString()
    }

    fun notifyUser(message: String) = actionLog.event("sequence.prompt", message.take(500))

    suspend fun actuate(testId: String): Result<ActuationResult> {
        if (dataRoute == EngineDataRoute.DIRECT_VCI) {
            val port = vciPort ?: return Result.failure(
                EngineException.StepFailed("actuation", -1, "Direct VCI not connected"),
            )
            return safeRun("actuation") { port.actuate(testId).getOrThrow() }
        }

        val cap = capabilities.find("actuation")
            ?: return Result.failure(EngineException.CapabilityNotFound("actuation"))

        return safeRun(cap.id) {
            val log = mutableListOf<String>()
            var success = false

            withTimeout(cap.timeoutSec * 1_000L) {
                walkPath(cap, extraLog = log)
                val finalState = pollUntilDone(cap, startStepIdx = cap.path.size)

                success = finalState.screen is ScreenKind.ActuationTest ||
                    finalState.errorBanner == null
                log += "final_screen=${finalState.screen::class.simpleName}"
            }

            ActuationResult(testId = testId, success = success, log = log)
        }
    }

    // ------------------------------------------------------------------
    // Internal helpers
    // ------------------------------------------------------------------

    /**
     * Runs [block] catching all exceptions and wrapping them as typed
     * [EngineException]s so callers always receive [Result.failure] with a
     * known exception type — never a raw [Throwable].
     */
    private suspend fun <T> safeRun(capId: String, block: suspend () -> T): Result<T> {
        enterWork()
        return try {
            Result.success(block())
        } catch (e: EngineException) {
            Result.failure(e)
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            Result.failure(EngineException.StepFailed(capId, -1, "coroutine timeout: ${e.message}"))
        } catch (e: Exception) {
            Result.failure(
                EngineException.StepFailed(
                    capId = capId,
                    stepIdx = -1,
                    reason = e.message ?: e::class.simpleName ?: "unknown",
                )
            )
        } finally {
            exitWork()
        }
    }

    /**
     * Walk every step in [cap.path], tapping each label via [a11y].
     * Delays [interStepDelayMs] between steps and logs every action.
     *
     * @throws EngineException.StepFailed if a tap returns false (node not found).
     */
    private suspend fun walkPath(
        cap: CapabilityMap.Capability,
        extraLog: MutableList<String>? = null,
    ) {
        cap.path.forEachIndexed { stepIdx, label ->
            val elapsedMs = measureTimeMillis {
                val tapped = a11y.tapByText(label)

                actionLog.event(
                    kind = "step",
                    detail = "cap=${cap.id} step=$stepIdx label=$label tapped=$tapped",
                )
                extraLog?.add("step $stepIdx: tap '$label' -> $tapped")

                if (!tapped) {
                    throw EngineException.StepFailed(
                        capId = cap.id,
                        stepIdx = stepIdx,
                        reason = "tapByText('$label') returned false — node not found",
                    )
                }
            }

            actionLog.event(
                kind = "step_timing",
                detail = "cap=${cap.id} step=$stepIdx elapsedMs=$elapsedMs",
            )

            if (stepIdx < cap.path.lastIndex) {
                delay(interStepDelayMs)
                refreshState()
            }
        }
    }

    /**
     * Poll [EngineScraper] until [cap.doneWhen] appears in the screen text,
     * or the outer [withTimeout] fires (at which point we rethrow as
     * [EngineException.Timeout]).
     *
     * If [cap.doneWhen] is null the function returns immediately after one scrape.
     */
    private suspend fun pollUntilDone(
        cap: CapabilityMap.Capability,
        startStepIdx: Int,
    ): EngineState {
        val doneMarker = cap.doneWhen
            ?: return refreshState()

        val deadline = System.currentTimeMillis() + cap.timeoutSec * 1_000L

        while (System.currentTimeMillis() < deadline) {
            val current = refreshState()
            val screenText = current.raw?.text?.lowercase().orEmpty()
            if (screenText.contains(doneMarker.lowercase())) {
                return current
            }
            delay(interStepDelayMs)
        }

        throw EngineException.Timeout(
            capId = cap.id,
            stepIdx = startStepIdx,
            timeoutSec = cap.timeoutSec,
        )
    }

    /** Snap the current screen and push it into [state]. Returns the new value. */
    private fun refreshState(): EngineState {
        val snapshot = a11y.readScreen()
        val newState = scraper.scrape(snapshot)
        state.value = newState
        return newState
    }

    // ------------------------------------------------------------------
    // Serialisation helpers
    // ------------------------------------------------------------------

    private fun stateToJsonObject(engineState: EngineState): JsonObject = buildJsonObject {
        put("screen", engineState.screen::class.simpleName ?: "Unknown")
        put("vehicleVin", engineState.vehicleVin)
        put("busy", engineState.busy)
        put("errorBanner", engineState.errorBanner)
        put("updatedAtMs", engineState.updatedAtMs)
        putJsonArray("dtcs") {
            engineState.dtcs.forEach { dtc ->
                add(buildJsonObject {
                    put("code", dtc.code)
                    put("module", dtc.module)
                    put("description", dtc.description)
                    put("status", dtc.status)
                })
            }
        }
    }

}
