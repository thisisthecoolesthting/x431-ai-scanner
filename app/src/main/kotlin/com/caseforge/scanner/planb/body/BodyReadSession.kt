package com.caseforge.scanner.planb.body

import android.content.Context
import com.caseforge.scanner.planb.PlanbMarque
import com.caseforge.scanner.planb.detectPlanbMarque
import com.caseforge.scanner.planb.gateway.GatewaySession

/**
 * Tier 1 orchestrator: delegates to [reader] for DTC and live-data reads.
 *
 * When [planbBodyRead] is true, [readDtcs] routes through [gateway]; [readLiveData] remains on [reader]
 * until gateway live framing exists.
 */
class BodyReadSession(
    private val reader: BodyModuleReader = StubBodyModuleReader(),
    private val planbBodyRead: Boolean = false,
    private val gateway: GatewaySession = GatewaySession(),
    val marqueId: PlanbMarque? = null,
) {

    fun readDtcs(ecuId: String): Result<List<BodyDtc>> =
        if (planbBodyRead) {
            gateway.connect(ecuId).fold(
                onSuccess = { gateway.readDtcs() },
                onFailure = { Result.failure(it) },
            )
        } else {
            reader.readDtcs(ecuId)
        }

    fun readLiveData(ecuId: String, dids: List<String>): Result<List<BodyLiveDatum>> =
        reader.readLiveData(ecuId, dids)

    companion object {
        fun withDetectedMarque(
            context: Context,
            vin: String?,
            reader: BodyModuleReader = StubBodyModuleReader(),
            planbBodyRead: Boolean = false,
            gateway: GatewaySession = GatewaySession(),
        ): BodyReadSession =
            BodyReadSession(
                reader = reader,
                planbBodyRead = planbBodyRead,
                gateway = gateway,
                marqueId = detectPlanbMarque(context, vin),
            )
    }
}
