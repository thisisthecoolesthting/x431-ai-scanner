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

    // Customers
    @Insert
    suspend fun insertCustomer(c: CustomerEntity): Long
    @Query("SELECT * FROM customers ORDER BY name ASC")
    suspend fun listCustomers(): List<CustomerEntity>

    // Repair orders
    @Insert
    suspend fun insertRepairOrder(ro: RepairOrderEntity): Long
    @Query("UPDATE repair_orders SET status = :status, closedAt = :closedAt, invoiceText = :invoice WHERE id = :id")
    suspend fun closeRepairOrder(id: Long, status: String, closedAt: Long, invoice: String?)
    @Query("SELECT * FROM repair_orders ORDER BY createdAt DESC")
    suspend fun listRepairOrders(): List<RepairOrderEntity>
    @Query("SELECT * FROM repair_orders WHERE status != 'completed' AND status != 'invoiced' ORDER BY createdAt DESC")
    suspend fun listOpenRepairOrders(): List<RepairOrderEntity>
}
