package com.kevcoder.carbcalculator.ui.capture

import com.kevcoder.carbcalculator.data.repository.AnalysisResultCache
import com.kevcoder.carbcalculator.data.repository.CarbRepository
import com.kevcoder.carbcalculator.data.repository.SubmissionLogRepository
import com.kevcoder.carbcalculator.domain.model.AnalysisResult
import com.kevcoder.carbcalculator.domain.model.FoodItem
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
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
    private lateinit var submissionLogRepository: SubmissionLogRepository
    private lateinit var resultCache: AnalysisResultCache
    private lateinit var viewModel: CaptureViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        carbRepository = mockk()
        submissionLogRepository = mockk(relaxed = true)
        resultCache = mockk(relaxed = true)
        viewModel = CaptureViewModel(carbRepository, submissionLogRepository, resultCache)
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
        val fakeResult = AnalysisResult(
            items = listOf(FoodItem("Apple", 25f)),
            totalCarbs = 25f,
            foodDescription = "An apple",
            imagePath = file.absolutePath,
        )
        coEvery { carbRepository.analyzeFood(any(), any()) } returns fakeResult

        var successCalled = false
        viewModel.onAnalyze(file, "An apple") { successCalled = true }
        advanceUntilIdle()

        coVerify { resultCache.put(fakeResult) }
        assertTrue(successCalled)
    }

    @Test
    fun `onAnalyze transitions to Error when repository throws`() = runTest {
        val file = File("/tmp/photo.jpg")
        coEvery { carbRepository.analyzeFood(any(), any()) } throws RuntimeException("Network error")

        viewModel.onAnalyze(file, null) {}
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is CaptureUiState.Error)
        assertEquals("Network error", (state as CaptureUiState.Error).message)
    }

    @Test
    fun `resetState returns to Idle`() {
        viewModel.onPhotoCaptured(File("/tmp/photo.jpg"))
        viewModel.resetState()
        assertEquals(CaptureUiState.Idle, viewModel.uiState.value)
    }
}
