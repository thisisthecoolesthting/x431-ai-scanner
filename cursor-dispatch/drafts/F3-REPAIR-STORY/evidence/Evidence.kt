package com.caseforge.scanner.evidence

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Persisted evidence snapshot attached to a repair ticket.
 *
 * Created either by bookmarking live engine state (EvidenceCapture.bookmarkLiveData)
 * or by attaching a photo taken during the repair (EvidenceCapture.attachPhoto).
 *
 * Room entity — insert/query via AppDatabase.evidenceDao().
 */
@Entity(tableName = "evidence")
data class Evidence(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),

    /** Links this snapshot to a repair ticket (ticketId from the ticket table). */
    val ticketId: String,

    val timestamp: Long = System.currentTimeMillis(),

    /** Categorises when in the workflow this evidence was captured. */
    val type: EvidenceType,

    /** Short human label, e.g. "Before scan", "Replaced coil pack", "Post-repair idle". */
    val label: String,

    /**
     * JSON blob produced by EvidenceCapture.bookmarkLiveData().
     * Contains dtcs, livePids, and screenKind at capture time.
     * Null when the evidence is photo-only.
     */
    val snapshotJson: String? = null,

    /**
     * content:// URI string of an engine-bay photo attached via EvidenceCapture.attachPhoto().
     * Null when this is a data-only bookmark.
     */
    val photoUri: String? = null,
)

enum class EvidenceType {
    /** Diagnostic state recorded before any repair work begins. */
    BEFORE,

    /** Mid-repair checkpoint — parts replaced, work in progress. */
    FIX,

    /** Post-repair state confirming codes cleared and data normalised. */
    AFTER,
}

// -------------------------------------------------------------------------
// Room DAO — place in db/EvidenceDao.kt (shown here for colocation clarity)
// -------------------------------------------------------------------------

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface EvidenceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(evidence: Evidence)

    @Query("SELECT * FROM evidence WHERE ticketId = :ticketId ORDER BY timestamp ASC")
    fun getByTicket(ticketId: String): List<Evidence>

    @Query("SELECT * FROM evidence WHERE id = :id LIMIT 1")
    fun getById(id: String): Evidence?

    @Query("UPDATE evidence SET photoUri = :uri WHERE id = :id")
    fun updatePhotoUri(id: String, uri: String)

    @Query("SELECT * FROM evidence WHERE ticketId = :ticketId AND type = :type ORDER BY timestamp ASC")
    fun getByTicketAndType(ticketId: String, type: EvidenceType): List<Evidence>
}

// -------------------------------------------------------------------------
// AppDatabase extension — add to your existing AppDatabase.kt:
//
//   @Database(entities = [..., Evidence::class], version = N)
//   abstract class AppDatabase : RoomDatabase() {
//       abstract fun evidenceDao(): EvidenceDao
//   }
// -------------------------------------------------------------------------
