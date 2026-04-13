package com.kevcoder.carbcalculator.data.repository

import com.kevcoder.carbcalculator.data.local.db.SubmissionLogDao
import com.kevcoder.carbcalculator.data.local.db.SubmissionLogEntity
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

    /** Insert a submission log row. carbLogId may be null for orphaned error logs. */
    suspend fun logRequest(
        carbLogId: Long?,
        imagePath: String?,
        imageSizeBytes: Long?,
        foodDescription: String?,
        status: String,
        foodItemsJson: String?,
        totalCarbs: Float?,
        errorMessage: String?,
        responseTimestamp: Long?,
        requestHeaders: String?,
        responseHeaders: String?,
        responseBody: String?,
    ): Long = withContext(Dispatchers.IO) {
        val httpStatusCode = responseHeaders?.let { extractHttpStatusCode(it) }
        dao.insert(
            SubmissionLogEntity(
                carbLogId = carbLogId,
                requestTimestamp = System.currentTimeMillis(),
                imagePath = imagePath,
                imageSizeBytes = imageSizeBytes,
                foodDescription = foodDescription,
                status = status,
                foodItemsJson = foodItemsJson,
                totalCarbs = totalCarbs,
                errorMessage = errorMessage,
                responseTimestamp = responseTimestamp,
                requestHeaders = requestHeaders,
                responseHeaders = responseHeaders,
                responseBody = responseBody,
                httpStatusCode = httpStatusCode,
            )
        )
    }

    fun getByParentId(carbLogId: Long): Flow<List<SubmissionLog>> =
        dao.getByParentId(carbLogId).map { entities -> entities.map { it.toDomain() } }

    fun getOrphanedErrorLogs(): Flow<List<SubmissionLog>> =
        dao.getOrphanedErrorLogs().map { entities -> entities.map { it.toDomain() } }

    suspend fun deleteSubmissionLog(id: Long) = withContext(Dispatchers.IO) {
        dao.deleteById(id)
    }

    suspend fun deleteByParentId(carbLogId: Long) = withContext(Dispatchers.IO) {
        dao.deleteByParentId(carbLogId)
    }

    suspend fun purgeOlderThan(cutoffMs: Long) = withContext(Dispatchers.IO) {
        dao.deleteOlderThan(cutoffMs)
    }

    private fun extractHttpStatusCode(responseHeaders: String): Int? {
        // Extract status code from response headers like "HTTP/1.1 403 Forbidden"
        return try {
            val statusLine = responseHeaders.lines().firstOrNull() ?: return null
            val parts = statusLine.split(" ")
            if (parts.size >= 2) parts[1].toIntOrNull() else null
        } catch (e: Exception) {
            null
        }
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
            carbLogId = carbLogId,
            requestTimestamp = requestTimestamp,
            imagePath = imagePath,
            imageSizeBytes = imageSizeBytes,
            foodDescription = foodDescription,
            status = status,
            items = items,
            totalCarbs = totalCarbs,
            errorMessage = errorMessage,
            responseTimestamp = responseTimestamp,
            requestHeaders = requestHeaders,
            responseHeaders = responseHeaders,
            responseBody = responseBody,
            httpStatusCode = httpStatusCode,
        )
    }
}
