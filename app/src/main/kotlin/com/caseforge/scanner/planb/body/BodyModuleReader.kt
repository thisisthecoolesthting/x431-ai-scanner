package com.caseforge.scanner.planb.body

/**
 * Tier 1 — read-only access to body / convenience-module data (implementation-specific ECU id).
 * Jeep-neutral contract: callers supply [ecuId] and DID identifiers; transport is injected elsewhere.
 */
interface BodyModuleReader {
    fun readDtcs(ecuId: String): Result<List<BodyDtc>>

    fun readLiveData(ecuId: String, dids: List<String>): Result<List<BodyLiveDatum>>
}

/**
 * Single diagnostic trouble code row from a body-module read.
 */
data class BodyDtc(
    val code: String,
    val description: String = "",
)

/**
 * One decoded live-data point for a requested DID.
 */
data class BodyLiveDatum(
    val did: String,
    val value: String,
)
