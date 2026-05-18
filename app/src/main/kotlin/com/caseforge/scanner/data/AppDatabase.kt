package com.caseforge.scanner.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.caseforge.scanner.evidence.Evidence

@Database(
    entities = [
        SessionEntity::class,
        DtcEntity::class,
        ActionEntity::class,
        CustomerEntity::class,
        RepairOrderEntity::class,
        Evidence::class,
    ],
    version = 5,
    exportSchema = false,
)
@TypeConverters(EvidenceConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun evidenceDao(): EvidenceDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null
        fun get(context: Context): AppDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(
                context.applicationContext, AppDatabase::class.java, "caseforge.db"
            ).fallbackToDestructiveMigration().build().also { INSTANCE = it }
        }
    }
}
