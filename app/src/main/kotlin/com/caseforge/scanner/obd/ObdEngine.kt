package com.caseforge.scanner.obd

/**
 * Tier 0 facade over [ObdSession] — VIN, DTCs, and a small live-data snapshot.
 */
class ObdEngine(
    val session: ObdSession,
) {

    suspend fun readVin(): String? {
        val raw = session.exchange(ObdModes.MODE_VEHICLE_INFO, ObdModes.Pid09.VIN)
        return ObdVinReader.parseFromReassembled(raw)
            ?: ObdVinReader.parseFromIsoTpFrames(listOf(raw))
    }

    suspend fun readStoredDtcs(): List<ObdDtc> {
        val raw = session.exchange(ObdModes.MODE_SHOW_STORED_DTCS, pid = null)
        return ObdDtcReader.parseStored(raw)
    }

    suspend fun readPendingDtcs(): List<ObdDtc> {
        val raw = session.exchange(ObdModes.MODE_SHOW_PENDING_DTCS, pid = null)
        return ObdDtcReader.parsePending(raw)
    }

    suspend fun readLiveSnapshot(): ObdLivePidReader.LiveSnapshot {
        val rpmResp = session.exchange(ObdModes.MODE_SHOW_CURRENT_DATA, ObdModes.Pid01.ENGINE_RPM)
        val clResp = session.exchange(ObdModes.MODE_SHOW_CURRENT_DATA, ObdModes.Pid01.COOLANT_TEMP)
        val spdResp = session.exchange(ObdModes.MODE_SHOW_CURRENT_DATA, ObdModes.Pid01.VEHICLE_SPEED)
        return ObdLivePidReader.LiveSnapshot(
            rpm = ObdLivePidReader.decodeRpmResponse(rpmResp),
            coolantCelsius = ObdLivePidReader.decodeCoolantResponse(clResp),
            speedKmh = ObdLivePidReader.decodeSpeedResponse(spdResp),
        )
    }
}
