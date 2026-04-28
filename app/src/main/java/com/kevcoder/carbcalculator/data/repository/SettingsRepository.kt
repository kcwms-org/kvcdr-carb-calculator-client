package com.kevcoder.carbcalculator.data.repository

import android.content.Context
import com.kevcoder.carbcalculator.data.local.datastore.AppPreferencesDataStore
import com.kevcoder.carbcalculator.data.local.db.CarbLogDao
import com.kevcoder.carbcalculator.data.local.db.CarbLogEntity
import com.kevcoder.carbcalculator.data.util.AppBackup
import com.kevcoder.carbcalculator.data.util.CarbLogSnapshot
import com.kevcoder.carbcalculator.data.util.SettingsSnapshot
import com.kevcoder.carbcalculator.domain.model.AppSettings
import com.squareup.moshi.Moshi
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dataStore: AppPreferencesDataStore,
    private val carbLogDao: CarbLogDao,
    private val moshi: Moshi,
    @Named("carb") private val carbApiClient: OkHttpClient,
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

    suspend fun exportBackup(): Result<File> = withContext(Dispatchers.IO) {
        try {
            val settings = SettingsSnapshot(
                carbApiUrl = dataStore.carbApiUrl.first(),
                dexcomEnv = dataStore.dexcomEnv.first(),
                imageQuality = dataStore.imageQuality.first(),
                submissionPurgeInterval = dataStore.submissionPurgeInterval.first(),
                saveImagesToDevice = dataStore.saveImagesToDevice.first(),
                expandSubmissionsDefault = dataStore.expandSubmissionsDefault.first(),
            )

            val logs = carbLogDao.getAllLogs().first().map { entity ->
                CarbLogSnapshot(
                    timestamp = entity.timestamp,
                    foodDescription = entity.foodDescription,
                    foodItemsJson = entity.foodItemsJson,
                    totalCarbs = entity.totalCarbs,
                    glucoseMgDl = entity.glucoseMgDl,
                    glucoseTimestamp = entity.glucoseTimestamp,
                    imageData = entity.imageData,
                    thumbnailPath = entity.thumbnailPath,
                )
            }

            val backup = AppBackup(settings = settings, carbLogs = logs)
            val json = moshi.adapter(AppBackup::class.java).toJson(backup)

            val downloadsDir = File(context.getExternalFilesDir(null), "Downloads")
            downloadsDir.mkdirs()
            val backupFile = File(downloadsDir, "carb-calculator-backup-${System.currentTimeMillis()}.json")
            backupFile.writeText(json)

            Result.success(backupFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun importBackup(file: File): Result<String> = withContext(Dispatchers.IO) {
        try {
            val json = file.readText()
            val backup = moshi.adapter(AppBackup::class.java).fromJson(json)
                ?: return@withContext Result.failure(Exception("Invalid backup format"))

            // Validate API URL is reachable
            val validationResult = validateApiUrl(backup.settings.carbApiUrl)
            if (!validationResult) {
                return@withContext Result.failure(Exception("API URL is not reachable. Please check the URL and try again."))
            }

            // Restore settings
            saveApiUrl(backup.settings.carbApiUrl)
            saveDexcomEnv(backup.settings.dexcomEnv)
            saveImageQuality(backup.settings.imageQuality)
            saveSubmissionPurgeInterval(backup.settings.submissionPurgeInterval)
            saveSaveImagesToDevice(backup.settings.saveImagesToDevice)
            saveExpandSubmissionsDefault(backup.settings.expandSubmissionsDefault)

            // Restore logs
            backup.carbLogs.forEach { snapshot ->
                carbLogDao.insert(
                    CarbLogEntity(
                        timestamp = snapshot.timestamp,
                        foodDescription = snapshot.foodDescription,
                        foodItemsJson = snapshot.foodItemsJson,
                        totalCarbs = snapshot.totalCarbs,
                        glucoseMgDl = snapshot.glucoseMgDl,
                        glucoseTimestamp = snapshot.glucoseTimestamp,
                        imageData = snapshot.imageData,
                        thumbnailPath = snapshot.thumbnailPath,
                    )
                )
            }

            Result.success("Restored ${backup.carbLogs.size} logged meals and settings")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun validateApiUrl(url: String): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            val testUrl = url.trimEnd('/') + "/presign"
            val request = Request.Builder().url(testUrl).build()
            val response = carbApiClient.newCall(request).execute()
            response.isSuccessful || response.code == 401 // Accept 401 if auth fails; means URL exists
        } catch (e: Exception) {
            false
        }
    }
}
