package com.caseforge.scanner.planb.programming

/**
 * Structured outcome of [ProgrammingSession.requestFlash] — scaffold only; no VCI writes.
 */
sealed class FlashRequestResult {
    abstract val op: FlashOp
    abstract val auditEntryId: String
    abstract val message: String

    data class Blocked(
        override val op: FlashOp,
        override val auditEntryId: String,
        override val message: String = ProgrammingGate.TIER4_BLOCKED.message.orEmpty(),
    ) : FlashRequestResult()

    data class PartnerRequired(
        override val op: FlashOp,
        override val auditEntryId: String,
        override val message: String = ProgrammingGate.PARTNER_REQUIRED.message.orEmpty(),
    ) : FlashRequestResult()

    data class QueuedForLab(
        override val op: FlashOp,
        override val auditEntryId: String,
        override val message: String,
    ) : FlashRequestResult()
}
