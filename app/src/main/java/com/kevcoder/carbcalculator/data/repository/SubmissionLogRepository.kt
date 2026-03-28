package com.kevcoder.carbcalculator.data.repository

import com.kevcoder.carbcalculator.data.local.db.SubmissionLogDao
import com.kevcoder.carbcalculator.data.local.db.SubmissionLogEntity
import com.kevcoder.carbcalculator.domain.model.AnalysisResult
import com.kevcoder.carbcalculator.domain.model.FoodItem
import com.kevcoder.carbcalculator.domain.model.SubmissionLog
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SubmissionLogRepository @Inject constructor(
    private val dao: SubmissionLogDao,
    private val moshi: Moshi,
) {
    data class FoodItemJson(val name: String, val estimatedCarbs: Float)

    private val foodItemListType = Types.newParameterizedType(List::class.java, FoodItemJson::class.java)
    private val foodItemJsonAdapter = moshi.adapter<List<FoodItemJson>>(foodItemListType)

    /** Call before the API request fires. Returns the new submission log row ID. */
    suspend fun logRequest(
        imagePath: String?,
        imageSizeBytes: Long?,
        foodDescription: String?,
    ): Long = withContext(Dispatchers.IO) {
        dao.insert(
            SubmissionLogEntity(
                requestTimestamp = System.currentTimeMillis(),
                imagePath = imagePath,
                imageSizeBytes = imageSizeBytes,
                foodDescription = foodDescription,
                status = "pending",
                foodItemsJson = null,
                totalCarbs = null,
                errorMessage = null,
                responseTimestamp = null,
                savedLogId = null,
                requestHeaders = null,
                responseHeaders = null,
                responseBody = null,
            )
        )
    }

    /** Call after a successful API response. */
    suspend fun logSuccess(
        submissionId: Long,
        result: AnalysisResult,
        requestHeaders: String?,
        responseHeaders: String?,
        responseBody: String?,
    ) = withContext(Dispatchers.IO) {
        val json = foodItemJsonAdapter.toJson(
            result.items.map { FoodItemJson(it.name, it.estimatedCarbs) }
        )
        dao.updateWithSuccess(
            id = submissionId,
            foodItemsJson = json,
            totalCarbs = result.totalCarbs,
            responseTimestamp = System.currentTimeMillis(),
            requestHeaders = requestHeaders,
            responseHeaders = responseHeaders,
            responseBody = responseBody,
        )
    }

    /** Call when the API request fails. */
    suspend fun logError(
        submissionId: Long,
        errorMessage: String,
        requestHeaders: String?,
        responseHeaders: String?,
        responseBody: String?,
    ) = withContext(Dispatchers.IO) {
        dao.updateWithError(
            id = submissionId,
            errorMessage = errorMessage,
            responseTimestamp = System.currentTimeMillis(),
            requestHeaders = requestHeaders,
            responseHeaders = responseHeaders,
            responseBody = responseBody,
        )
    }

    /** Call when the user explicitly saves a result to history. */
    suspend fun markAsSaved(submissionId: Long, savedLogId: Long) = withContext(Dispatchers.IO) {
        dao.markAsSaved(submissionId, savedLogId)
    }

    fun getAll(): Flow<List<SubmissionLog>> = dao.getAllLogs().map { entities ->
        entities.map { it.toDomain() }
    }

    suspend fun deleteAll() = withContext(Dispatchers.IO) {
        dao.deleteAll()
    }

    suspend fun purgeOlderThan(cutoffMs: Long) = withContext(Dispatchers.IO) {
        dao.deleteOlderThan(cutoffMs)
    }

    private fun SubmissionLogEntity.toDomain(): SubmissionLog {
        val items = foodItemsJson?.let { json ->
            try {
                foodItemJsonAdapter.fromJson(json)
                    ?.map { FoodItem(it.name, it.estimatedCarbs) }
                    ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            }
        } ?: emptyList()

        return SubmissionLog(
            id = id,
            requestTimestamp = requestTimestamp,
            imagePath = imagePath,
            imageSizeBytes = imageSizeBytes,
            foodDescription = foodDescription,
            status = status,
            items = items,
            totalCarbs = totalCarbs,
            errorMessage = errorMessage,
            responseTimestamp = responseTimestamp,
            savedLogId = savedLogId,
            requestHeaders = requestHeaders,
            responseHeaders = responseHeaders,
            responseBody = responseBody,
        )
    }
}
