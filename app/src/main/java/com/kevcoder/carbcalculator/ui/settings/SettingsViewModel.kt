package com.kevcoder.carbcalculator.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kevcoder.carbcalculator.auth.dexcom.DexcomTokenManager
import com.kevcoder.carbcalculator.data.local.datastore.AppPreferencesDataStore
import com.kevcoder.carbcalculator.data.repository.DexcomRepository
import com.kevcoder.carbcalculator.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val carbApiUrl: String = AppPreferencesDataStore.DEFAULT_CARB_API_URL,
    val dexcomEnv: String = AppPreferencesDataStore.DEXCOM_ENV_PRODUCTION,
    val isDexcomConnected: Boolean = false,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val dexcomRepository: DexcomRepository,
    private val tokenManager: DexcomTokenManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    /** Emits the OAuth2 URL to open in CustomTabs */
    private val _authUrlEvent = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val authUrlEvent: SharedFlow<String> = _authUrlEvent.asSharedFlow()

    init {
        viewModelScope.launch {
            settingsRepository.getSettings().collect { settings ->
                _uiState.value = _uiState.value.copy(
                    carbApiUrl = settings.carbApiUrl,
                    dexcomEnv = settings.dexcomEnv,
                    isDexcomConnected = tokenManager.isConnected(),
                )
            }
        }
    }

    fun onSaveApiUrl(url: String) {
        viewModelScope.launch { settingsRepository.saveApiUrl(url) }
    }

    fun onDexcomEnvChanged(env: String) {
        viewModelScope.launch { settingsRepository.saveDexcomEnv(env) }
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
}
