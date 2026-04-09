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
        dataStore.imageQuality,
        dataStore.saveImagesToDevice,
    ) { url, env, purgeInterval, imageQuality, saveImagesToDevice ->
        AppSettings(carbApiUrl = url, dexcomEnv = env, submissionPurgeInterval = purgeInterval, imageQuality = imageQuality, saveImagesToDevice = saveImagesToDevice)
    }

    suspend fun saveApiUrl(url: String) = dataStore.saveCarbApiUrl(url)

    suspend fun saveDexcomEnv(env: String) = dataStore.saveDexcomEnv(env)

    suspend fun saveSubmissionPurgeInterval(interval: String) =
        dataStore.saveSubmissionPurgeInterval(interval)

    suspend fun saveImageQuality(quality: Int) = dataStore.saveImageQuality(quality)

    suspend fun saveSaveImagesToDevice(value: Boolean) = dataStore.saveSaveImagesToDevice(value)

    fun getImageQuality(): Flow<Int> = dataStore.imageQuality
}
