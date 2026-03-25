package com.kevcoder.carbcalculator.data.repository

import com.kevcoder.carbcalculator.auth.dexcom.DexcomTokenManager
import com.kevcoder.carbcalculator.data.local.datastore.AppPreferencesDataStore
import com.kevcoder.carbcalculator.data.remote.dexcom.DexcomApiService
import com.kevcoder.carbcalculator.data.remote.dexcom.EgvRecord
import com.kevcoder.carbcalculator.data.remote.dexcom.EgvsResponse
import io.mockk.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class DexcomRepositoryTest {

    private lateinit var dexcomApiService: DexcomApiService
    private lateinit var tokenManager: DexcomTokenManager
    private lateinit var dataStore: AppPreferencesDataStore
    private lateinit var repository: DexcomRepository

    @Before
    fun setUp() {
        dexcomApiService = mockk()
        tokenManager = mockk()
        dataStore = mockk()
        every { dataStore.dexcomEnv } returns flowOf(AppPreferencesDataStore.DEXCOM_ENV_PRODUCTION)
        repository = DexcomRepository(dexcomApiService, tokenManager, dataStore)
    }

    @Test
    fun `isConnected delegates to tokenManager`() {
        every { tokenManager.isConnected() } returns true
        assertTrue(repository.isConnected())

        every { tokenManager.isConnected() } returns false
        assertFalse(repository.isConnected())
    }

    @Test
    fun `getLatestGlucose returns null when not connected`() = runTest {
        every { tokenManager.isConnected() } returns false
        assertNull(repository.getLatestGlucose())
    }

    @Test
    fun `getLatestGlucose returns most recent EGV reading`() = runTest {
        every { tokenManager.isConnected() } returns true
        val egvs = listOf(
            EgvRecord("2026-01-01T10:00:00Z", "2026-01-01T10:00:00Z", 100, "mg/dL", "flat", 0f),
            EgvRecord("2026-01-01T10:05:00Z", "2026-01-01T10:05:00Z", 120, "mg/dL", "rising", 1f),
        )
        coEvery { dexcomApiService.getEgvs(any(), any()) } returns EgvsResponse(egvs)

        val reading = repository.getLatestGlucose()

        assertNotNull(reading)
        assertEquals(120, reading?.mgDl)
    }

    @Test
    fun `getLatestGlucose returns null when EGV list is empty`() = runTest {
        every { tokenManager.isConnected() } returns true
        coEvery { dexcomApiService.getEgvs(any(), any()) } returns EgvsResponse(emptyList())

        assertNull(repository.getLatestGlucose())
    }

    @Test
    fun `getLatestGlucose returns null when API throws`() = runTest {
        every { tokenManager.isConnected() } returns true
        coEvery { dexcomApiService.getEgvs(any(), any()) } throws RuntimeException("Network error")

        assertNull(repository.getLatestGlucose())
    }

    @Test
    fun `disconnectDexcom delegates to tokenManager`() = runTest {
        every { tokenManager.clearTokens() } just Runs
        repository.disconnectDexcom()
        verify { tokenManager.clearTokens() }
    }
}
