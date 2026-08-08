package com.caseforge.scanner.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.caseforge.scanner.data.session.CustomerSessionDao
import com.caseforge.scanner.data.session.CustomerSessionEntity
import com.caseforge.scanner.evidence.Evidence

@Database(
    entities = [
        SessionEntity::class,
        DtcEntity::class,
        ActionEntity::class,
        CustomerEntity::class,
        RepairOrderEntity::class,
        Evidence::class,
        CustomerSessionEntity::class,
    ],
    version = 10,
    exportSchema = false,
)
@TypeConverters(EvidenceConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun evidenceDao(): EvidenceDao
    abstract fun customerSessionDao(): CustomerSessionDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null
        fun get(context: Context): AppDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(
                context.applicationContext, AppDatabase::class.java, "tcw.db"
            ).fallbackToDestructiveMigration().build().also { INSTANCE = it }
        }
    }
}
