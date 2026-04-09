package com.kevcoder.carbcalculator.ui.capture

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kevcoder.carbcalculator.data.repository.AnalysisResultCache
import com.kevcoder.carbcalculator.data.repository.CarbRepository
import com.kevcoder.carbcalculator.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import java.time.OffsetDateTime
import javax.inject.Inject

sealed interface CaptureUiState {
    data object Idle : CaptureUiState
    data class PhotoTaken(val imagePath: String) : CaptureUiState
    data object Uploading : CaptureUiState
    data class Error(val message: String) : CaptureUiState
}

@HiltViewModel
class CaptureViewModel @Inject constructor(
    private val carbRepository: CarbRepository,
    private val resultCache: AnalysisResultCache,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<CaptureUiState>(CaptureUiState.Idle)
    val uiState: StateFlow<CaptureUiState> = _uiState.asStateFlow()

    private val _selectedDatetime = MutableStateFlow(OffsetDateTime.now())
    val selectedDatetime: StateFlow<OffsetDateTime> = _selectedDatetime.asStateFlow()

    fun onPhotoCaptured(imageFile: File) {
        _uiState.value = CaptureUiState.PhotoTaken(imageFile.absolutePath)
    }

    fun onDatetimeSelected(datetime: OffsetDateTime) {
        _selectedDatetime.value = datetime
    }

    fun resetDatetime() {
        _selectedDatetime.value = OffsetDateTime.now()
    }

    fun onCaptureFailed(message: String) {
        _uiState.value = CaptureUiState.Error(message)
    }

    fun onAnalyze(imageFile: File?, description: String?, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _uiState.value = CaptureUiState.Uploading
            val cleanDescription = description.takeIf { !it.isNullOrBlank() }
            try {
                val imageQuality = settingsRepository.getImageQuality().first()
                val analyzed = carbRepository.analyzeFood(
                    imageFile,
                    cleanDescription,
                    imageQuality,
                    _selectedDatetime.value,
                )
                resultCache.put(
                    result = analyzed.analysisResult,
                    requestHeaders = analyzed.requestHeaders,
                    responseHeaders = analyzed.responseHeaders,
                    responseBody = analyzed.responseBody,
                )
                resetState()
                resetDatetime()
                onSuccess()
            } catch (e: Exception) {
                _uiState.value = CaptureUiState.Error(e.message ?: "Analysis failed")
            }
        }
    }

    fun resetState() {
        _uiState.value = CaptureUiState.Idle
    }
}
