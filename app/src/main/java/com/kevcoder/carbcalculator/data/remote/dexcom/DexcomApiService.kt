package com.kevcoder.carbcalculator.data.remote.dexcom

import retrofit2.http.GET
import retrofit2.http.Query

interface DexcomApiService {

    /**
     * Fetch estimated glucose values for a given time range.
     * Use a narrow window (e.g., last 10 minutes) to get the most recent reading.
     */
    @GET("v3/users/self/egvs")
    suspend fun getEgvs(
        @Query("startDate") startDate: String,
        @Query("endDate") endDate: String,
    ): EgvsResponse
}
