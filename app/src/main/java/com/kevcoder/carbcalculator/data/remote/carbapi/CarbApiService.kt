package com.kevcoder.carbcalculator.data.remote.carbapi

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

interface CarbApiService {

    @Multipart
    @POST("analyze")
    suspend fun analyze(
        @Part image: MultipartBody.Part? = null,
        @Part("text") text: RequestBody? = null,
        @Part("datetime") datetime: RequestBody? = null,
    ): AnalysisResponse
}
