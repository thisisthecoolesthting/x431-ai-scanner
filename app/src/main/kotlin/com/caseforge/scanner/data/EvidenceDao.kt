package com.caseforge.scanner.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.caseforge.scanner.evidence.Evidence
import com.caseforge.scanner.evidence.EvidenceType

@Dao
interface EvidenceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(evidence: Evidence)

    @Query("SELECT * FROM evidence WHERE ticketId = :ticketId ORDER BY timestamp ASC")
    fun getByTicket(ticketId: String): List<Evidence>

    @Query("UPDATE evidence SET photoUri = :uri WHERE id = :id")
    fun updatePhotoUri(id: String, uri: String)
}
