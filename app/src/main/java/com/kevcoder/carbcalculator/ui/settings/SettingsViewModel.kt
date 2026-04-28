package com.kevcoder.carbcalculator.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.kevcoder.carbcalculator.auth.dexcom.DexcomTokenManager
import com.kevcoder.carbcalculator.data.local.datastore.AppPreferencesDataStore
import com.kevcoder.carbcalculator.data.repository.DexcomRepository
import com.kevcoder.carbcalculator.data.repository.SettingsRepository
import com.kevcoder.carbcalculator.workers.SubmissionPurgeWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject

data class SettingsUiState(
    val carbApiUrl: String = AppPreferencesDataStore.DEFAULT_CARB_API_URL,
    val dexcomEnv: String = AppPreferencesDataStore.DEXCOM_ENV_PRODUCTION,
    val isDexcomConnected: Boolean = false,
    val submissionPurgeInterval: String = AppPreferencesDataStore.DEFAULT_PURGE_INTERVAL,
    val imageQuality: Int = AppPreferencesDataStore.DEFAULT_IMAGE_QUALITY,
    val saveImagesToDevice: Boolean = false,
    val expandSubmissionsDefault: Boolean = false,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val dexcomRepository: DexcomRepository,
    private val tokenManager: DexcomTokenManager,
    private val workManager: WorkManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    /** Emits the OAuth2 URL to open in CustomTabs */
    private val _authUrlEvent = MutableSharedFlow<String>(replay = 1, extraBufferCapacity = 1)
    val authUrlEvent: SharedFlow<String> = _authUrlEvent.asSharedFlow()

    /** Fires once each time a config change is saved successfully */
    private val _saveSuccessEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val saveSuccessEvent: SharedFlow<Unit> = _saveSuccessEvent.asSharedFlow()

    /** Emits success message on export or import */
    private val _backupEvent = MutableSharedFlow<BackupEvent>(extraBufferCapacity = 1)
    val backupEvent: SharedFlow<BackupEvent> = _backupEvent.asSharedFlow()

    init {
        viewModelScope.launch {
            settingsRepository.getSettings().collect { settings ->
                _uiState.value = _uiState.value.copy(
                    carbApiUrl = settings.carbApiUrl,
                    dexcomEnv = settings.dexcomEnv,
                    isDexcomConnected = tokenManager.isConnected(),
                    submissionPurgeInterval = settings.submissionPurgeInterval,
                    imageQuality = settings.imageQuality,
                    saveImagesToDevice = settings.saveImagesToDevice,
                    expandSubmissionsDefault = settings.expandSubmissionsDefault,
                )
            }
        }
    }

    fun onSaveApiUrl(url: String) {
        viewModelScope.launch {
            settingsRepository.saveApiUrl(url)
            _saveSuccessEvent.emit(Unit)
        }
    }

    fun onDexcomEnvChanged(env: String) {
        viewModelScope.launch {
            settingsRepository.saveDexcomEnv(env)
            _saveSuccessEvent.emit(Unit)
        }
    }

    fun onConnectDexcom() {
        viewModelScope.launch {
            val url = dexcomRepository.buildAuthUrlSuspend()
            _authUrlEvent.emit(url)
        }
    }

    fun onDisconnectDexcom() {
        viewModelScope.launch {
            dexcomRepository.disconnectDexcom()
            _uiState.value = _uiState.value.copy(isDexcomConnected = false)
        }
    }

    fun onDexcomConnected() {
        _uiState.value = _uiState.value.copy(isDexcomConnected = true)
    }

    fun onImageQualityChanged(quality: Int) {
        viewModelScope.launch {
            settingsRepository.saveImageQuality(quality)
        }
    }

    fun onSaveImagesToDeviceChanged(value: Boolean) {
        viewModelScope.launch {
            settingsRepository.saveSaveImagesToDevice(value)
            _saveSuccessEvent.emit(Unit)
        }
    }

    fun onSubmissionPurgeIntervalChanged(interval: String) {
        viewModelScope.launch {
            settingsRepository.saveSubmissionPurgeInterval(interval)
            schedulePurgeWork(interval)
            _saveSuccessEvent.emit(Unit)
        }
    }

    private fun schedulePurgeWork(interval: String) {
        workManager.cancelUniqueWork(PURGE_WORK_NAME)
        val (repeatInterval, timeUnit) = when (interval) {
            AppPreferencesDataStore.PURGE_HOURLY -> 1L to TimeUnit.HOURS
            AppPreferencesDataStore.PURGE_DAILY -> 1L to TimeUnit.DAYS
            AppPreferencesDataStore.PURGE_WEEKLY -> 7L to TimeUnit.DAYS
            AppPreferencesDataStore.PURGE_MONTHLY -> 30L to TimeUnit.DAYS
            else -> return // PURGE_NEVER — work was already cancelled above
        }
        val request = PeriodicWorkRequestBuilder<SubmissionPurgeWorker>(repeatInterval, timeUnit)
            .build()
        workManager.enqueueUniquePeriodicWork(
            PURGE_WORK_NAME,
            ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE,
            request,
        )
    }

    fun onExpandSubmissionsDefaultChanged(value: Boolean) {
        viewModelScope.launch {
            settingsRepository.saveExpandSubmissionsDefault(value)
            _saveSuccessEvent.emit(Unit)
        }
    }

    fun onExportBackup() {
        viewModelScope.launch {
            val result = settingsRepository.exportBackup()
            result.onSuccess { file ->
                _backupEvent.emit(BackupEvent.ExportSuccess(file.absolutePath))
            }
            result.onFailure { e ->
                _backupEvent.emit(BackupEvent.Error(e.message ?: "Export failed"))
            }
        }
    }

    fun onImportBackup(file: File) {
        viewModelScope.launch {
            val result = settingsRepository.importBackup(file)
            result.onSuccess { message ->
                _backupEvent.emit(BackupEvent.ImportSuccess(message))
            }
            result.onFailure { e ->
                _backupEvent.emit(BackupEvent.Error(e.message ?: "Import failed"))
            }
        }
    }

    companion object {
        private const val PURGE_WORK_NAME = "submission_purge"
    }
}

sealed class BackupEvent {
    data class ExportSuccess(val filePath: String) : BackupEvent()
    data class ImportSuccess(val message: String) : BackupEvent()
    data class Error(val message: String) : BackupEvent()
}
