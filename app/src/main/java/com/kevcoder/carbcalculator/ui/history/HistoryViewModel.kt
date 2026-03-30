package com.kevcoder.carbcalculator.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kevcoder.carbcalculator.data.repository.CarbRepository
import com.kevcoder.carbcalculator.data.repository.SubmissionLogRepository
import com.kevcoder.carbcalculator.domain.model.CarbLog
import com.kevcoder.carbcalculator.domain.model.SubmissionLog
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val carbRepository: CarbRepository,
    private val submissionLogRepository: SubmissionLogRepository,
) : ViewModel() {

    val logs: StateFlow<List<CarbLog>> = carbRepository.getLogs()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = emptyList(),
        )

    private val _expandedLogId = MutableStateFlow<Long?>(null)
    val expandedLogId: StateFlow<Long?> = _expandedLogId

    @OptIn(ExperimentalCoroutinesApi::class)
    val expandedSubmissions: StateFlow<List<SubmissionLog>> = _expandedLogId
        .flatMapLatest { id ->
            if (id != null) submissionLogRepository.getByParentId(id)
            else flowOf(emptyList())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = emptyList(),
        )

    fun toggleExpand(id: Long) {
        _expandedLogId.value = if (_expandedLogId.value == id) null else id
    }

    fun deleteLog(id: Long) {
        viewModelScope.launch {
            if (_expandedLogId.value == id) _expandedLogId.value = null
            carbRepository.deleteLog(id)
        }
    }
}
