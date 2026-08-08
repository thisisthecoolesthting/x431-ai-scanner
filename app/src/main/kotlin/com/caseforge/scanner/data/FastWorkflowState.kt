package com.caseforge.scanner.data

/**
 * Cached values from the last successful workflow pass — surfaced on cold start for perceived speed.
 * Persisted via [SettingsRepo]; UI wiring is handled in later dispatches.
 */
data class FastWorkflowState(
    val lastVin: String? = null,
    val lastTransportLabel: String? = null,
    val lastBatteryVoltage: Float? = null,
    val lastReceiverHost: String? = null,
    val lastSuccessfulScanAt: Long = 0L,
    val lastGoodBtAddress: String? = null,
    val lastGoodTransport: String? = null,
) {
    /** True when any meaningful cache entry exists. */
    val hasAnyMemory: Boolean
        get() = !lastVin.isNullOrBlank()
            || !lastTransportLabel.isNullOrBlank()
            || lastBatteryVoltage != null
            || !lastReceiverHost.isNullOrBlank()
            || lastSuccessfulScanAt > 0L
            || !lastGoodBtAddress.isNullOrBlank()
            || !lastGoodTransport.isNullOrBlank()
}
