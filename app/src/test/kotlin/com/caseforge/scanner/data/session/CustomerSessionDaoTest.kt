package com.caseforge.scanner.data.session

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.caseforge.scanner.data.AppDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CustomerSessionDaoTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val db: AppDatabase = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
        .allowMainThreadQueries()
        .build()

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun loadByVin_isCaseInsensitiveQuery() = runBlocking {
        val dao = db.customerSessionDao()
        val vin = "1HGBH41JXMN109186"
        dao.upsert(
            CustomerSessionEntity(
                vin = vin,
                lastSessionId = "sess-1",
                lastNeedDescription = "prior need",
            ),
        )
        val lower = dao.loadByVin(vin.lowercase())
        assertEquals("sess-1", lower?.lastSessionId)
        assertEquals("prior need", lower?.lastNeedDescription)
    }

    @Test
    fun upsert_replacesRow() = runBlocking {
        val dao = db.customerSessionDao()
        val vin = "2HGBH41JXMN109187"
        dao.upsert(CustomerSessionEntity(vin = vin, lastSessionId = "a"))
        dao.upsert(CustomerSessionEntity(vin = vin, lastSessionId = "b"))
        assertEquals("b", dao.loadByVin(vin)?.lastSessionId)
    }

    @Test
    fun loadByVin_missingReturnsNull() = runBlocking {
        assertNull(db.customerSessionDao().loadByVin("ZZZZZZZZZZZZZZZZZ"))
    }
}
