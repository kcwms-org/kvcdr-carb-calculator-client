package com.kevcoder.carbcalculator.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SubmissionLogDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(log: SubmissionLogEntity): Long

    @Query("SELECT * FROM submission_logs ORDER BY requestTimestamp DESC")
    fun getAllLogs(): Flow<List<SubmissionLogEntity>>

    @Query(
        """
        UPDATE submission_logs
        SET status = 'success',
            foodItemsJson = :foodItemsJson,
            totalCarbs = :totalCarbs,
            responseTimestamp = :responseTimestamp,
            requestHeaders = :requestHeaders,
            responseHeaders = :responseHeaders,
            responseBody = :responseBody
        WHERE id = :id
        """
    )
    suspend fun updateWithSuccess(
        id: Long,
        foodItemsJson: String,
        totalCarbs: Float,
        responseTimestamp: Long,
        requestHeaders: String?,
        responseHeaders: String?,
        responseBody: String?,
    )

    @Query(
        """
        UPDATE submission_logs
        SET status = 'error',
            errorMessage = :errorMessage,
            responseTimestamp = :responseTimestamp,
            requestHeaders = :requestHeaders,
            responseHeaders = :responseHeaders,
            responseBody = :responseBody
        WHERE id = :id
        """
    )
    suspend fun updateWithError(
        id: Long,
        errorMessage: String,
        responseTimestamp: Long,
        requestHeaders: String?,
        responseHeaders: String?,
        responseBody: String?,
    )

    @Query("UPDATE submission_logs SET savedLogId = :savedId WHERE id = :submissionId")
    suspend fun markAsSaved(submissionId: Long, savedId: Long)

    @Query("DELETE FROM submission_logs")
    suspend fun deleteAll()

    @Query("DELETE FROM submission_logs WHERE requestTimestamp < :cutoffTimestamp")
    suspend fun deleteOlderThan(cutoffTimestamp: Long)
}
