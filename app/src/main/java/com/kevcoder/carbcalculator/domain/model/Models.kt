package com.kevcoder.carbcalculator.domain.model

data class FoodItem(
    val name: String,
    val estimatedCarbs: Float,
)

data class AnalysisResult(
    val items: List<FoodItem>,
    val totalCarbs: Float,
    val foodDescription: String?,
    /** Temp file path from camera capture — not yet persisted */
    val imagePath: String?,
)

data class GlucoseReading(
    val mgDl: Int,
    val timestamp: Long,
)

data class CarbLog(
    val id: Long,
    val timestamp: Long,
    val foodDescription: String?,
    val items: List<FoodItem>,
    val totalCarbs: Float,
    val thumbnailPath: String?,
    val glucose: GlucoseReading?,
)

data class SubmissionLog(
    val id: Long,
    val requestTimestamp: Long,
    val imagePath: String?,
    val imageSizeBytes: Long?,
    val foodDescription: String?,
    /** "pending" | "success" | "error" */
    val status: String,
    val items: List<FoodItem>,
    val totalCarbs: Float?,
    val errorMessage: String?,
    val responseTimestamp: Long?,
    /** Non-null if user explicitly saved this result to history */
    val savedLogId: Long?,
)

data class AppSettings(
    val carbApiUrl: String,
    val dexcomEnv: String,
    val submissionPurgeInterval: String,
)
