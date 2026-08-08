package com.caseforge.scanner.ui.tuning

import com.caseforge.scanner.engine.VciDiagnosticPort
import com.caseforge.scanner.vci.VciCommunicator
import com.caseforge.scanner.vci.VciDiagnosticAdapter

/**
 * Shared connection context for tuning / reference screens (read-only).
 */
data class TuningSessionContext(
    val vciConnected: Boolean,
    val vehicleVin: String?,
    val lastRecordedVin: String?,
    val tier4ProgrammingReady: Boolean,
    val gatewayReplayEnabled: Boolean,
    val diagnosticPort: VciDiagnosticPort?,
) {
    val vciCommunicator: VciCommunicator?
        get() = (diagnosticPort as? VciDiagnosticAdapter)?.communicator
}
