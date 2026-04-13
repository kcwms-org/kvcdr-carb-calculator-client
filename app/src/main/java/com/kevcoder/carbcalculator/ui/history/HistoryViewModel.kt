package com.kevcoder.carbcalculator.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kevcoder.carbcalculator.data.repository.CarbRepository
import com.kevcoder.carbcalculator.data.repository.SettingsRepository
import com.kevcoder.carbcalculator.data.repository.SubmissionLogRepository
import com.kevcoder.carbcalculator.domain.model.CarbLog
import com.kevcoder.carbcalculator.domain.model.SubmissionLog
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Sealed class to represent either a successful CarbLog or an orphaned error SubmissionLog */
sealed interface HistoryItem {
    val timestamp: Long

    data class SuccessfulLog(val carbLog: CarbLog) : HistoryItem {
        override val timestamp: Long get() = carbLog.timestamp
    }
    data class ErrorLog(val submissionLog: SubmissionLog) : HistoryItem {
        override val timestamp: Long get() = submissionLog.requestTimestamp
    }
}

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val carbRepository: CarbRepository,
    private val submissionLogRepository: SubmissionLogRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    // Get successful CarbLogs
    private val carbLogs = carbRepository.getLogs()

    // Get orphaned error SubmissionLogs
    private val orphanedErrors = submissionLogRepository.getOrphanedErrorLogs()

    // Note: expandByDefault setting loaded but not used for initial expansion
    // Users explicitly toggle expand; this setting could be used for future UX improvements

    // Combine both into a single list sorted by timestamp (desc)
    val historyItems: StateFlow<List<HistoryItem>> = combine(carbLogs, orphanedErrors) { logs, errors ->
        val items = logs.map { HistoryItem.SuccessfulLog(it) } +
                errors.map { HistoryItem.ErrorLog(it) }
        items.sortedByDescending { it.timestamp }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = emptyList(),
    )

    private val _expandedItemId = MutableStateFlow<String?>(null)
    val expandedItemId: StateFlow<String?> = _expandedItemId

    private val _viewingImageLog = MutableStateFlow<CarbLog?>(null)
    val viewingImageLog: StateFlow<CarbLog?> = _viewingImageLog

    @OptIn(ExperimentalCoroutinesApi::class)
    val expandedSubmissions: StateFlow<List<SubmissionLog>> = _expandedItemId
        .flatMapLatest { id ->
            if (id != null && id.startsWith("carb-")) {
                val carbLogId = id.removePrefix("carb-").toLongOrNull() ?: return@flatMapLatest flowOf(emptyList())
                submissionLogRepository.getByParentId(carbLogId)
            } else {
                flowOf(emptyList())
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = emptyList(),
        )

    fun toggleExpand(id: String) {
        _expandedItemId.value = if (_expandedItemId.value == id) null else id
    }

    fun deleteSuccessfulLog(id: Long) {
        viewModelScope.launch {
            if (_expandedItemId.value == "carb-$id") _expandedItemId.value = null
            carbRepository.deleteLog(id)
        }
    }

    fun deleteErrorLog(id: Long) {
        viewModelScope.launch {
            if (_expandedItemId.value == "error-$id") _expandedItemId.value = null
            submissionLogRepository.deleteSubmissionLog(id)
        }
    }

    fun onImageClick(log: CarbLog) {
        _viewingImageLog.value = log
    }

    fun onImageViewerDismiss() {
        _viewingImageLog.value = null
    }
}
