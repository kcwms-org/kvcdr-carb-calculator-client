package com.kevcoder.carbcalculator.data.repository

import com.kevcoder.carbcalculator.auth.dexcom.DexcomTokenManager
import com.kevcoder.carbcalculator.data.local.datastore.AppPreferencesDataStore
import com.kevcoder.carbcalculator.data.remote.dexcom.DexcomApiService
import com.kevcoder.carbcalculator.domain.model.GlucoseReading
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DexcomRepository @Inject constructor(
    private val dexcomApiService: DexcomApiService,
    private val tokenManager: DexcomTokenManager,
    private val dataStore: AppPreferencesDataStore,
) {
    private val isoFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
        .withZone(ZoneOffset.UTC)

    fun isConnected(): Boolean = tokenManager.isConnected()

    /**
     * Fetches the most recent glucose reading (within last 10 minutes).
     * Returns null if not connected or no data available.
     */
    suspend fun getLatestGlucose(): GlucoseReading? {
        if (!tokenManager.isConnected()) return null
        return try {
            val now = Instant.now()
            val tenMinutesAgo = now.minusSeconds(600)
            val response = dexcomApiService.getEgvs(
                startDate = isoFormatter.format(tenMinutesAgo),
                endDate = isoFormatter.format(now),
            )
            response.egvs.lastOrNull()?.let { egv ->
                GlucoseReading(
                    mgDl = egv.value,
                    timestamp = Instant.parse(egv.systemTime).toEpochMilli(),
                )
            }
        } catch (e: Exception) {
            null
        }
    }

    suspend fun disconnectDexcom() = tokenManager.clearTokens()

    suspend fun exchangeCode(code: String) {
        val useSandbox = dataStore.dexcomEnv.first() == AppPreferencesDataStore.DEXCOM_ENV_SANDBOX
        tokenManager.exchangeCode(code, useSandbox)
    }

    fun buildAuthUrl(): String {
        val useSandbox = false // synchronous; real env read happens in ViewModel
        return tokenManager.buildAuthUrl(useSandbox)
    }

    suspend fun buildAuthUrlSuspend(): String {
        val useSandbox = dataStore.dexcomEnv.first() == AppPreferencesDataStore.DEXCOM_ENV_SANDBOX
        return tokenManager.buildAuthUrl(useSandbox)
    }
}
