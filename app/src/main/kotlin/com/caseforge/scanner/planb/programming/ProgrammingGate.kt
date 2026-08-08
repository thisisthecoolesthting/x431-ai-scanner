package com.caseforge.scanner.planb.programming

/**
 * Tier 4 programming is not executed by this application; callers receive one of these errors.
 */
sealed class ProgrammingGate(message: String) : Exception(message) {
    /** In-app Tier 4 programming and flash routines are not available. */
    object TIER4_BLOCKED : ProgrammingGate(
        "Tier 4 programming is not automated in this app. Use the checklist for reference before authorized service.",
    )

    /** The operation requires tooling and procedures outside this app's scope. */
    object PARTNER_REQUIRED : ProgrammingGate(
        "This step requires appropriate authorized equipment and technician training.",
    )

    /** Gateway write / flash transfer requires partner lab authorization; no on-vehicle writes in-app. */
    object PARTNER_LAB_REQUIRED : ProgrammingGate(
        "Gateway write and flash data transfer require partner lab authorization and equipment. " +
            "This app does not perform on-vehicle write blocks.",
    )

    enum class FlashRequestStatus {
        BLOCKED,
        PARTNER_REQUIRED,
        QUEUED_FOR_LAB,
    }

    companion object {
        /**
         * Partner-gated resolution for flash scaffold requests. No VCI opcodes are emitted.
         * Priority: partner handoff → lab queue → in-app blocked.
         */
        fun resolveFlashRequest(op: FlashOp): FlashRequestStatus = when {
            requiresPartnerHandoff(op) -> FlashRequestStatus.PARTNER_REQUIRED
            isLabQueueCandidate(op) -> FlashRequestStatus.QUEUED_FOR_LAB
            else -> FlashRequestStatus.BLOCKED
        }

        fun toFlashRequestResult(
            op: FlashOp,
            auditEntryId: String,
            status: FlashRequestStatus,
        ): FlashRequestResult = when (status) {
            FlashRequestStatus.BLOCKED -> FlashRequestResult.Blocked(
                op = op,
                auditEntryId = auditEntryId,
            )
            FlashRequestStatus.PARTNER_REQUIRED -> FlashRequestResult.PartnerRequired(
                op = op,
                auditEntryId = auditEntryId,
            )
            FlashRequestStatus.QUEUED_FOR_LAB -> FlashRequestResult.QueuedForLab(
                op = op,
                auditEntryId = auditEntryId,
                message = PARTNER_LAB_REQUIRED.message.orEmpty(),
            )
        }

        private fun requiresPartnerHandoff(op: FlashOp): Boolean =
            op.partnerHandoff || op.isSkreemEntry()

        private fun isLabQueueCandidate(op: FlashOp): Boolean =
            op.applyMode.equals("lab_queue", ignoreCase = true) ||
                op.applyMode.equals("queued_for_lab", ignoreCase = true)
    }
}
