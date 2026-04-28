package com.kevcoder.carbcalculator.data.util

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class AppBackup(
    val version: Int = 1,
    val exportedAt: Long = System.currentTimeMillis(),
    val settings: SettingsSnapshot,
    val carbLogs: List<CarbLogSnapshot>,
)

@JsonClass(generateAdapter = true)
data class SettingsSnapshot(
    val carbApiUrl: String,
    val dexcomEnv: String,
    val imageQuality: Int,
    val submissionPurgeInterval: String,
    val saveImagesToDevice: Boolean,
    val expandSubmissionsDefault: Boolean,
)

@JsonClass(generateAdapter = true)
data class CarbLogSnapshot(
    val timestamp: Long,
    val foodDescription: String?,
    val foodItemsJson: String,
    val totalCarbs: Float,
    val glucoseMgDl: Int?,
    val glucoseTimestamp: Long?,
    val imageData: ByteArray?,
    val thumbnailPath: String?,
)
