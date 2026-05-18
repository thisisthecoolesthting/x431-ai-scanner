package com.caseforge.scanner.engine

import com.caseforge.scanner.agent.ScreenSnapshot
import kotlinx.serialization.json.JsonObject

// ---------------------------------------------------------------------------
// Domain types surfaced by EngineDriver
// ---------------------------------------------------------------------------

data class Dtc(
    val module: String,
    val code: String,
    val description: String,
    val severity: Severity,
    val freezeFrame: JsonObject?,
)

data class FullScanResult(
    val modules: List<ModuleScan>,
    val durationMs: Long,
)

data class ModuleScan(
    val name: String,
    val dtcs: List<Dtc>,
    val skipped: Boolean,
    val skipReason: String?,
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

data class CapabilityResult(val success: Boolean, val output: String)

enum class Severity { Red, Amber, Gray }

// ---------------------------------------------------------------------------
// CapabilityRegistry — B1 will supply the real implementation
// ---------------------------------------------------------------------------

/** Registry that resolves capability descriptors by id. B1 provides the impl. */
interface CapabilityRegistry {
    fun find(id: String): CapabilityMap.Capability?
}

// ---------------------------------------------------------------------------
// A11yPort — thin interface over ScannerAccessibilityService.
//
// EngineDriver depends on this interface (not the concrete class) so that unit
// tests can inject a plain JVM fake without pulling in the Android framework.
// ScannerAccessibilityService implements this interface; the verbatim constructor
// parameter type in EngineDriver is preserved via the interface.
// ---------------------------------------------------------------------------

/**
 * Minimal surface of [com.caseforge.scanner.agent.ScannerAccessibilityService]
 * that [EngineDriver] actually uses.
 *
 * Implemented by the real [com.caseforge.scanner.agent.ScannerAccessibilityService]
 * and by plain JVM fakes in tests.
 */
interface A11yPort {
    /** Tap the first element whose text contains [query] (case-insensitive). Returns true if a tap was dispatched. */
    fun tapByText(query: String, exact: Boolean = false): Boolean

    /** Read the current X431 screen as a serialisable tree. */
    fun readScreen(): ScreenSnapshot
}

// ---------------------------------------------------------------------------
// Typed exceptions — all EngineDriver failures are wrapped in one of these
// ---------------------------------------------------------------------------

sealed class EngineException(msg: String, cause: Throwable? = null) : Exception(msg, cause) {

    class Timeout(val capId: String, val stepIdx: Int, timeoutSec: Int) :
        EngineException("Capability $capId timed out at step $stepIdx after ${timeoutSec}s")

    class CapabilityNotFound(val capId: String) :
        EngineException("Capability '$capId' not in registry")

    class StepFailed(val capId: String, val stepIdx: Int, reason: String) :
        EngineException("Capability $capId step $stepIdx failed: $reason")
}
