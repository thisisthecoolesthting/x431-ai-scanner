package com.caseforge.scanner.evidence

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "evidence")
data class Evidence(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val ticketId: String,
    val timestamp: Long = System.currentTimeMillis(),
    val type: EvidenceType,
    val label: String,
    val snapshotJson: String? = null,
    val photoUri: String? = null,
)

enum class EvidenceType { BEFORE, FIX, AFTER }
