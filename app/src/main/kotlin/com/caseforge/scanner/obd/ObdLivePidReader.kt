package com.caseforge.scanner.obd

/**
 * Mode 01 PID decoders (SAE J1979). [decodeFromMode01Response] expects a positive response:
 * `41 [pid] [A] ([B])`.
 */
object ObdLivePidReader {

    data class LiveSnapshot(
        val rpm: Int?,
        val coolantCelsius: Int?,
        val speedKmh: Int?,
    )

    /**
     * Decodes a single-PID Mode 01 response. Returns null if [response] does not match `41 [expectedPid]`.
     */
    fun decodeFromMode01Response(response: ByteArray, expectedPid: Int): ByteArray? {
        if (response.size < 3) return null
        val svc = response[0].toInt() and 0xFF
        if (svc != ObdModes.positiveResponse(ObdModes.MODE_SHOW_CURRENT_DATA)) return null
        val pid = response[1].toInt() and 0xFF
        if (pid != expectedPid) return null
        return response.copyOfRange(2, response.size)
    }

    /** Engine RPM — PID `0x0C`: `((A*256)+B)/4` */
    fun decodeRpm(a: Int, b: Int): Int = ((a and 0xFF) * 256 + (b and 0xFF)) / 4

    /** Coolant °C — PID `0x05`: `A-40` */
    fun decodeCoolantCelsius(a: Int): Int = (a and 0xFF) - 40

    /** Vehicle speed — PID `0x0D`: `A` km/h */
    fun decodeSpeedKmh(a: Int): Int = a and 0xFF

    fun decodeRpmResponse(response: ByteArray): Int? {
        val data = decodeFromMode01Response(response, ObdModes.Pid01.ENGINE_RPM) ?: return null
        if (data.size < 2) return null
        return decodeRpm(data[0].toInt(), data[1].toInt())
    }

    fun decodeCoolantResponse(response: ByteArray): Int? {
        val data = decodeFromMode01Response(response, ObdModes.Pid01.COOLANT_TEMP) ?: return null
        if (data.isEmpty()) return null
        return decodeCoolantCelsius(data[0].toInt())
    }

    fun decodeSpeedResponse(response: ByteArray): Int? {
        val data = decodeFromMode01Response(response, ObdModes.Pid01.VEHICLE_SPEED) ?: return null
        if (data.isEmpty()) return null
        return decodeSpeedKmh(data[0].toInt())
    }
}
