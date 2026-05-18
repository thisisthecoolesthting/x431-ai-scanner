package com.caseforge.scanner.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val vin: String?,
    val startedAt: Long,
    val endedAt: Long?,
    val symptom: String?,
    val rootCause: String?,
    val recommendedRepair: String?,
    val transcriptJson: String,
    /**
     * Distinguishes the kind of session: "diagnostic" (the default, symptom-driven flow)
     * or "fullscan" (one-tap Full Scan of every module). Used by FullScanResultsScreen
     * to pull the most recent full-scan run.
     */
    val scope: String = "diagnostic",
    val latitude: Double? = null,
    val longitude: Double? = null,
)

@Entity(tableName = "dtcs")
data class DtcEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val code: String,
    val module: String?,
    val description: String?,
    val status: String?,
)

@Entity(tableName = "actions")
data class ActionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val at: Long,
    val tool: String,
    val args: String,
    val outcome: String,
)


@Entity(tableName = "customers")
data class CustomerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val phone: String? = null,
    val email: String? = null,
    val notes: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "repair_orders")
data class RepairOrderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val customerId: Long? = null,
    val vin: String? = null,
    val vehicleSummary: String? = null,
    val symptom: String? = null,
    val status: String = "open",           // open | in_progress | completed | invoiced
    val laborHours: Double = 0.0,
    val partsTotal: Double = 0.0,
    val laborRate: Double = 0.0,
    val createdAt: Long = System.currentTimeMillis(),
    val closedAt: Long? = null,
    val preScanSessionId: Long? = null,
    val postScanSessionId: Long? = null,
    val invoiceText: String? = null,
)
