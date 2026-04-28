package com.kevcoder.carbcalculator.data.repository

import com.kevcoder.carbcalculator.data.local.db.CarbLogDao
import com.kevcoder.carbcalculator.data.local.db.CarbLogEntity
import com.kevcoder.carbcalculator.data.remote.carbapi.AnalysisResponse
import com.kevcoder.carbcalculator.data.remote.carbapi.CarbApiCapture
import com.kevcoder.carbcalculator.data.remote.carbapi.CarbApiService
import com.kevcoder.carbcalculator.data.remote.carbapi.FoodItemResponse
import com.kevcoder.carbcalculator.domain.model.AnalysisResult
import com.kevcoder.carbcalculator.domain.model.FoodItem
import com.kevcoder.carbcalculator.domain.model.GlucoseReading
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import io.mockk.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class CarbRepositoryTest {

    private lateinit var carbApiService: CarbApiService
    private lateinit var carbApiCapture: CarbApiCapture
    private lateinit var carbLogDao: CarbLogDao
    private lateinit var submissionLogRepository: SubmissionLogRepository
    private lateinit var context: android.content.Context
    private lateinit var moshi: Moshi
    private lateinit var applicationScope: CoroutineScope
    private lateinit var repository: CarbRepository

    @Before
    fun setUp() {
        carbApiService = mockk()
        carbApiCapture = CarbApiCapture()
        carbLogDao = mockk()
        submissionLogRepository = mockk(relaxed = true)
        context = mockk()
        moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
        applicationScope = CoroutineScope(UnconfinedTestDispatcher())

        every { context.filesDir } returns File(System.getProperty("java.io.tmpdir")!!)
        repository = CarbRepository(context, carbApiService, carbApiCapture, carbLogDao, submissionLogRepository, moshi, applicationScope)
    }

    @Test
    fun `analyzeFood with image maps API response to domain AnalysisResult`() = runTest {
        val tempFile = File.createTempFile("test_photo", ".jpg")
        try {
            val apiResponse = AnalysisResponse(
                items = listOf(FoodItemResponse("Apple", 25f), FoodItemResponse("Banana", 27f)),
                totalCarbsGrams = 52f,
            )
            coEvery { carbApiService.analyze(any(), any(), any()) } returns apiResponse

            val result = repository.analyzeFood(tempFile, "Fruit bowl")

            assertEquals(2, result.analysisResult.items.size)
            assertEquals("Apple", result.analysisResult.items[0].name)
            assertEquals(25f, result.analysisResult.items[0].estimatedCarbs)
            assertEquals(52f, result.analysisResult.totalCarbs)
            assertEquals("Fruit bowl", result.analysisResult.foodDescription)
            assertEquals(tempFile.absolutePath, result.analysisResult.imagePath)
            coVerify { carbApiService.analyze(any(), any(), any()) }
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun `analyzeFood throws when API analyze fails`() = runTest {
        val tempFile = File.createTempFile("test_photo", ".jpg")
        try {
            coEvery { carbApiService.analyze(any(), any(), any()) } throws RuntimeException("API error")

            assertThrows(RuntimeException::class.java) {
                kotlinx.coroutines.runBlocking { repository.analyzeFood(tempFile, null) }
            }
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun `analyzeFood with null image skips multipart image`() = runTest {
        coEvery { carbApiService.analyze(null, any(), any()) } returns AnalysisResponse(
            items = listOf(FoodItemResponse("Pasta", 60f)),
            totalCarbsGrams = 60f,
        )

        val result = repository.analyzeFood(null, "A bowl of pasta")

        coVerify { carbApiService.analyze(null, any(), any()) }
        assertEquals(60f, result.analysisResult.totalCarbs)
        assertNull(result.analysisResult.imagePath)
    }

    @Test
    fun `saveLog inserts entity into DAO and returns generated ID`() = runTest {
        val result = AnalysisResult(
            items = listOf(FoodItem("Rice", 45f)),
            totalCarbs = 45f,
            foodDescription = "Bowl of rice",
            imagePath = null,
        )
        val glucose = GlucoseReading(mgDl = 110, timestamp = 5000L)
        coEvery { carbLogDao.insert(any()) } returns 42L

        val id = repository.saveLog(result, glucose)

        assertEquals(42L, id)
        val slot = slot<CarbLogEntity>()
        coVerify { carbLogDao.insert(capture(slot)) }
        assertEquals(45f, slot.captured.totalCarbs)
        assertEquals("Bowl of rice", slot.captured.foodDescription)
        assertEquals(110, slot.captured.glucoseMgDl)
        assertEquals(5000L, slot.captured.glucoseTimestamp)
    }

    @Test
    fun `saveLog stores null glucose fields when no reading provided`() = runTest {
        val result = AnalysisResult(
            items = emptyList(),
            totalCarbs = 0f,
            foodDescription = null,
            imagePath = null,
        )
        coEvery { carbLogDao.insert(any()) } returns 1L

        repository.saveLog(result, glucose = null)

        val slot = slot<CarbLogEntity>()
        coVerify { carbLogDao.insert(capture(slot)) }
        assertNull(slot.captured.glucoseMgDl)
        assertNull(slot.captured.glucoseTimestamp)
    }

    @Test
    fun `getLogs maps entities to domain CarbLog`() = runTest {
        val entity = CarbLogEntity(
            id = 1L,
            timestamp = 1000L,
            foodDescription = "Pasta",
            foodItemsJson = """[{"name":"Spaghetti","estimatedCarbs":55.0}]""",
            totalCarbs = 55f,
            thumbnailPath = null,
            glucoseMgDl = 130,
            glucoseTimestamp = 2000L,
        )
        every { carbLogDao.getAllLogs() } returns flowOf(listOf(entity))

        val logs = repository.getLogs().first()

        assertEquals(1, logs.size)
        val log = logs[0]
        assertEquals(1L, log.id)
        assertEquals("Pasta", log.foodDescription)
        assertEquals(55f, log.totalCarbs)
        assertEquals(1, log.items.size)
        assertEquals("Spaghetti", log.items[0].name)
        assertNotNull(log.glucose)
        assertEquals(130, log.glucose?.mgDl)
    }

    @Test
    fun `getLogs returns empty items list when JSON is malformed`() = runTest {
        val entity = CarbLogEntity(
            id = 1L,
            timestamp = 1000L,
            foodDescription = null,
            foodItemsJson = "INVALID_JSON",
            totalCarbs = 10f,
            thumbnailPath = null,
            glucoseMgDl = null,
            glucoseTimestamp = null,
        )
        every { carbLogDao.getAllLogs() } returns flowOf(listOf(entity))

        val logs = repository.getLogs().first()

        assertEquals(1, logs.size)
        assertTrue(logs[0].items.isEmpty())
    }
}
