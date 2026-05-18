package com.caseforge.scanner.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface SessionDao {
    @Insert
    suspend fun insertSession(session: SessionEntity): Long

    @Insert
    suspend fun insertDtc(dtc: DtcEntity): Long

    @Insert
    suspend fun insertAction(action: ActionEntity): Long

    @Query("SELECT * FROM sessions ORDER BY startedAt DESC")
    suspend fun listAll(): List<SessionEntity>

    @Query("SELECT * FROM sessions WHERE vin = :vin ORDER BY startedAt DESC")
    suspend fun listByVin(vin: String): List<SessionEntity>

    @Query("SELECT * FROM sessions WHERE scope = :scope ORDER BY startedAt DESC LIMIT 1")
    suspend fun latestByScope(scope: String): SessionEntity?

    @Query("SELECT * FROM dtcs WHERE sessionId = :sessionId")
    suspend fun dtcsFor(sessionId: Long): List<DtcEntity>

    @Query("SELECT * FROM actions WHERE sessionId = :sessionId ORDER BY at ASC")
    suspend fun actionsFor(sessionId: Long): List<ActionEntity>

    @Query("DELETE FROM sessions WHERE id = :id")
    suspend fun deleteSession(id: Long)
}
