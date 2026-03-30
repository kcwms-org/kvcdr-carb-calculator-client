package com.kevcoder.carbcalculator.ui.result

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kevcoder.carbcalculator.data.repository.AnalysisResultCache
import com.kevcoder.carbcalculator.data.repository.CarbRepository
import com.kevcoder.carbcalculator.data.repository.DexcomRepository
import com.kevcoder.carbcalculator.data.repository.SubmissionLogRepository
import com.kevcoder.carbcalculator.domain.model.AnalysisResult
import com.kevcoder.carbcalculator.domain.model.GlucoseReading
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ResultUiState(
    val result: AnalysisResult? = null,
    val glucose: GlucoseReading? = null,
    val isSaving: Boolean = false,
    val saved: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class ResultViewModel @Inject constructor(
    private val resultCache: AnalysisResultCache,
    private val carbRepository: CarbRepository,
    private val submissionLogRepository: SubmissionLogRepository,
    private val dexcomRepository: DexcomRepository,
    private val moshi: Moshi,
) : ViewModel() {

    private val foodItemListType = Types.newParameterizedType(
        List::class.java,
        SubmissionLogRepository.FoodItemJson::class.java,
    )
    private val foodItemJsonAdapter = moshi.adapter<List<SubmissionLogRepository.FoodItemJson>>(foodItemListType)

    private val _uiState = MutableStateFlow(ResultUiState())
    val uiState: StateFlow<ResultUiState> = _uiState.asStateFlow()

    init {
        val result = resultCache.get()
        _uiState.value = _uiState.value.copy(result = result)
        if (result != null) fetchGlucose()
    }

    private fun fetchGlucose() {
        viewModelScope.launch {
            val reading = dexcomRepository.getLatestGlucose()
            _uiState.value = _uiState.value.copy(glucose = reading)
        }
    }

    fun onSave(onSuccess: () -> Unit) {
        val result = _uiState.value.result ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, error = null)
            try {
                val carbLogId = carbRepository.saveLog(result, _uiState.value.glucose)

                val foodItemsJson = foodItemJsonAdapter.toJson(
                    result.items.map { SubmissionLogRepository.FoodItemJson(it.name, it.estimatedCarbs) }
                )
                submissionLogRepository.logRequest(
                    carbLogId = carbLogId,
                    imagePath = result.imagePath,
                    imageSizeBytes = result.imagePath?.let { java.io.File(it).takeIf { f -> f.exists() }?.length() },
                    foodDescription = result.foodDescription,
                    status = "success",
                    foodItemsJson = foodItemsJson,
                    totalCarbs = result.totalCarbs,
                    errorMessage = null,
                    responseTimestamp = System.currentTimeMillis(),
                    requestHeaders = resultCache.getRequestHeaders(),
                    responseHeaders = resultCache.getResponseHeaders(),
                    responseBody = resultCache.getResponseBody(),
                )

                resultCache.clear()
                _uiState.value = _uiState.value.copy(isSaving = false, saved = true)
                onSuccess()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    error = e.message ?: "Failed to save",
                )
            }
        }
    }

    fun onDiscard(onDiscard: () -> Unit) {
        resultCache.clear()
        onDiscard()
    }
}
