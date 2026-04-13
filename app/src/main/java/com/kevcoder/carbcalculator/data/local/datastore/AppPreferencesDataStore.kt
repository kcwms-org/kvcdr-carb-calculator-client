package com.kevcoder.carbcalculator.data.local.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "app_preferences")

@Singleton
class AppPreferencesDataStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        val CARB_API_URL = stringPreferencesKey("carb_api_url")
        val DEXCOM_ENV = stringPreferencesKey("dexcom_env")
        val SUBMISSION_PURGE_INTERVAL = stringPreferencesKey("submission_purge_interval")
        val IMAGE_QUALITY = intPreferencesKey("image_quality")
        val SAVE_IMAGES_TO_DEVICE = booleanPreferencesKey("save_images_to_device")
        val EXPAND_SUBMISSIONS_DEFAULT = booleanPreferencesKey("expand_submissions_default")

        const val DEFAULT_CARB_API_URL = "https://carb-calculator.kevcoder.com"
        const val DEXCOM_ENV_PRODUCTION = "production"
        const val DEXCOM_ENV_SANDBOX = "sandbox"

        const val PURGE_NEVER = "never"
        const val PURGE_HOURLY = "hourly"
        const val PURGE_DAILY = "daily"
        const val PURGE_WEEKLY = "weekly"
        const val PURGE_MONTHLY = "monthly"
        const val DEFAULT_PURGE_INTERVAL = PURGE_NEVER

        const val DEFAULT_IMAGE_QUALITY = 80
    }

    val carbApiUrl: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[CARB_API_URL] ?: DEFAULT_CARB_API_URL
    }

    val dexcomEnv: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[DEXCOM_ENV] ?: DEXCOM_ENV_PRODUCTION
    }

    val submissionPurgeInterval: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[SUBMISSION_PURGE_INTERVAL] ?: DEFAULT_PURGE_INTERVAL
    }

    val imageQuality: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[IMAGE_QUALITY] ?: DEFAULT_IMAGE_QUALITY
    }

    val saveImagesToDevice: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[SAVE_IMAGES_TO_DEVICE] ?: false
    }

    val expandSubmissionsDefault: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[EXPAND_SUBMISSIONS_DEFAULT] ?: false
    }

    suspend fun saveCarbApiUrl(url: String) {
        context.dataStore.edit { prefs -> prefs[CARB_API_URL] = url }
    }

    suspend fun saveDexcomEnv(env: String) {
        context.dataStore.edit { prefs -> prefs[DEXCOM_ENV] = env }
    }

    suspend fun saveSubmissionPurgeInterval(interval: String) {
        context.dataStore.edit { prefs -> prefs[SUBMISSION_PURGE_INTERVAL] = interval }
    }

    suspend fun saveImageQuality(quality: Int) {
        context.dataStore.edit { prefs -> prefs[IMAGE_QUALITY] = quality.coerceIn(1, 100) }
    }

    suspend fun saveSaveImagesToDevice(value: Boolean) {
        context.dataStore.edit { prefs -> prefs[SAVE_IMAGES_TO_DEVICE] = value }
    }

    suspend fun saveExpandSubmissionsDefault(value: Boolean) {
        context.dataStore.edit { prefs -> prefs[EXPAND_SUBMISSIONS_DEFAULT] = value }
    }
}
