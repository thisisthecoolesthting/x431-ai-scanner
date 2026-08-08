package com.caseforge.scanner.ui.tuning

import com.caseforge.scanner.vci.VciCommunicator

fun livePidLabel(pid: Int): String = when (pid) {
    VciCommunicator.PID_ENGINE_COOLANT_TEMP -> "Coolant temp"
    VciCommunicator.PID_ENGINE_RPM -> "Engine RPM"
    VciCommunicator.PID_VEHICLE_SPEED -> "Vehicle speed"
    VciCommunicator.PID_MAF_RATE -> "MAF"
    VciCommunicator.PID_THROTTLE_POSITION -> "Throttle"
    VciCommunicator.PID_O2_VOLTAGE_B1S1 -> "O2 B1S1"
    VciCommunicator.PID_CONTROL_MODULE_V -> "Module voltage"
    else -> "PID 0x${pid.toString(16).uppercase().padStart(2, '0')}"
}

fun livePidHex(pid: Int): String =
    pid.toString(16).uppercase().padStart(2, '0')
