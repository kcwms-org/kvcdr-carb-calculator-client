package com.kevcoder.carbcalculator.ui.result

import com.kevcoder.carbcalculator.data.repository.AnalysisResultCache
import com.kevcoder.carbcalculator.data.repository.CarbRepository
import com.kevcoder.carbcalculator.data.repository.DexcomRepository
import com.kevcoder.carbcalculator.data.repository.SettingsRepository
import com.kevcoder.carbcalculator.data.repository.SubmissionLogRepository
import com.kevcoder.carbcalculator.domain.model.AnalysisResult
import com.kevcoder.carbcalculator.domain.model.FoodItem
import com.kevcoder.carbcalculator.domain.model.GlucoseReading
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ResultViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var resultCache: AnalysisResultCache
    private lateinit var carbRepository: CarbRepository
    private lateinit var submissionLogRepository: SubmissionLogRepository
    private lateinit var dexcomRepository: DexcomRepository
    private lateinit var settingsRepository: SettingsRepository
    private val moshi: Moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private lateinit var viewModel: ResultViewModel

    private val fakeResult = AnalysisResult(
        items = listOf(FoodItem("Banana", 27f)),
        totalCarbs = 27f,
        foodDescription = "A banana",
        imagePath = "/tmp/photo.jpg",
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        resultCache = mockk()
        carbRepository = mockk()
        submissionLogRepository = mockk(relaxed = true)
        dexcomRepository = mockk()
        settingsRepository = mockk()
        every { resultCache.get() } returns fakeResult
        every { resultCache.getRequestHeaders() } returns null
        every { resultCache.getResponseHeaders() } returns null
        every { resultCache.getResponseBody() } returns null
        coEvery { dexcomRepository.getLatestGlucose() } returns null
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = ResultViewModel(resultCache, carbRepository, submissionLogRepository, dexcomRepository, settingsRepository, moshi)

    @Test
    fun `init loads result from cache`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()
        assertEquals(fakeResult, viewModel.uiState.value.result)
    }

    @Test
    fun `init fetches glucose and populates state when available`() = runTest {
        val glucose = GlucoseReading(mgDl = 120, timestamp = 1000L)
        coEvery { dexcomRepository.getLatestGlucose() } returns glucose
        viewModel = createViewModel()
        advanceUntilIdle()
        assertEquals(glucose, viewModel.uiState.value.glucose)
    }

    @Test
    fun `init sets glucose to null when Dexcom not connected`() = runTest {
        coEvery { dexcomRepository.getLatestGlucose() } returns null
        viewModel = createViewModel()
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.glucose)
    }

    @Test
    fun `onSave calls repository and clears cache on success`() = runTest {
        coEvery { carbRepository.saveLog(any(), any()) } returns 1L
        every { resultCache.clear() } just Runs
        viewModel = createViewModel()
        advanceUntilIdle()

        var successCalled = false
        viewModel.onSave { successCalled = true }
        advanceUntilIdle()

        coVerify { carbRepository.saveLog(fakeResult, null) }
        verify { resultCache.clear() }
        assertTrue(successCalled)
        assertTrue(viewModel.uiState.value.saved)
    }

    @Test
    fun `onSave sets error state when repository throws`() = runTest {
        coEvery { carbRepository.saveLog(any(), any()) } throws RuntimeException("DB error")
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onSave {}
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isSaving)
        assertEquals("DB error", state.error)
    }

    @Test
    fun `onDiscard clears cache and invokes callback`() = runTest {
        every { resultCache.clear() } just Runs
        viewModel = createViewModel()
        advanceUntilIdle()

        var discardCalled = false
        viewModel.onDiscard { discardCalled = true }

        verify { resultCache.clear() }
        assertTrue(discardCalled)
    }
}
