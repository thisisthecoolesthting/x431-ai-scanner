package com.caseforge.scanner.planb.gateway

import com.caseforge.scanner.planb.PlanbMarque
import com.caseforge.scanner.planb.programming.ProgrammingGate

/**
 * Plan B gateway write lane — partner-gated UDS write / flash request surface.
 *
 * **Stub:** validates session shape only; [writeBlock] and [prepareSecurityAccess] always fail with
 * [ProgrammingGate.PARTNER_LAB_REQUIRED]. No bytes are transmitted to the vehicle.
 */
class GatewayWriteSession(
    val marque: PlanbMarque? = null,
) {
    private var connectedEcuId: String? = null

    /** Placeholder UDS SecurityAccess level; positive levels reserved for future partner-lab wiring. */
    enum class SecurityAccessLevel(val udsLevel: Int) {
        LOCKED(0),
        PARTNER_LAB_PLACEHOLDER(0x03),
    }

    private var securityAccessLevel: SecurityAccessLevel = SecurityAccessLevel.LOCKED

    /** Associates this write session with logical [ecuId] (typically an [EcuEntry.id]). */
    fun connect(ecuId: String): Result<Unit> {
        if (ecuId.isBlank()) {
            return Result.failure(IllegalArgumentException("ecuId must be non-blank"))
        }
        connectedEcuId = ecuId
        return Result.success(Unit)
    }

    /**
     * Records intended SecurityAccess level for partner workflows; never unlocks on-vehicle.
     * Stub: always returns [ProgrammingGate.PARTNER_LAB_REQUIRED].
     */
    fun prepareSecurityAccess(level: SecurityAccessLevel): Result<Unit> {
        if (connectedEcuId == null) {
            return Result.failure(
                IllegalStateException("GatewayWriteSession.prepareSecurityAccess before connect(ecuId)"),
            )
        }
        securityAccessLevel = level
        return Result.failure(ProgrammingGate.PARTNER_LAB_REQUIRED)
    }

    /**
     * Write-block / transfer-data stub. Validates [request] metadata only.
     * Stub: always returns [ProgrammingGate.PARTNER_LAB_REQUIRED]; no on-vehicle writes.
     */
    fun writeBlock(request: WriteBlockRequest): Result<Unit> {
        if (connectedEcuId == null) {
            return Result.failure(
                IllegalStateException("GatewayWriteSession.writeBlock before connect(ecuId)"),
            )
        }
        if (request.blockIndex < 0) {
            return Result.failure(IllegalArgumentException("blockIndex must be non-negative"))
        }
        if (request.payload.isEmpty()) {
            return Result.failure(IllegalArgumentException("payload must not be empty"))
        }
        return Result.failure(ProgrammingGate.PARTNER_LAB_REQUIRED)
    }

    fun currentSecurityAccessLevel(): SecurityAccessLevel = securityAccessLevel
}

/** Metadata for a single gateway write / transfer-data block (payload is not sent on-vehicle in stub). */
data class WriteBlockRequest(
    val blockIndex: Int,
    val payload: ByteArray,
    val totalBlocks: Int? = null,
)
