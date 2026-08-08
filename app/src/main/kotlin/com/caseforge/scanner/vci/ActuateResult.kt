package com.caseforge.scanner.vci

import com.caseforge.scanner.engine.ActuationResult as EngineActuationResult

/**
 * Structured outcome for [VciCommunicator.actuate] bidirectional active tests.
 *
 * Policy denials use [ActuateStatus.UNSUPPORTED]; wire/protocol failures use [ActuateStatus.ERROR].
 */
enum class ActuateStatus {
    SUCCESS,
    PARTIAL,
    UNSUPPORTED,
    ERROR,
}

data class ActuateResult(
    val testId: String,
    val status: ActuateStatus,
    val log: List<String> = emptyList(),
) {
    val success: Boolean
        get() = status == ActuateStatus.SUCCESS

    companion object {
        fun unsupported(testId: String, reason: String): ActuateResult =
            ActuateResult(
                testId = testId,
                status = ActuateStatus.UNSUPPORTED,
                log = listOf(reason),
            )

        fun error(testId: String, reason: String): ActuateResult =
            ActuateResult(
                testId = testId,
                status = ActuateStatus.ERROR,
                log = listOf(reason),
            )
    }
}

/**
 * Safety inputs for actuation. Callers (Lane 5 / AgentRunner) supply values from settings + approval UI.
 * Defaults deny all actuation until explicitly armed.
 */
data class ActuateGate(
    /** Mirrors [com.caseforge.scanner.data.SettingsRepo.directVciExperimental]. */
    val directVciExperimental: Boolean = false,
    /** Opaque token from [ActuateApproval.mint] after operator approves the pending action. */
    val approvalToken: String? = null,
    /** Plan B marque id (e.g. `ford`, `jeep`) — must be in [ActuateMarquePolicy.TRIAL_MARQUE_IDS]. */
    val marqueId: String? = null,
)

/** Ford + Stellantis trial marques for bidirectional actuation (matches Tier 4 trial scope). */
object ActuateMarquePolicy {
    val TRIAL_MARQUE_IDS: Set<String> = setOf(
        "ford",
        "jeep",
        "dodge",
        "ram",
        "chrysler",
    )

    fun isAllowed(marqueId: String?): Boolean {
        val id = marqueId?.trim()?.lowercase().orEmpty()
        return id.isNotEmpty() && id in TRIAL_MARQUE_IDS
    }
}

/**
 * Short-lived approval binding between [testId] and operator consent.
 * Format: `OK:<testId>:<approvedAtMs>:<sig>` where sig = djb2 of testId|approvedAtMs.
 */
object ActuateApproval {
    const val MAX_AGE_MS: Long = 120_000L
    private const val PREFIX = "OK"

    fun mint(testId: String, approvedAtMs: Long = System.currentTimeMillis()): String {
        val tid = testId.trim()
        val sig = signature(tid, approvedAtMs)
        return "$PREFIX:$tid:$approvedAtMs:$sig"
    }

    fun verify(token: String?, testId: String, nowMs: Long = System.currentTimeMillis()): Boolean {
        if (token.isNullOrBlank()) return false
        val tid = testId.trim()
        val parts = token.split(':')
        if (parts.size != 4 || parts[0] != PREFIX) return false
        if (parts[1] != tid) return false
        val approvedAt = parts[2].toLongOrNull() ?: return false
        if (nowMs - approvedAt > MAX_AGE_MS) return false
        return parts[3] == signature(tid, approvedAt)
    }

    private fun signature(testId: String, approvedAtMs: Long): String {
        var hash = 5381
        val material = "$testId|$approvedAtMs"
        for (ch in material) {
            hash = ((hash shl 5) + hash) + ch.code
        }
        return (hash.toLong() and 0xFFFF_FFFFL).toString(16)
    }
}

/** Bridge for [com.caseforge.scanner.engine.VciDiagnosticPort] until Lane 5 updates the adapter. */
fun ActuateResult.toEngineActuationResult(): EngineActuationResult =
    EngineActuationResult(
        testId = testId,
        success = success,
        log = log,
    )
