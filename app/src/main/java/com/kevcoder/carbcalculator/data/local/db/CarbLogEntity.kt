package com.kevcoder.carbcalculator.data.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "carb_logs")
data class CarbLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long,
    val foodDescription: String?,
    /** JSON-serialized List<FoodItemJson> */
    val foodItemsJson: String,
    val totalCarbs: Float,
    /** Absolute path to saved JPEG in filesDir/thumbnails/ */
    val thumbnailPath: String?,
    val glucoseMgDl: Int?,
    val glucoseTimestamp: Long?,
)
