package com.caseforge.scanner.planb.coding

import android.content.Context
import com.caseforge.scanner.planb.PlanbMarque

private const val G4_BLOCK = "Plan B reversible coding apply/rollback pending G4 (gateway session + validated DID/RoutineControl)."

/**
 * Per-marque coding context: loads the checklist from assets; mutating paths are blocked until G4.
 */
class CodingSession(
    context: Context,
    val marque: PlanbMarque,
    private val loader: CodingChecklistLoader = CodingChecklistLoader,
) {
    val checklist: CodingChecklist? = loader.load(context, marque)

    fun apply(opId: String): Result<Unit> {
        if (opId.isBlank()) {
            return Result.failure(IllegalArgumentException("opId must be non-blank"))
        }
        checklist?.entries?.firstOrNull { it.id == opId }
            ?: return Result.failure(IllegalArgumentException("Unknown coding op: $opId"))
        return Result.failure(IllegalStateException(G4_BLOCK))
    }

    fun rollback(opId: String): Result<Unit> {
        if (opId.isBlank()) {
            return Result.failure(IllegalArgumentException("opId must be non-blank"))
        }
        val op = checklist?.entries?.firstOrNull { it.id == opId }
            ?: return Result.failure(IllegalArgumentException("Unknown coding op: $opId"))
        if (!op.rollbackSupported) {
            return Result.failure(IllegalStateException("Rollback not supported for op: $opId"))
        }
        return Result.failure(IllegalStateException(G4_BLOCK))
    }
}
