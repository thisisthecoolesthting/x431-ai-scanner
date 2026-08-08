package com.caseforge.scanner.obd

/**
 * OBD-II mode / service identifiers (SAE J1979) and common PIDs for Mode 01 / 09.
 * Naming is generic OBD — not vehicle-specific.
 */
object ObdModes {
    const val MODE_SHOW_CURRENT_DATA: Int = 0x01
    const val MODE_SHOW_STORED_DTCS: Int = 0x03
    const val MODE_CLEAR_DTCS: Int = 0x04
    const val MODE_SHOW_PENDING_DTCS: Int = 0x07
    const val MODE_VEHICLE_INFO: Int = 0x09

    /** Mode 01 — live data PIDs (subset). */
    object Pid01 {
        const val COOLANT_TEMP: Int = 0x05
        const val ENGINE_RPM: Int = 0x0C
        const val VEHICLE_SPEED: Int = 0x0D
    }

    /** Mode 09 — vehicle information PIDs (subset). */
    object Pid09 {
        const val VIN_MESSAGE_COUNT: Int = 0x01
        const val VIN: Int = 0x02
    }

    fun positiveResponse(mode: Int): Int = mode + 0x40
}
