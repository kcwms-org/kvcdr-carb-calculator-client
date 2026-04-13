package com.kevcoder.carbcalculator.ui.capture

import com.kevcoder.carbcalculator.data.local.datastore.AppPreferencesDataStore
import com.kevcoder.carbcalculator.data.repository.AnalysisResultCache
import com.kevcoder.carbcalculator.data.repository.CarbRepository
import com.kevcoder.carbcalculator.data.repository.SettingsRepository
import com.kevcoder.carbcalculator.domain.model.AnalysisResult
import com.kevcoder.carbcalculator.domain.model.FoodItem
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class CaptureViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var carbRepository: CarbRepository
    private lateinit var resultCache: AnalysisResultCache
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var submissionLogRepository: com.kevcoder.carbcalculator.data.repository.SubmissionLogRepository
    private lateinit var carbApiCapture: com.kevcoder.carbcalculator.data.remote.carbapi.CarbApiCapture
    private lateinit var viewModel: CaptureViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        carbRepository = mockk()
        resultCache = mockk(relaxed = true)
        settingsRepository = mockk()
        submissionLogRepository = mockk(relaxed = true)
        carbApiCapture = mockk(relaxed = true)
        every { settingsRepository.getImageQuality() } returns kotlinx.coroutines.flow.flowOf(AppPreferencesDataStore.DEFAULT_IMAGE_QUALITY)
        viewModel = CaptureViewModel(carbRepository, resultCache, settingsRepository, submissionLogRepository, carbApiCapture)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is Idle`() {
        assertEquals(CaptureUiState.Idle, viewModel.uiState.value)
    }

    @Test
    fun `onPhotoCaptured transitions to PhotoTaken with correct path`() {
        val file = File("/tmp/photo.jpg")
        viewModel.onPhotoCaptured(file)
        val state = viewModel.uiState.value
        assertTrue(state is CaptureUiState.PhotoTaken)
        assertEquals(file.absolutePath, (state as CaptureUiState.PhotoTaken).imagePath)
    }

    @Test
    fun `onCaptureFailed transitions to Error with message`() {
        viewModel.onCaptureFailed("Camera error")
        val state = viewModel.uiState.value
        assertTrue(state is CaptureUiState.Error)
        assertEquals("Camera error", (state as CaptureUiState.Error).message)
    }

    @Test
    fun `onAnalyze transitions to Uploading then calls onSuccess on result`() = runTest {
        val file = File("/tmp/photo.jpg")
        val fakeAnalysisResult = AnalysisResult(
            items = listOf(FoodItem("Apple", 25f)),
            totalCarbs = 25f,
            foodDescription = "An apple",
            imagePath = file.absolutePath,
        )
        val fakeResult = CarbRepository.AnalyzeFoodResult(
            analysisResult = fakeAnalysisResult,
            requestHeaders = null,
            responseHeaders = null,
            responseBody = null,
        )
        coEvery { carbRepository.analyzeFood(any(), any(), any(), any()) } returns fakeResult

        var successCalled = false
        viewModel.onAnalyze(file, "An apple") { successCalled = true }
        advanceUntilIdle()

        coVerify {
            resultCache.put(
                result = fakeAnalysisResult,
                requestHeaders = null,
                responseHeaders = null,
                responseBody = null,
            )
        }
        assertTrue(successCalled)
        assertEquals(CaptureUiState.Idle, viewModel.uiState.value)
    }

    @Test
    fun `onAnalyze transitions to Error when repository throws`() = runTest {
        val file = File("/tmp/photo.jpg")
        coEvery { carbRepository.analyzeFood(any(), any(), any(), any()) } throws RuntimeException("Network error")

        viewModel.onAnalyze(file, null) {}
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is CaptureUiState.Error)
        assertEquals("Network error", (state as CaptureUiState.Error).message)
    }

    @Test
    fun `onAnalyze with no image and description calls repository with null file`() = runTest {
        val fakeAnalysisResult = AnalysisResult(
            items = listOf(FoodItem("Rice", 45f)),
            totalCarbs = 45f,
            foodDescription = "A bowl of rice",
            imagePath = null,
        )
        val fakeResult = CarbRepository.AnalyzeFoodResult(
            analysisResult = fakeAnalysisResult,
            requestHeaders = null,
            responseHeaders = null,
            responseBody = null,
        )
        coEvery { carbRepository.analyzeFood(null, any(), any(), any()) } returns fakeResult

        var successCalled = false
        viewModel.onAnalyze(null, "A bowl of rice") { successCalled = true }
        advanceUntilIdle()

        coVerify { carbRepository.analyzeFood(null, "A bowl of rice", any(), any()) }
        assertTrue(successCalled)
    }

    @Test
    fun `resetState returns to Idle`() {
        viewModel.onPhotoCaptured(File("/tmp/photo.jpg"))
        viewModel.resetState()
        assertEquals(CaptureUiState.Idle, viewModel.uiState.value)
    }
}
