package com.kevcoder.carbcalculator.ui.capture

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kevcoder.carbcalculator.data.repository.AnalysisResultCache
import com.kevcoder.carbcalculator.data.repository.CarbRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
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
) : ViewModel() {

    private val _uiState = MutableStateFlow<CaptureUiState>(CaptureUiState.Idle)
    val uiState: StateFlow<CaptureUiState> = _uiState.asStateFlow()

    fun onPhotoCaptured(imageFile: File) {
        _uiState.value = CaptureUiState.PhotoTaken(imageFile.absolutePath)
    }

    fun onCaptureFailed(message: String) {
        _uiState.value = CaptureUiState.Error(message)
    }

    fun onAnalyze(imageFile: File, description: String?, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _uiState.value = CaptureUiState.Uploading
            try {
                val result = carbRepository.analyzeFood(imageFile, description.takeIf { !it.isNullOrBlank() })
                resultCache.put(result)
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
