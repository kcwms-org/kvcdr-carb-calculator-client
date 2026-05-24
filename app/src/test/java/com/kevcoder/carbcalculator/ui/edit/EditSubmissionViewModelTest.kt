package com.kevcoder.carbcalculator.ui.edit

import com.kevcoder.carbcalculator.data.remote.carbapi.CarbApiCapture
import com.kevcoder.carbcalculator.data.repository.AnalysisResultCache
import com.kevcoder.carbcalculator.data.repository.CarbRepository
import com.kevcoder.carbcalculator.data.repository.EditLogCache
import com.kevcoder.carbcalculator.data.repository.SettingsRepository
import com.kevcoder.carbcalculator.data.repository.SubmissionLogRepository
import com.kevcoder.carbcalculator.domain.model.AnalysisResult
import com.kevcoder.carbcalculator.domain.model.CarbLog
import com.kevcoder.carbcalculator.domain.model.FoodItem
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EditSubmissionViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var editLogCache: EditLogCache
    private lateinit var resultCache: AnalysisResultCache
    private lateinit var carbRepository: CarbRepository
    private lateinit var submissionLogRepository: SubmissionLogRepository
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var carbApiCapture: CarbApiCapture
    private lateinit var viewModel: EditSubmissionViewModel

    private val fakeLog = CarbLog(
        id = 42L,
        timestamp = 1000L,
        foodDescription = "Pasta dinner",
        items = listOf(FoodItem("Pasta", 60f), FoodItem("Sauce", 10f)),
        totalCarbs = 70f,
        thumbnailPath = "/tmp/thumb.jpg",
        glucose = null,
        imageData = null,
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        editLogCache = mockk(relaxed = true)
        resultCache = mockk(relaxed = true)
        carbRepository = mockk()
        submissionLogRepository = mockk(relaxed = true)
        settingsRepository = mockk()
        carbApiCapture = mockk(relaxed = true)

        every { editLogCache.get() } returns fakeLog
        every { editLogCache.getOriginalLogId() } returns 42L
        every { settingsRepository.getImageQuality() } returns flowOf(80)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = EditSubmissionViewModel(
        editLogCache, resultCache, carbRepository,
        submissionLogRepository, settingsRepository, carbApiCapture,
    )

    @Test
    fun `init loads description and items from cache`() = runTest {
        viewModel = createViewModel()
        val state = viewModel.uiState.value
        assertEquals("Pasta dinner", state.foodDescription)
        assertEquals(2, state.items.size)
        assertEquals("Pasta", state.items[0].name)
        assertEquals("60.0", state.items[0].estimatedCarbs)
    }

    @Test
    fun `onSaveEdits puts AnalysisResult in resultCache and clears editLogCache`() = runTest {
        viewModel = createViewModel()
        coEvery { carbRepository.deleteLog(42L) } just Runs

        var successCalled = false
        viewModel.onSaveEdits { successCalled = true }
        advanceUntilIdle()

        verify { resultCache.put(any(), null, null, null) }
        verify { editLogCache.clear() }
        assertTrue(successCalled)
    }

    @Test
    fun `onSaveEdits deletes original log when originalLogId is non-null`() = runTest {
        viewModel = createViewModel()
        coEvery { carbRepository.deleteLog(42L) } just Runs

        viewModel.onSaveEdits {}
        advanceUntilIdle()

        coVerify { carbRepository.deleteLog(42L) }
    }

    @Test
    fun `onSaveEdits skips deleteLog when originalLogId is null`() = runTest {
        every { editLogCache.getOriginalLogId() } returns null
        viewModel = createViewModel()

        viewModel.onSaveEdits {}
        advanceUntilIdle()

        coVerify(exactly = 0) { carbRepository.deleteLog(any()) }
    }

    @Test
    fun `onSaveEdits sets error when item name is blank`() = runTest {
        viewModel = createViewModel()
        viewModel.onItemNameChange(viewModel.uiState.value.items[0].id, "")

        viewModel.onSaveEdits {}
        advanceUntilIdle()

        assertNotNull(viewModel.uiState.value.error)
        verify(exactly = 0) { resultCache.put(any(), any(), any(), any()) }
    }

    @Test
    fun `onSaveEdits sets error when carbs are zero`() = runTest {
        viewModel = createViewModel()
        viewModel.onItemCarbsChange(viewModel.uiState.value.items[0].id, "0")

        viewModel.onSaveEdits {}
        advanceUntilIdle()

        assertNotNull(viewModel.uiState.value.error)
    }

    @Test
    fun `onResubmit calls analyzeFood and puts result in resultCache`() = runTest {
        val fakeAnalysis = CarbRepository.AnalyzeFoodResult(
            analysisResult = AnalysisResult(
                items = listOf(FoodItem("Pasta", 55f)),
                totalCarbs = 55f,
                foodDescription = "Pasta dinner",
                imagePath = null,
            ),
            requestHeaders = null,
            responseHeaders = null,
            responseBody = null,
        )
        coEvery { carbRepository.analyzeFood(any(), any(), any(), any()) } returns fakeAnalysis
        coEvery { carbRepository.deleteLog(42L) } just Runs

        viewModel = createViewModel()

        var successCalled = false
        viewModel.onResubmit { successCalled = true }
        advanceUntilIdle()

        coVerify { carbRepository.analyzeFood(any(), any(), 80, any()) }
        verify { resultCache.put(fakeAnalysis.analysisResult, null, null, null) }
        verify { editLogCache.clear() }
        assertTrue(successCalled)
    }

    @Test
    fun `onResubmit deletes original log when originalLogId is non-null`() = runTest {
        val fakeAnalysis = CarbRepository.AnalyzeFoodResult(
            analysisResult = AnalysisResult(listOf(), 0f, null, null),
            requestHeaders = null, responseHeaders = null, responseBody = null,
        )
        coEvery { carbRepository.analyzeFood(any(), any(), any(), any()) } returns fakeAnalysis
        coEvery { carbRepository.deleteLog(42L) } just Runs

        viewModel = createViewModel()
        viewModel.onResubmit {}
        advanceUntilIdle()

        coVerify { carbRepository.deleteLog(42L) }
    }

    @Test
    fun `onResubmit skips deleteLog when originalLogId is null`() = runTest {
        every { editLogCache.getOriginalLogId() } returns null
        val fakeAnalysis = CarbRepository.AnalyzeFoodResult(
            analysisResult = AnalysisResult(listOf(), 0f, null, null),
            requestHeaders = null, responseHeaders = null, responseBody = null,
        )
        coEvery { carbRepository.analyzeFood(any(), any(), any(), any()) } returns fakeAnalysis

        viewModel = createViewModel()
        viewModel.onResubmit {}
        advanceUntilIdle()

        coVerify(exactly = 0) { carbRepository.deleteLog(any()) }
    }

    @Test
    fun `onResubmit sets error state on API failure`() = runTest {
        coEvery { carbRepository.analyzeFood(any(), any(), any(), any()) } throws RuntimeException("Network error")

        viewModel = createViewModel()
        viewModel.onResubmit {}
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isSubmitting)
        assertEquals("Network error", state.error)
        verify { submissionLogRepository.logRequest(carbLogId = null, any(), any(), any(), "error", any(), any(), any(), any(), any(), any(), any()) }
    }
}
