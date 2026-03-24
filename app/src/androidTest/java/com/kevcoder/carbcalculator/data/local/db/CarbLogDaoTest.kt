package com.kevcoder.carbcalculator.data.local.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CarbLogDaoTest {

    private lateinit var db: CarbCalculatorDatabase
    private lateinit var dao: CarbLogDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            CarbCalculatorDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = db.carbLogDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun entity(
        foodDescription: String? = "Apple",
        totalCarbs: Float = 25f,
        glucoseMgDl: Int? = null,
    ) = CarbLogEntity(
        timestamp = System.currentTimeMillis(),
        foodDescription = foodDescription,
        foodItemsJson = """[{"name":"Apple","estimatedCarbs":25.0}]""",
        totalCarbs = totalCarbs,
        thumbnailPath = null,
        glucoseMgDl = glucoseMgDl,
        glucoseTimestamp = if (glucoseMgDl != null) System.currentTimeMillis() else null,
    )

    @Test
    fun insertAndRetrieveById() = runTest {
        val id = dao.insert(entity())
        val retrieved = dao.getLogById(id)
        assertNotNull(retrieved)
        assertEquals(id, retrieved!!.id)
        assertEquals("Apple", retrieved.foodDescription)
        assertEquals(25f, retrieved.totalCarbs)
    }

    @Test
    fun getAllLogsReturnsMostRecentFirst() = runTest {
        val id1 = dao.insert(entity(foodDescription = "First", totalCarbs = 10f))
        Thread.sleep(5) // ensure distinct timestamps
        val id2 = dao.insert(entity(foodDescription = "Second", totalCarbs = 20f))

        val logs = dao.getAllLogs().first()

        assertEquals(2, logs.size)
        // Most recent (higher timestamp) should be first
        assertEquals("Second", logs[0].foodDescription)
        assertEquals("First", logs[1].foodDescription)
    }

    @Test
    fun deleteLogRemovesEntry() = runTest {
        val id = dao.insert(entity())
        dao.deleteLog(id)
        assertNull(dao.getLogById(id))
    }

    @Test
    fun getAllLogsEmptyInitially() = runTest {
        val logs = dao.getAllLogs().first()
        assertTrue(logs.isEmpty())
    }

    @Test
    fun insertWithGlucosePersistsGlucoseFields() = runTest {
        val id = dao.insert(entity(glucoseMgDl = 120))
        val retrieved = dao.getLogById(id)
        assertNotNull(retrieved)
        assertEquals(120, retrieved!!.glucoseMgDl)
        assertNotNull(retrieved.glucoseTimestamp)
    }

    @Test
    fun insertWithNullGlucosePersistsNullFields() = runTest {
        val id = dao.insert(entity(glucoseMgDl = null))
        val retrieved = dao.getLogById(id)
        assertNotNull(retrieved)
        assertNull(retrieved!!.glucoseMgDl)
        assertNull(retrieved.glucoseTimestamp)
    }

    @Test
    fun insertMultipleAndDeleteOne() = runTest {
        val id1 = dao.insert(entity(foodDescription = "Pizza"))
        val id2 = dao.insert(entity(foodDescription = "Salad"))
        dao.deleteLog(id1)

        val logs = dao.getAllLogs().first()
        assertEquals(1, logs.size)
        assertEquals("Salad", logs[0].foodDescription)
    }

    @Test
    fun getLogByIdReturnsNullForMissingId() = runTest {
        assertNull(dao.getLogById(999L))
    }
}
