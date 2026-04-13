package com.kevcoder.carbcalculator.ui.history

import com.kevcoder.carbcalculator.data.repository.CarbRepository
import com.kevcoder.carbcalculator.data.repository.SettingsRepository
import com.kevcoder.carbcalculator.data.repository.SubmissionLogRepository
import com.kevcoder.carbcalculator.domain.model.CarbLog
import com.kevcoder.carbcalculator.domain.model.SubmissionLog
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.Runs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var carbRepository: CarbRepository
    private lateinit var submissionLogRepository: SubmissionLogRepository
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var viewModel: HistoryViewModel

    private val fakeLogs = listOf(
        CarbLog(
            id = 1L,
            timestamp = 1000L,
            foodDescription = "Pizza",
            items = emptyList(),
            totalCarbs = 60f,
            thumbnailPath = null,
            glucose = null,
        ),
        CarbLog(
            id = 2L,
            timestamp = 2000L,
            foodDescription = "Salad",
            items = emptyList(),
            totalCarbs = 10f,
            thumbnailPath = null,
            glucose = null,
        ),
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        carbRepository = mockk()
        submissionLogRepository = mockk()
        settingsRepository = mockk(relaxed = true)
        every { carbRepository.getLogs() } returns flowOf(fakeLogs)
        every { submissionLogRepository.getOrphanedErrorLogs() } returns flowOf(emptyList())
        every { submissionLogRepository.getByParentId(any()) } returns flowOf(emptyList())
        viewModel = HistoryViewModel(carbRepository, submissionLogRepository, settingsRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial historyItems are empty before flow emits`() {
        assertTrue(viewModel.historyItems.value.isEmpty())
    }

    @Test
    fun `historyItems state populated from repository flow`() = runTest {
        advanceUntilIdle()
        assertEquals(2, viewModel.historyItems.value.size)
        assertTrue(viewModel.historyItems.value[0] is HistoryItem.SuccessfulLog)
        assertTrue(viewModel.historyItems.value[1] is HistoryItem.SuccessfulLog)
    }

    @Test
    fun `deleteSuccessfulLog calls repository`() = runTest {
        coEvery { carbRepository.deleteLog(1L) } just Runs
        viewModel.deleteSuccessfulLog(1L)
        advanceUntilIdle()
        coVerify { carbRepository.deleteLog(1L) }
    }

    @Test
    fun `deleteErrorLog calls repository`() = runTest {
        coEvery { submissionLogRepository.deleteSubmissionLog(1L) } just Runs
        viewModel.deleteErrorLog(1L)
        advanceUntilIdle()
        coVerify { submissionLogRepository.deleteSubmissionLog(1L) }
    }

    @Test
    fun `toggleExpand sets expandedItemId with carb- prefix`() = runTest {
        assertNull(viewModel.expandedItemId.value)
        viewModel.toggleExpand("carb-1")
        assertEquals("carb-1", viewModel.expandedItemId.value)
        viewModel.toggleExpand("carb-1")
        assertNull(viewModel.expandedItemId.value)
    }

    @Test
    fun `toggleExpand sets expandedItemId with error- prefix`() = runTest {
        assertNull(viewModel.expandedItemId.value)
        viewModel.toggleExpand("error-1")
        assertEquals("error-1", viewModel.expandedItemId.value)
        viewModel.toggleExpand("error-1")
        assertNull(viewModel.expandedItemId.value)
    }
}
