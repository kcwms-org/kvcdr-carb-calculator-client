package com.kevcoder.carbcalculator.data.repository

import com.kevcoder.carbcalculator.data.local.datastore.AppPreferencesDataStore
import com.kevcoder.carbcalculator.domain.model.AppSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepository @Inject constructor(
    private val dataStore: AppPreferencesDataStore,
) {
    fun getSettings(): Flow<AppSettings> = combine(
        dataStore.carbApiUrl,
        dataStore.dexcomEnv,
    ) { url, env -> AppSettings(carbApiUrl = url, dexcomEnv = env) }

    suspend fun saveApiUrl(url: String) = dataStore.saveCarbApiUrl(url)

    suspend fun saveDexcomEnv(env: String) = dataStore.saveDexcomEnv(env)
}
