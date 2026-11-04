package com.kevcoder.carbcalculator.ui.edit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kevcoder.carbcalculator.data.remote.carbapi.CarbApiCapture
import com.kevcoder.carbcalculator.data.repository.AnalysisResultCache
import com.kevcoder.carbcalculator.data.repository.CarbRepository
import com.kevcoder.carbcalculator.data.repository.EditLogCache
import com.kevcoder.carbcalculator.data.repository.SettingsRepository
import com.kevcoder.carbcalculator.data.repository.SubmissionLogRepository
import com.kevcoder.carbcalculator.domain.model.AnalysisResult
import com.kevcoder.carbcalculator.domain.model.FoodItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class EditableFoodItem(val id: Int, val name: String, val estimatedCarbs: String)

data class EditSubmissionUiState(
    val foodDescription: String = "",
    val items: List<EditableFoodItem> = emptyList(),
    val imagePath: String? = null,
    val imageData: ByteArray? = null,
    val isSubmitting: Boolean = false,
    val isSaving: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class EditSubmissionViewModel @Inject constructor(
    private val editLogCache: EditLogCache,
    private val resultCache: AnalysisResultCache,
    private val carbRepository: CarbRepository,
    private val submissionLogRepository: SubmissionLogRepository,
    private val settingsRepository: SettingsRepository,
    private val carbApiCapture: CarbApiCapture,
) : ViewModel() {

    private val _uiState = MutableStateFlow(EditSubmissionUiState())
    val uiState: StateFlow<EditSubmissionUiState> = _uiState.asStateFlow()

    private var nextItemId = 0

    init {
        val log = editLogCache.get()
        if (log != null) {
            _uiState.value = EditSubmissionUiState(
                foodDescription = log.foodDescription ?: "",
                items = log.items.map { EditableFoodItem(nextItemId++, it.name, it.estimatedCarbs.toString()) },
                imagePath = log.thumbnailPath,
                imageData = log.imageData,
            )
        }
    }

    fun onDescriptionChange(value: String) {
        _uiState.value = _uiState.value.copy(foodDescription = value)
    }

    fun onItemNameChange(id: Int, name: String) {
        _uiState.value = _uiState.value.copy(
            items = _uiState.value.items.map { if (it.id == id) it.copy(name = name) else it },
        )
    }

    fun onItemCarbsChange(id: Int, carbs: String) {
        _uiState.value = _uiState.value.copy(
            items = _uiState.value.items.map { if (it.id == id) it.copy(estimatedCarbs = carbs) else it },
        )
    }

    fun onAddItem() {
        _uiState.value = _uiState.value.copy(
            items = _uiState.value.items + EditableFoodItem(nextItemId++, "", ""),
        )
    }

    fun onRemoveItem(id: Int) {
        _uiState.value = _uiState.value.copy(
            items = _uiState.value.items.filter { it.id != id },
        )
    }

    fun onSaveEdits(onSuccess: () -> Unit) {
        val state = _uiState.value
        val validationError = validateItems(state.items)
        if (validationError != null) {
            _uiState.value = state.copy(error = validationError)
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, error = null)
            val foodItems = _uiState.value.items.map { FoodItem(it.name.trim(), it.estimatedCarbs.toFloat()) }
            val totalCarbs = foodItems.sumOf { it.estimatedCarbs.toDouble() }.toFloat()
            val result = AnalysisResult(
                items = foodItems,
                totalCarbs = totalCarbs,
                foodDescription = _uiState.value.foodDescription.takeIf { it.isNotBlank() },
                imagePath = _uiState.value.imagePath,
                imageData = _uiState.value.imageData,
            )
            resultCache.put(result = result, requestHeaders = null, responseHeaders = null, responseBody = null)

            val originalLogId = editLogCache.getOriginalLogId()
            if (originalLogId != null) {
                carbRepository.deleteLog(originalLogId)
            }
            editLogCache.clear()
            _uiState.value = _uiState.value.copy(isSaving = false)
            onSuccess()
        }
    }

    fun onResubmit(onSuccess: () -> Unit) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSubmitting = true, error = null)
            val description = _uiState.value.foodDescription.takeIf { it.isNotBlank() }
            val imagePath = _uiState.value.imagePath
            val imageFile = imagePath?.let { File(it).takeIf { f -> f.exists() } }
            try {
                val imageQuality = settingsRepository.getImageQuality().first()
                val analyzed = carbRepository.analyzeFood(
                    imageFile = imageFile,
                    description = description,
                    imageQuality = imageQuality,
                )
                resultCache.put(
                    result = analyzed.analysisResult,
                    requestHeaders = analyzed.requestHeaders,
                    responseHeaders = analyzed.responseHeaders,
                    responseBody = analyzed.responseBody,
                )

                val originalLogId = editLogCache.getOriginalLogId()
                if (originalLogId != null) {
                    carbRepository.deleteLog(originalLogId)
                }
                editLogCache.clear()
                _uiState.value = _uiState.value.copy(isSubmitting = false)
                onSuccess()
            } catch (e: Exception) {
                submissionLogRepository.logRequest(
                    carbLogId = null,
                    imagePath = imagePath,
                    imageSizeBytes = imageFile?.length(),
                    foodDescription = description,
                    status = "error",
                    foodItemsJson = null,
                    totalCarbs = null,
                    errorMessage = e.message ?: "Re-analysis failed",
                    responseTimestamp = System.currentTimeMillis(),
                    requestHeaders = carbApiCapture.requestHeaders,
                    responseHeaders = carbApiCapture.responseHeaders,
                    responseBody = carbApiCapture.responseBody,
                )
                _uiState.value = _uiState.value.copy(
                    isSubmitting = false,
                    error = e.message ?: "Re-analysis failed",
                )
            }
        }
    }

    fun onDiscard(onDiscard: () -> Unit) {
        editLogCache.clear()
        onDiscard()
    }

    private fun validateItems(items: List<EditableFoodItem>): String? {
        if (items.isEmpty()) return "Add at least one food item"
        for (item in items) {
            if (item.name.isBlank()) return "All items must have a name"
            val carbs = item.estimatedCarbs.toFloatOrNull()
            if (carbs == null || !carbs.isFinite() || carbs < 0f) return "All items must have a carb value of 0 or more"
        }
        return null
    }
}
