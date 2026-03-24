package com.kevcoder.carbcalculator.auth.dexcom

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages Dexcom OAuth2 tokens using EncryptedSharedPreferences.
 *
 * NOTE: Dexcom policy recommends storing tokens on a backend server rather than the
 * mobile device. This implementation stores tokens on-device for a client-only app.
 * Revisit with a companion backend proxy if policy enforcement becomes a concern.
 */
@Singleton
class DexcomTokenManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        private const val PREFS_FILE = "dexcom_tokens"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_EXPIRES_AT = "expires_at"

        // Register your app at developer.dexcom.com and populate these.
        // In a real project these would come from BuildConfig / secrets management.
        const val CLIENT_ID = "YOUR_DEXCOM_CLIENT_ID"
        const val CLIENT_SECRET = "YOUR_DEXCOM_CLIENT_SECRET"
        const val REDIRECT_URI = "kvcdr-carb://oauth2callback"

        const val PRODUCTION_TOKEN_URL = "https://api.dexcom.com/v3/oauth2/token"
        const val SANDBOX_TOKEN_URL = "https://sandbox-api.dexcom.com/v3/oauth2/token"
        const val PRODUCTION_AUTH_URL = "https://api.dexcom.com/v3/oauth2/login"
        const val SANDBOX_AUTH_URL = "https://sandbox-api.dexcom.com/v3/oauth2/login"

        // Buffer: refresh token 60 s before actual expiry
        private const val EXPIRY_BUFFER_MS = 60_000L
    }

    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            PREFS_FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    private val httpClient = OkHttpClient()

    fun isConnected(): Boolean = prefs.getString(KEY_REFRESH_TOKEN, null) != null

    fun buildAuthUrl(useSandbox: Boolean): String {
        val base = if (useSandbox) SANDBOX_AUTH_URL else PRODUCTION_AUTH_URL
        return "$base?client_id=$CLIENT_ID&redirect_uri=$REDIRECT_URI&response_type=code&scope=offline_access"
    }

    /** Exchange authorization code for tokens. Call from MainActivity after redirect. */
    suspend fun exchangeCode(code: String, useSandbox: Boolean) = withContext(Dispatchers.IO) {
        val tokenUrl = if (useSandbox) SANDBOX_TOKEN_URL else PRODUCTION_TOKEN_URL
        val body = FormBody.Builder()
            .add("grant_type", "authorization_code")
            .add("code", code)
            .add("redirect_uri", REDIRECT_URI)
            .add("client_id", CLIENT_ID)
            .add("client_secret", CLIENT_SECRET)
            .build()
        val request = Request.Builder().url(tokenUrl).post(body).build()
        val response = httpClient.newCall(request).execute()
        if (response.isSuccessful) {
            val json = JSONObject(response.body!!.string())
            saveTokens(
                accessToken = json.getString("access_token"),
                refreshToken = json.getString("refresh_token"),
                expiresIn = json.getLong("expires_in"),
            )
        } else {
            throw IllegalStateException("Token exchange failed: ${response.code}")
        }
    }

    /**
     * Returns a valid access token, refreshing it first if expired.
     * Returns null if not connected.
     */
    suspend fun getValidToken(useSandbox: Boolean): String? = withContext(Dispatchers.IO) {
        val accessToken = prefs.getString(KEY_ACCESS_TOKEN, null) ?: return@withContext null
        val expiresAt = prefs.getLong(KEY_EXPIRES_AT, 0L)
        if (System.currentTimeMillis() < expiresAt - EXPIRY_BUFFER_MS) {
            return@withContext accessToken
        }
        // Token expired — refresh
        val refreshToken = prefs.getString(KEY_REFRESH_TOKEN, null) ?: return@withContext null
        val tokenUrl = if (useSandbox) SANDBOX_TOKEN_URL else PRODUCTION_TOKEN_URL
        val body = FormBody.Builder()
            .add("grant_type", "refresh_token")
            .add("refresh_token", refreshToken)
            .add("client_id", CLIENT_ID)
            .add("client_secret", CLIENT_SECRET)
            .build()
        val request = Request.Builder().url(tokenUrl).post(body).build()
        val response = httpClient.newCall(request).execute()
        return@withContext if (response.isSuccessful) {
            val json = JSONObject(response.body!!.string())
            saveTokens(
                accessToken = json.getString("access_token"),
                refreshToken = json.getString("refresh_token"),
                expiresIn = json.getLong("expires_in"),
            )
            json.getString("access_token")
        } else {
            clearTokens()
            null
        }
    }

    fun clearTokens() {
        prefs.edit()
            .remove(KEY_ACCESS_TOKEN)
            .remove(KEY_REFRESH_TOKEN)
            .remove(KEY_EXPIRES_AT)
            .apply()
    }

    private fun saveTokens(accessToken: String, refreshToken: String, expiresIn: Long) {
        prefs.edit()
            .putString(KEY_ACCESS_TOKEN, accessToken)
            .putString(KEY_REFRESH_TOKEN, refreshToken)
            .putLong(KEY_EXPIRES_AT, System.currentTimeMillis() + expiresIn * 1000)
            .apply()
    }
}
