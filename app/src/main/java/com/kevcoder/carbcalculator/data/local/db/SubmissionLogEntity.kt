package com.kevcoder.carbcalculator.data.local.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "submission_logs",
    foreignKeys = [
        ForeignKey(
            entity = CarbLogEntity::class,
            parentColumns = ["id"],
            childColumns = ["carbLogId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("carbLogId")],
)
data class SubmissionLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    /** FK to carb_logs.id — set at insert time when the user saves */
    val carbLogId: Long,
    /** Epoch millis when Analyze was tapped */
    val requestTimestamp: Long,
    /** Absolute path to the image file at analysis time (may no longer exist) */
    val imagePath: String?,
    /** File size in bytes at analysis time */
    val imageSizeBytes: Long?,
    /** Optional user-typed description */
    val foodDescription: String?,
    /** "success" | "error" */
    val status: String,
    /** JSON-serialized List<FoodItemJson> — null on failure */
    val foodItemsJson: String?,
    /** Parsed total carbs — null on failure */
    val totalCarbs: Float?,
    /** Raw error message — null on success */
    val errorMessage: String?,
    /** Epoch millis when response was received */
    val responseTimestamp: Long?,
    /** Full POST request line + headers captured by the OkHttp interceptor */
    val requestHeaders: String?,
    /** Full response status line + headers */
    val responseHeaders: String?,
    /** Raw response body text */
    val responseBody: String?,
)
