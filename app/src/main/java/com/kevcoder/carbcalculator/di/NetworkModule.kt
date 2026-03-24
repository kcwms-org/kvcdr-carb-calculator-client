package com.kevcoder.carbcalculator.di

import com.kevcoder.carbcalculator.data.local.datastore.AppPreferencesDataStore
import com.kevcoder.carbcalculator.data.remote.carbapi.CarbApiService
import com.kevcoder.carbcalculator.data.remote.dexcom.DexcomApiService
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideMoshi(): Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    @Provides
    @Singleton
    fun provideLoggingInterceptor(): HttpLoggingInterceptor =
        HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

    @Provides
    @Singleton
    @Named("carb")
    fun provideCarbOkHttpClient(logging: HttpLoggingInterceptor): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(logging)
            .build()

    /**
     * The carb API Retrofit instance uses a dynamic base URL interceptor.
     * The actual URL is read from DataStore at request time, so changing the URL
     * in Settings takes effect on the very next call without recreating Retrofit.
     */
    @Provides
    @Singleton
    fun provideCarbApiService(
        @Named("carb") client: OkHttpClient,
        moshi: Moshi,
        dataStore: AppPreferencesDataStore,
    ): CarbApiService {
        val dynamicUrlInterceptor = Interceptor { chain ->
            val baseUrl = runBlocking { dataStore.carbApiUrl.first() }
                .trimEnd('/')
            val original = chain.request()
            val newUrl = original.url.newBuilder()
                .scheme(baseUrl.substringBefore("://"))
                .host(baseUrl.substringAfter("://").substringBefore(":").substringBefore("/"))
                .apply {
                    val port = baseUrl.substringAfter("://").substringAfter(":", "").substringBefore("/")
                    if (port.isNotEmpty()) port(port.toInt())
                }
                .build()
            chain.proceed(original.newBuilder().url(newUrl).build())
        }

        val dynamicClient = client.newBuilder()
            .addInterceptor(dynamicUrlInterceptor)
            .build()

        // Placeholder base URL — the interceptor above rewrites it at runtime.
        return Retrofit.Builder()
            .baseUrl("http://localhost:3000/")
            .client(dynamicClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(CarbApiService::class.java)
    }

    @Provides
    @Singleton
    @Named("dexcom")
    fun provideDexcomOkHttpClient(
        logging: HttpLoggingInterceptor,
        dataStore: AppPreferencesDataStore,
        tokenManagerProvider: dagger.Lazy<com.kevcoder.carbcalculator.auth.dexcom.DexcomTokenManager>,
    ): OkHttpClient {
        val authInterceptor = Interceptor { chain ->
            val useSandbox = runBlocking {
                dataStore.dexcomEnv.first()
            } == AppPreferencesDataStore.DEXCOM_ENV_SANDBOX
            val token = runBlocking { tokenManagerProvider.get().getValidToken(useSandbox) }
            val request = if (token != null) {
                chain.request().newBuilder()
                    .addHeader("Authorization", "Bearer $token")
                    .build()
            } else {
                chain.request()
            }
            chain.proceed(request)
        }
        return OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(logging)
            .build()
    }

    @Provides
    @Singleton
    fun provideDexcomApiService(
        @Named("dexcom") client: OkHttpClient,
        moshi: Moshi,
        dataStore: AppPreferencesDataStore,
    ): DexcomApiService {
        val dynamicBaseUrlInterceptor = Interceptor { chain ->
            val useSandbox = runBlocking {
                dataStore.dexcomEnv.first()
            } == AppPreferencesDataStore.DEXCOM_ENV_SANDBOX
            val baseUrl = if (useSandbox) {
                "https://sandbox-api.dexcom.com"
            } else {
                "https://api.dexcom.com"
            }
            val original = chain.request()
            val newUrl = original.url.newBuilder()
                .scheme("https")
                .host(baseUrl.removePrefix("https://"))
                .build()
            chain.proceed(original.newBuilder().url(newUrl).build())
        }
        val dexcomClient = client.newBuilder()
            .addInterceptor(dynamicBaseUrlInterceptor)
            .build()
        return Retrofit.Builder()
            .baseUrl("https://api.dexcom.com/")
            .client(dexcomClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(DexcomApiService::class.java)
    }
}
