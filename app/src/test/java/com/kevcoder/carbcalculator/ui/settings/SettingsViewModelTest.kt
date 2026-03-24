package com.kevcoder.carbcalculator.ui.settings

import com.kevcoder.carbcalculator.auth.dexcom.DexcomTokenManager
import com.kevcoder.carbcalculator.data.local.datastore.AppPreferencesDataStore
import com.kevcoder.carbcalculator.data.repository.DexcomRepository
import com.kevcoder.carbcalculator.data.repository.SettingsRepository
import com.kevcoder.carbcalculator.domain.model.AppSettings
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var dexcomRepository: DexcomRepository
    private lateinit var tokenManager: DexcomTokenManager
    private lateinit var viewModel: SettingsViewModel

    private val defaultSettings = AppSettings(
        carbApiUrl = AppPreferencesDataStore.DEFAULT_CARB_API_URL,
        dexcomEnv = AppPreferencesDataStore.DEXCOM_ENV_PRODUCTION,
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        settingsRepository = mockk()
        dexcomRepository = mockk()
        tokenManager = mockk()
        every { settingsRepository.getSettings() } returns flowOf(defaultSettings)
        every { tokenManager.isConnected() } returns false
        viewModel = SettingsViewModel(settingsRepository, dexcomRepository, tokenManager)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state reflects default settings`() = runTest {
        advanceUntilIdle()
        val state = viewModel.uiState.value
        assertEquals(AppPreferencesDataStore.DEFAULT_CARB_API_URL, state.carbApiUrl)
        assertEquals(AppPreferencesDataStore.DEXCOM_ENV_PRODUCTION, state.dexcomEnv)
        assertFalse(state.isDexcomConnected)
    }

    @Test
    fun `isDexcomConnected is true when tokenManager reports connected`() = runTest {
        every { tokenManager.isConnected() } returns true
        viewModel = SettingsViewModel(settingsRepository, dexcomRepository, tokenManager)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isDexcomConnected)
    }

    @Test
    fun `onSaveApiUrl delegates to repository`() = runTest {
        coEvery { settingsRepository.saveApiUrl(any()) } just Runs
        viewModel.onSaveApiUrl("http://localhost:3000")
        advanceUntilIdle()
        coVerify { settingsRepository.saveApiUrl("http://localhost:3000") }
    }

    @Test
    fun `onDexcomEnvChanged delegates to repository`() = runTest {
        coEvery { settingsRepository.saveDexcomEnv(any()) } just Runs
        viewModel.onDexcomEnvChanged(AppPreferencesDataStore.DEXCOM_ENV_SANDBOX)
        advanceUntilIdle()
        coVerify { settingsRepository.saveDexcomEnv(AppPreferencesDataStore.DEXCOM_ENV_SANDBOX) }
    }

    @Test
    fun `onConnectDexcom emits auth URL event`() = runTest {
        coEvery { dexcomRepository.buildAuthUrlSuspend() } returns "https://api.dexcom.com/v3/oauth2/login?client_id=test"
        viewModel.onConnectDexcom()
        advanceUntilIdle()
        val url = viewModel.authUrlEvent.first()
        assertEquals("https://api.dexcom.com/v3/oauth2/login?client_id=test", url)
    }

    @Test
    fun `onDisconnectDexcom calls repository and updates connected state`() = runTest {
        coEvery { dexcomRepository.disconnectDexcom() } just Runs
        viewModel.onDisconnectDexcom()
        advanceUntilIdle()
        coVerify { dexcomRepository.disconnectDexcom() }
        assertFalse(viewModel.uiState.value.isDexcomConnected)
    }

    @Test
    fun `onDexcomConnected updates connected state to true`() {
        viewModel.onDexcomConnected()
        assertTrue(viewModel.uiState.value.isDexcomConnected)
    }
}
