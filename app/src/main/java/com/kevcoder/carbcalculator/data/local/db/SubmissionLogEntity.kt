package com.kevcoder.carbcalculator.data.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "submission_logs")
data class SubmissionLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    /** Epoch millis when Analyze was tapped */
    val requestTimestamp: Long,
    /** Absolute path to the image file at analysis time (may no longer exist) */
    val imagePath: String?,
    /** File size in bytes at analysis time */
    val imageSizeBytes: Long?,
    /** Optional user-typed description */
    val foodDescription: String?,
    /** "pending" | "success" | "error" */
    val status: String,
    /** JSON-serialized List<FoodItemJson> — null on failure */
    val foodItemsJson: String?,
    /** Parsed total carbs — null on failure */
    val totalCarbs: Float?,
    /** Raw error message — null on success */
    val errorMessage: String?,
    /** Epoch millis when response was received */
    val responseTimestamp: Long?,
    /** FK to carb_logs.id if user explicitly saved — null otherwise */
    val savedLogId: Long?,
    /** Full POST request line + headers captured by the OkHttp interceptor */
    val requestHeaders: String?,
    /** Full response status line + headers */
    val responseHeaders: String?,
    /** Raw response body text */
    val responseBody: String?,
)
