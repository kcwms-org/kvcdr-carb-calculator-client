package com.kevcoder.carbcalculator.data.remote.carbapi

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class FoodItemResponse(
    @Json(name = "name") val name: String,
    @Json(name = "estimated_carbs") val estimatedCarbs: Float,
)

@JsonClass(generateAdapter = true)
data class AnalysisResponse(
    @Json(name = "items") val items: List<FoodItemResponse>,
    @Json(name = "total_carbs") val totalCarbs: Float,
)
