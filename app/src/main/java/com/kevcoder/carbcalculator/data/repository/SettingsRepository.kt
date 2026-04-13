package com.kevcoder.carbcalculator.data.repository

import com.kevcoder.carbcalculator.data.local.datastore.AppPreferencesDataStore
import com.kevcoder.carbcalculator.domain.model.AppSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
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
        dataStore.expandSubmissionsDefault,
    ) { values ->
        AppSettings(
            carbApiUrl = values[0] as String,
            dexcomEnv = values[1] as String,
            submissionPurgeInterval = values[2] as String,
            imageQuality = values[3] as Int,
            saveImagesToDevice = values[4] as Boolean,
            expandSubmissionsDefault = values[5] as Boolean,
        )
    }

    suspend fun saveApiUrl(url: String) = dataStore.saveCarbApiUrl(url)

    suspend fun saveDexcomEnv(env: String) = dataStore.saveDexcomEnv(env)

    suspend fun saveSubmissionPurgeInterval(interval: String) =
        dataStore.saveSubmissionPurgeInterval(interval)

    suspend fun saveImageQuality(quality: Int) = dataStore.saveImageQuality(quality)

    suspend fun saveSaveImagesToDevice(value: Boolean) = dataStore.saveSaveImagesToDevice(value)

    suspend fun saveExpandSubmissionsDefault(value: Boolean) = dataStore.saveExpandSubmissionsDefault(value)

    fun getImageQuality(): Flow<Int> = dataStore.imageQuality
}
