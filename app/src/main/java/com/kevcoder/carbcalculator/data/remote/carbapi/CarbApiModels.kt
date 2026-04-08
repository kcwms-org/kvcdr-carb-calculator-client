package com.kevcoder.carbcalculator.data.remote.carbapi

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class FoodItemResponse(
    @Json(name = "name")       val name: String,
    @Json(name = "carbs_grams") val carbsGrams: Float,
    @Json(name = "confidence") val confidence: String? = null,
    @Json(name = "notes")      val notes: String? = null,
)

@JsonClass(generateAdapter = true)
data class ImageDataResponse(
    @Json(name = "data")      val data: String,
    @Json(name = "mime_type") val mimeType: String,
)

@JsonClass(generateAdapter = true)
data class AnalysisResponse(
    @Json(name = "items")            val items: List<FoodItemResponse>,
    @Json(name = "total_carbs_grams") val totalCarbsGrams: Float,
    @Json(name = "engine_used")      val engineUsed: String? = null,
    @Json(name = "cached")           val cached: Boolean? = null,
    @Json(name = "datetime")         val datetime: String? = null,
    @Json(name = "images")           val images: List<ImageDataResponse>? = null,
)

@JsonClass(generateAdapter = true)
data class PresignResponse(
    @Json(name = "upload_url")       val uploadUrl: String,
    @Json(name = "image_url")        val imageUrl: String,
    @Json(name = "key")              val key: String,
    @Json(name = "required_headers") val requiredHeaders: Map<String, String>,
)
