package com.caseforge.scanner.data.session

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface CustomerSessionDao {
    @Query("SELECT * FROM customer_sessions WHERE UPPER(vin) = UPPER(:vin) LIMIT 1")
    suspend fun loadByVin(vin: String): CustomerSessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: CustomerSessionEntity)

    @Query("SELECT * FROM customer_sessions ORDER BY updatedAt DESC")
    suspend fun listAll(): List<CustomerSessionEntity>
}
