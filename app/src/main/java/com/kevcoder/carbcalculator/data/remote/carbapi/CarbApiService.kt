package com.kevcoder.carbcalculator.data.remote.carbapi

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path

interface CarbApiService {

    @GET("presign")
    suspend fun presign(): PresignResponse

    @Multipart
    @POST("analyze")
    suspend fun analyze(
        @Part image: MultipartBody.Part? = null,
        @Part("image_url") imageUrl: RequestBody? = null,
        @Part("text") text: RequestBody? = null,
    ): AnalysisResponse

    @DELETE("upload/{key}")
    suspend fun deleteUpload(
        @Path("key") key: String,
    )
}
