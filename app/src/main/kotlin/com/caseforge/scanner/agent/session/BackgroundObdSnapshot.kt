package com.caseforge.scanner.agent.session

import kotlinx.serialization.Serializable

/**
 * Silent OBD snapshot polled during session chat — DTC summary + Mode 01 live PIDs when linked.
 * Stub fields (protocol, ECU addr) are populated when USB ELM327 is connected; otherwise disconnected.
 */
@Serializable
data class BackgroundObdSnapshot(
    val connected: Boolean = false,
    val linkStatus: String = "No OBD cable detected",
    val protocol: String? = null,
    val ecuAddress: String? = null,
    val dtcSummary: String? = null,
    val storedDtcCount: Int = 0,
    val pendingDtcCount: Int = 0,
    val monitorsReady: String? = null,
    val rpm: Float? = null,
    val coolantC: Float? = null,
    val voltage: Float? = null,
    val rpmHistory: List<Float> = emptyList(),
    val sampledAtMs: Long = System.currentTimeMillis(),
)
