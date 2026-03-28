package com.kevcoder.carbcalculator.ui.capture

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kevcoder.carbcalculator.data.remote.carbapi.CarbApiCapture
import com.kevcoder.carbcalculator.data.repository.AnalysisResultCache
import com.kevcoder.carbcalculator.data.repository.CarbRepository
import com.kevcoder.carbcalculator.data.repository.SubmissionLogRepository
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
    private val carbApiCapture: CarbApiCapture,
    private val submissionLogRepository: SubmissionLogRepository,
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
            val cleanDescription = description.takeIf { !it.isNullOrBlank() }
            val submissionId = submissionLogRepository.logRequest(
                imagePath = imageFile.absolutePath,
                imageSizeBytes = imageFile.length().takeIf { it > 0 },
                foodDescription = cleanDescription,
            )
            try {
                val analyzed = carbRepository.analyzeFood(imageFile, cleanDescription)
                submissionLogRepository.logSuccess(
                    submissionId = submissionId,
                    result = analyzed.analysisResult,
                    requestHeaders = analyzed.requestHeaders,
                    responseHeaders = analyzed.responseHeaders,
                    responseBody = analyzed.responseBody,
                )
                resultCache.put(analyzed.analysisResult)
                resultCache.putSubmissionId(submissionId)
                onSuccess()
            } catch (e: Exception) {
                submissionLogRepository.logError(
                    submissionId = submissionId,
                    errorMessage = e.message ?: "Analysis failed",
                    requestHeaders = carbApiCapture.requestHeaders,
                    responseHeaders = carbApiCapture.responseHeaders,
                    responseBody = carbApiCapture.responseBody,
                )
                _uiState.value = CaptureUiState.Error(e.message ?: "Analysis failed")
            }
        }
    }

    fun resetState() {
        _uiState.value = CaptureUiState.Idle
    }
}
