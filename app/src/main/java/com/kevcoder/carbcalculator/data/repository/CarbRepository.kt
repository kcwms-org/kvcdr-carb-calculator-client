package com.kevcoder.carbcalculator.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.kevcoder.carbcalculator.data.local.db.CarbLogDao
import com.kevcoder.carbcalculator.data.local.db.CarbLogEntity
import com.kevcoder.carbcalculator.data.remote.carbapi.CarbApiCapture
import com.kevcoder.carbcalculator.data.remote.carbapi.CarbApiService
import com.kevcoder.carbcalculator.di.ApplicationScope
import com.kevcoder.carbcalculator.domain.model.AnalysisResult
import com.kevcoder.carbcalculator.domain.model.CarbLog
import com.kevcoder.carbcalculator.domain.model.FoodItem
import com.kevcoder.carbcalculator.domain.model.GlucoseReading
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.io.File
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
class CarbRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val carbApiService: CarbApiService,
    private val carbApiCapture: CarbApiCapture,
    private val carbLogDao: CarbLogDao,
    private val moshi: Moshi,
    @Named("storage") private val storageHttpClient: OkHttpClient,
    @ApplicationScope private val applicationScope: CoroutineScope,
) {
    private val foodItemListType = Types.newParameterizedType(List::class.java, FoodItemJson::class.java)
    private val foodItemJsonAdapter = moshi.adapter<List<FoodItemJson>>(foodItemListType)

    data class FoodItemJson(val name: String, val estimatedCarbs: Float)

    data class AnalyzeFoodResult(
        val analysisResult: AnalysisResult,
        val requestHeaders: String?,
        val responseHeaders: String?,
        val responseBody: String?,
    )

    suspend fun analyzeFood(imageFile: File?, description: String?, imageQuality: Int = 80): AnalyzeFoodResult =
        withContext(Dispatchers.IO) {
            carbApiCapture.clear()

            val textBody = description?.toRequestBody("text/plain".toMediaType())

            val response = if (imageFile != null) {
                val imageBytes = compressImage(imageFile, imageQuality.coerceIn(1, 100))

                // Step 1: get presigned upload URL
                val presign = carbApiService.presign()

                // Step 2: PUT image directly to object storage
                val putRequest = Request.Builder()
                    .url(presign.uploadUrl)
                    .apply { presign.requiredHeaders.forEach { (k, v) -> addHeader(k, v) } }
                    .put(imageBytes.toRequestBody("image/jpeg".toMediaType()))
                    .build()

                val putResponse = storageHttpClient.newCall(putRequest).execute()
                if (!putResponse.isSuccessful) {
                    val code = putResponse.code
                    putResponse.close()
                    error("Storage upload failed: HTTP $code")
                }
                putResponse.close()

                // Step 3: analyze via URL
                val imageUrlBody = presign.imageUrl.toRequestBody("text/plain".toMediaType())
                val result = carbApiService.analyze(image = null, imageUrl = imageUrlBody, text = textBody)

                // Step 4: fire-and-forget cleanup
                val key = presign.key
                applicationScope.launch {
                    try { carbApiService.deleteUpload(key) } catch (_: Exception) {}
                }

                result
            } else {
                // Text-only: skip presign/PUT, send description directly
                carbApiService.analyze(image = null, imageUrl = null, text = textBody)
            }

            AnalyzeFoodResult(
                analysisResult = AnalysisResult(
                    items = response.items.map { FoodItem(it.name, it.carbsGrams) },
                    totalCarbs = response.totalCarbsGrams,
                    foodDescription = description,
                    imagePath = imageFile?.absolutePath,
                ),
                requestHeaders = carbApiCapture.requestHeaders,
                responseHeaders = carbApiCapture.responseHeaders,
                responseBody = carbApiCapture.responseBody,
            )
        }

    suspend fun saveLog(
        result: AnalysisResult,
        glucose: GlucoseReading?,
    ): Long = withContext(Dispatchers.IO) {
        val thumbnailPath = result.imagePath?.let { srcPath ->
            val src = File(srcPath)
            if (src.exists()) {
                val thumbnailsDir = File(context.filesDir, "thumbnails").also { it.mkdirs() }
                val dest = File(thumbnailsDir, "${System.currentTimeMillis()}.jpg")
                src.copyTo(dest, overwrite = true)
                dest.absolutePath
            } else null
        }

        val foodItemsJson = foodItemJsonAdapter.toJson(
            result.items.map { FoodItemJson(it.name, it.estimatedCarbs) }
        )
        val entity = CarbLogEntity(
            timestamp = System.currentTimeMillis(),
            foodDescription = result.foodDescription,
            foodItemsJson = foodItemsJson,
            totalCarbs = result.totalCarbs,
            thumbnailPath = thumbnailPath,
            glucoseMgDl = glucose?.mgDl,
            glucoseTimestamp = glucose?.timestamp,
        )
        carbLogDao.insert(entity)
    }

    fun getLogs(): Flow<List<CarbLog>> = carbLogDao.getAllLogs().map { entities ->
        entities.map { entity ->
            val items = try {
                foodItemJsonAdapter.fromJson(entity.foodItemsJson)
                    ?.map { FoodItem(it.name, it.estimatedCarbs) }
                    ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            }
            CarbLog(
                id = entity.id,
                timestamp = entity.timestamp,
                foodDescription = entity.foodDescription,
                items = items,
                totalCarbs = entity.totalCarbs,
                thumbnailPath = entity.thumbnailPath,
                glucose = if (entity.glucoseMgDl != null && entity.glucoseTimestamp != null) {
                    GlucoseReading(entity.glucoseMgDl, entity.glucoseTimestamp)
                } else null,
            )
        }
    }

    suspend fun deleteLog(id: Long) = withContext(Dispatchers.IO) {
        carbLogDao.getLogById(id)?.thumbnailPath?.let { File(it).delete() }
        carbLogDao.deleteLog(id)
    }

    private fun compressImage(file: File, quality: Int): ByteArray {
        return try {
            val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                ?: return file.readBytes()
            ByteArrayOutputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
                bitmap.recycle()
                out.toByteArray()
            }
        } catch (_: Exception) {
            file.readBytes()
        }
    }
}
