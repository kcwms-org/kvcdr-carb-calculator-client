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
        dataStore.submissionPurgeInterval,
    ) { url, env, purgeInterval ->
        AppSettings(carbApiUrl = url, dexcomEnv = env, submissionPurgeInterval = purgeInterval)
    }

    suspend fun saveApiUrl(url: String) = dataStore.saveCarbApiUrl(url)

    suspend fun saveDexcomEnv(env: String) = dataStore.saveDexcomEnv(env)

    suspend fun saveSubmissionPurgeInterval(interval: String) =
        dataStore.saveSubmissionPurgeInterval(interval)
}
