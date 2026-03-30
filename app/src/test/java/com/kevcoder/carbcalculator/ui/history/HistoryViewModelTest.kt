package com.kevcoder.carbcalculator.ui.history

import com.kevcoder.carbcalculator.data.repository.CarbRepository
import com.kevcoder.carbcalculator.data.repository.SubmissionLogRepository
import com.kevcoder.carbcalculator.domain.model.CarbLog
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
        every { carbRepository.getLogs() } returns flowOf(fakeLogs)
        every { submissionLogRepository.getByParentId(any()) } returns flowOf(emptyList())
        viewModel = HistoryViewModel(carbRepository, submissionLogRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial logs are empty before flow emits`() {
        assertTrue(viewModel.logs.value.isEmpty())
    }

    @Test
    fun `logs state populated from repository flow`() = runTest {
        advanceUntilIdle()
        assertEquals(fakeLogs, viewModel.logs.value)
    }

    @Test
    fun `deleteLog calls repository`() = runTest {
        coEvery { carbRepository.deleteLog(1L) } just Runs
        viewModel.deleteLog(1L)
        advanceUntilIdle()
        coVerify { carbRepository.deleteLog(1L) }
    }

    @Test
    fun `toggleExpand sets expandedLogId`() = runTest {
        assertNull(viewModel.expandedLogId.value)
        viewModel.toggleExpand(1L)
        assertEquals(1L, viewModel.expandedLogId.value)
        viewModel.toggleExpand(1L)
        assertNull(viewModel.expandedLogId.value)
    }
}
