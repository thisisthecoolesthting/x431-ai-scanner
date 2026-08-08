package com.caseforge.scanner.planb.programming

import android.content.Context
import com.caseforge.scanner.planb.PlanbMarque

/**
 * Tier 4 programming context: checklists load from assets; flash/program requests cannot run in-app (no rollback path).
 */
class ProgrammingSession(
    context: Context,
    val marque: PlanbMarque,
    private val loader: ProgrammingChecklistLoader = ProgrammingChecklistLoader,
    private val auditLog: ProgrammingAuditLog = ProgrammingAuditLog(context),
    checklist: ProgrammingChecklist? = null,
) {

    private val checklist: ProgrammingChecklist? = checklist ?: loader.load(context, marque)

    fun readChecklist(): ProgrammingChecklist? = checklist

    /**
     * Scaffold flash request pipeline: validate → audit → partner gate → structured result.
     * Does not execute flash opcodes or VCI writes.
     */
    fun requestFlash(op: FlashOp): FlashRequestResult {
        require(op.id.isNotBlank()) { "Flash op id must be non-blank" }
        val known = checklist?.entries?.firstOrNull { it.id == op.id }
            ?: throw IllegalArgumentException("Unknown flash op: ${op.id}")

        val auditEntryId = auditLog.recordFlashRequest(marque, known)
        val status = ProgrammingGate.resolveFlashRequest(known)
        val result = ProgrammingGate.toFlashRequestResult(known, auditEntryId, status)
        auditLog.recordFlashOutcome(auditEntryId, result)
        return result
    }
}
