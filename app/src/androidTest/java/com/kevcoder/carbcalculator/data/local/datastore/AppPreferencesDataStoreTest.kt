package com.kevcoder.carbcalculator.data.local.datastore

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class AppPreferencesDataStoreTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var context: Context
    private lateinit var dataStore: AppPreferencesDataStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        // Delete any leftover test prefs file to ensure clean state
        File(context.filesDir, "datastore/app_preferences_test.preferences_pb").delete()
        dataStore = AppPreferencesDataStore(context)
    }

    @Test
    fun carbApiUrl_defaultsToConfiguredValue() = runTest(testDispatcher) {
        val url = dataStore.carbApiUrl.first()
        assertEquals(AppPreferencesDataStore.DEFAULT_CARB_API_URL, url)
    }

    @Test
    fun dexcomEnv_defaultsToProduction() = runTest(testDispatcher) {
        val env = dataStore.dexcomEnv.first()
        assertEquals(AppPreferencesDataStore.DEXCOM_ENV_PRODUCTION, env)
    }

    @Test
    fun saveCarbApiUrl_persistsAndReadsBack() = runTest(testDispatcher) {
        val newUrl = "http://192.168.1.100:3000"
        dataStore.saveCarbApiUrl(newUrl)
        val result = dataStore.carbApiUrl.first()
        assertEquals(newUrl, result)
    }

    @Test
    fun saveDexcomEnv_persistsAndReadsBack() = runTest(testDispatcher) {
        dataStore.saveDexcomEnv(AppPreferencesDataStore.DEXCOM_ENV_SANDBOX)
        val result = dataStore.dexcomEnv.first()
        assertEquals(AppPreferencesDataStore.DEXCOM_ENV_SANDBOX, result)
    }

    @Test
    fun saveDexcomEnv_canSwitchBackToProduction() = runTest(testDispatcher) {
        dataStore.saveDexcomEnv(AppPreferencesDataStore.DEXCOM_ENV_SANDBOX)
        dataStore.saveDexcomEnv(AppPreferencesDataStore.DEXCOM_ENV_PRODUCTION)
        val result = dataStore.dexcomEnv.first()
        assertEquals(AppPreferencesDataStore.DEXCOM_ENV_PRODUCTION, result)
    }
}
