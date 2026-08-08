package com.caseforge.scanner.data.session

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Per-VIN customer session rollup for the New Session wizard + chat flow.
 * Individual visit artifacts live under `filesDir/sessions/<sessionId>/`.
 */
@Entity(
    tableName = "customer_sessions",
    indices = [
        Index(value = ["updatedAt"]),
    ],
)
data class CustomerSessionEntity(
    @PrimaryKey val vin: String,
    val lastSessionId: String,
    /** JSON blob: prior visit summaries, photo metadata, chat excerpt. */
    val summaryJson: String = "{}",
    val engineBayPhotoPath: String? = null,
    val doorJambPhotoPath: String? = null,
    val dashboardPhotoPath: String? = null,
    val lastNeedDescription: String? = null,
    val lastDtcSummary: String? = null,
    /** JSON [BackgroundObdSnapshot] from last live poll during session chat. */
    val lastObdSnapshotJson: String? = null,
    /** JSON [DiagnosticPhotoInsights] from Claude vision on wizard photos. */
    val photoDiagnosticJson: String? = null,
    /** JSON [SessionTokenAccounting.AiUsageSnapshot] from the last ended session for this VIN. */
    val lastAiUsageJson: String? = null,
    val updatedAt: Long = System.currentTimeMillis(),
)
