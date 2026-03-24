package com.kevcoder.carbcalculator.data.remote.dexcom

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class EgvRecord(
    @Json(name = "systemTime") val systemTime: String,
    @Json(name = "displayTime") val displayTime: String,
    @Json(name = "value") val value: Int,
    @Json(name = "unit") val unit: String,
    @Json(name = "trend") val trend: String?,
    @Json(name = "trendRate") val trendRate: Float?,
)

@JsonClass(generateAdapter = true)
data class EgvsResponse(
    @Json(name = "egvs") val egvs: List<EgvRecord>,
)
