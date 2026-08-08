package com.caseforge.scanner.planb.gateway

import com.caseforge.scanner.planb.PlanbMarque
import com.caseforge.scanner.planb.body.BodyDtc
import com.caseforge.scanner.planb.gateway.replay.GoldenReplaySource
import com.caseforge.scanner.vci.VciCommunicator
import kotlinx.coroutines.runBlocking

/**
 * Plan B gateway lane session: ISO-TP / UDS surface over routed CAN identifiers.
 *
 * **Live:** when [gatewayReplayEnabled] is false and [vciCommunicator] is connected, [readDtcs] uses
 * [GatewayLiveReader] with the connected [EcuEntry] request/response CAN IDs from [defaultEntries].
 *
 * **Replay (tests / debug):** when [gatewayReplayEnabled] is true and the session marque matches
 * [goldenReplaySource], [readDtcs] returns synthetic codes from the golden scaffold — **Jeep/Stellantis**
 * generic stubs vs **Ford** Mode 03 replay from bundled JSONL (`planb/gateway/replay/ford_golden.log`).
 *
 * Gateway policy notes: [StellantisGatewayNotes].
 */
class GatewaySession(
    /**
     * Routed ECUs for forthcoming IsoTP / CAN-ID binding; defaults from [jeepWedgeDefaults].
     * Retained so constructor injection can swap maps during golden-log replay without API churn.
     */
    internal val defaultEntries: List<EcuEntry> = jeepWedgeDefaults(),
    /**
     * Optional marque slug or hint (e.g. from VIN normalization); used for replay marque matching when set.
     */
    val marque: String? = null,
    /** When true with a matching [goldenReplaySource], [readDtcs] may return scaffold DTC rows. */
    private val gatewayReplayEnabled: Boolean = false,
    private val goldenReplaySource: GoldenReplaySource? = null,
    /**
     * Live VCI transport for routed ISO-TP reads when replay is off. Null keeps the historical empty stub path.
     */
    private val vciCommunicator: VciCommunicator? = null,
    /**
     * Safe perf scaffold: when true, avoid redundant reconnect for the same ECU id and reuse
     * the current gateway session object. Keep OFF by default until more vehicle validation lands.
     */
    private val reuseConnectionScaffoldEnabled: Boolean = GATEWAY_POOL_ENABLED,
) {
    private var connectedId: String? = null
    internal var connectOperations: Int = 0
        private set

    /**
     * Collapse expanded marque aliases to one replay family key so golden sources and session hints can
     * still match even when one side uses an alias (for example Lexus vs Toyota, GMC vs Chevrolet).
     */
    private fun replayFamilyKey(rawMarque: String?): String? {
        val key = rawMarque?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: return null
        return when (key) {
            GatewayMap.MARQUE_DODGE,
            GatewayMap.MARQUE_RAM,
            GatewayMap.MARQUE_CHRYSLER,
            -> GatewayMap.MARQUE_DODGE
            GatewayMap.MARQUE_CHEVROLET,
            GatewayMap.MARQUE_GMC,
            GatewayMap.MARQUE_BUICK,
            GatewayMap.MARQUE_CADILLAC,
            -> GatewayMap.MARQUE_CHEVROLET
            GatewayMap.MARQUE_TOYOTA,
            GatewayMap.MARQUE_LEXUS,
            -> GatewayMap.MARQUE_TOYOTA
            GatewayMap.MARQUE_HONDA,
            GatewayMap.MARQUE_ACURA,
            -> GatewayMap.MARQUE_HONDA
            GatewayMap.MARQUE_NISSAN,
            GatewayMap.MARQUE_INFINITI,
            -> GatewayMap.MARQUE_NISSAN
            GatewayMap.MARQUE_HYUNDAI,
            GatewayMap.MARQUE_KIA,
            -> GatewayMap.MARQUE_HYUNDAI
            GatewayMap.MARQUE_JEEP,
            GatewayMap.MARQUE_FORD,
            -> key
            else -> PlanbMarque.fromId(key)?.id
        }
    }

    /** Associates this session with logical [ecuId] (typically an [EcuEntry.id]). */
    fun connect(ecuId: String): Result<Unit> {
        if (reuseConnectionScaffoldEnabled && connectedId == ecuId) {
            return Result.success(Unit)
        }
        connectOperations += 1
        connectedId = ecuId
        return Result.success(Unit)
    }

    /**
     * Reads stored DTCs from the gateway-target ECU established by [connect].
     *
     * Replay branch takes precedence when [gatewayReplayEnabled] is true. Otherwise, when
     * [vciCommunicator] is connected, performs a live routed ISO-TP Mode 03 read for the connected entry.
     */
    fun readDtcs(): Result<List<BodyDtc>> {
        if (connectedId == null) {
            return Result.failure(IllegalStateException("GatewaySession.readDtcs before connect(ecuId)"))
        }
        if (gatewayReplayEnabled) {
            return readDtcsReplay()
        }
        return readDtcsLive()
    }

    /** Suspend-friendly live read — same branch rules as [readDtcs]. */
    suspend fun readDtcsSuspend(): Result<List<BodyDtc>> {
        if (connectedId == null) {
            return Result.failure(IllegalStateException("GatewaySession.readDtcs before connect(ecuId)"))
        }
        if (gatewayReplayEnabled) {
            return readDtcsReplay()
        }
        return readDtcsLiveSuspend()
    }

    private fun readDtcsReplay(): Result<List<BodyDtc>> {
        val source = goldenReplaySource ?: return Result.success(emptyList())
        val sessionFamily = replayFamilyKey(marque)
        val sourceFamily = replayFamilyKey(source.marque.id)
        if (sessionFamily != null && sessionFamily == sourceFamily) {
            return Result.success(source.syntheticReplayDtcs())
        }
        return Result.success(emptyList())
    }

    private fun readDtcsLive(): Result<List<BodyDtc>> {
        val comm = vciCommunicator ?: return Result.success(emptyList())
        val entry = connectedEntry() ?: return Result.success(emptyList())
        if (!comm.isTransportConnected()) {
            return Result.success(emptyList())
        }
        return runBlocking {
            GatewayLiveReader.readStoredDtcs(comm, entry)
        }
    }

    private suspend fun readDtcsLiveSuspend(): Result<List<BodyDtc>> {
        val comm = vciCommunicator ?: return Result.success(emptyList())
        val entry = connectedEntry() ?: return Result.success(emptyList())
        if (!comm.isTransportConnected()) {
            return Result.success(emptyList())
        }
        return GatewayLiveReader.readStoredDtcs(comm, entry)
    }

    private fun connectedEntry(): EcuEntry? {
        val id = connectedId ?: return null
        defaultEntries.firstOrNull { it.id.equals(id, ignoreCase = true) }?.let { return it }
        marque?.let { m -> GatewayMap.lookupEntry(m, id)?.let { return it } }
        return null
    }

    companion object {
        /** Feature flag: gateway pooling/reuse for same marque/VIN session lanes. */
        const val GATEWAY_POOL_ENABLED: Boolean = false
    }
}
