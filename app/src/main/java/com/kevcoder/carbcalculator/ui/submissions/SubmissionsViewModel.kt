package com.kevcoder.carbcalculator.ui.submissions

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kevcoder.carbcalculator.data.repository.SubmissionLogRepository
import com.kevcoder.carbcalculator.domain.model.FoodItem
import com.kevcoder.carbcalculator.domain.model.SubmissionLog
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject

@HiltViewModel
class SubmissionsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: SubmissionLogRepository,
) : ViewModel() {

    val logs: StateFlow<List<SubmissionLog>> = repository.getAll()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = emptyList(),
        )

    fun deleteAll() {
        viewModelScope.launch { repository.deleteAll() }
    }

    fun copyLogAsJson(log: SubmissionLog) {
        copyToClipboard("submission_log_${log.id}", log.toJsonString())
    }

    fun copyAllAsJson() {
        val array = JSONArray()
        logs.value.forEach { array.put(JSONObject(it.toJsonString())) }
        copyToClipboard("all_submission_logs", array.toString(2))
    }

    private fun copyToClipboard(label: String, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
    }

    private fun SubmissionLog.toJsonString(): String {
        val obj = JSONObject()
        obj.put("id", id)
        obj.put("requestTimestamp", requestTimestamp)
        obj.put("imagePath", imagePath ?: JSONObject.NULL)
        obj.put("imageSizeBytes", imageSizeBytes ?: JSONObject.NULL)
        obj.put("foodDescription", foodDescription ?: JSONObject.NULL)
        obj.put("status", status)
        obj.put("totalCarbs", totalCarbs ?: JSONObject.NULL)
        obj.put("errorMessage", errorMessage ?: JSONObject.NULL)
        obj.put("responseTimestamp", responseTimestamp ?: JSONObject.NULL)
        obj.put("savedLogId", savedLogId ?: JSONObject.NULL)
        val itemsArray = JSONArray()
        items.forEach { item ->
            val itemObj = JSONObject()
            itemObj.put("name", item.name)
            itemObj.put("estimatedCarbs", item.estimatedCarbs)
            itemsArray.put(itemObj)
        }
        obj.put("items", itemsArray)
        return obj.toString(2)
    }
}
