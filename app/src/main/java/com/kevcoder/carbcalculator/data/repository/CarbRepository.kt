package com.kevcoder.carbcalculator.data.repository

import android.content.Context
import android.graphics.BitmapFactory
import android.os.Build
import android.provider.MediaStore
import com.kevcoder.carbcalculator.data.local.db.CarbLogDao
import com.kevcoder.carbcalculator.data.local.db.CarbLogEntity
import com.kevcoder.carbcalculator.data.remote.carbapi.CarbApiCapture
import com.kevcoder.carbcalculator.data.remote.carbapi.CarbApiService
import com.kevcoder.carbcalculator.data.util.ImageProcessor
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
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton
import android.util.Log

@Singleton
class CarbRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val carbApiService: CarbApiService,
    private val carbApiCapture: CarbApiCapture,
    private val carbLogDao: CarbLogDao,
    private val submissionLogRepository: SubmissionLogRepository,
    private val moshi: Moshi,
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

    suspend fun analyzeFood(
        imageFile: File?,
        description: String?,
        imageQuality: Int = 80,
        datetime: OffsetDateTime? = null,
    ): AnalyzeFoodResult =
        withContext(Dispatchers.IO) {
            carbApiCapture.clear()

            val textBody = description?.toRequestBody("text/plain".toMediaType())
            val datetimeStr = (datetime ?: OffsetDateTime.now()).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
            val datetimeBody = datetimeStr.toRequestBody("text/plain".toMediaType())

            val imagePart = imageFile?.let { file ->
                val bytes = ImageProcessor.processForUpload(
                    file = file,
                    quality = imageQuality.coerceIn(1, 100),
                )
                if (bytes.size > MAX_UPLOAD_BYTES) {
                    error("Compressed image is ${bytes.size} bytes, exceeding $MAX_UPLOAD_BYTES byte limit")
                }
                Log.d("CarbRepository", "Uploading image: ${bytes.size} bytes")
                MultipartBody.Part.createFormData(
                    "image",
                    "capture.jpg",
                    bytes.toRequestBody("image/jpeg".toMediaType()),
                )
            }

            val response = carbApiService.analyze(
                image = imagePart,
                text = textBody,
                datetime = datetimeBody,
            )

            val imageData = response.images?.firstOrNull()?.data?.let {
                try {
                    Base64.getDecoder().decode(it)
                } catch (e: Exception) {
                    Log.e("CarbRepository", "Failed to decode base64 image", e)
                    null
                }
            }

            val responseDateTime = response.datetime?.let {
                try {
                    java.time.Instant.parse(it).toEpochMilli()
                } catch (e: Exception) {
                    Log.e("CarbRepository", "Failed to parse datetime", e)
                    null
                }
            }

            AnalyzeFoodResult(
                analysisResult = AnalysisResult(
                    items = response.items.map { FoodItem(it.name, it.carbsGrams) },
                    totalCarbs = response.totalCarbsGrams,
                    foodDescription = description,
                    imagePath = imageFile?.absolutePath,
                    imageData = imageData,
                    datetime = responseDateTime,
                ),
                requestHeaders = carbApiCapture.requestHeaders,
                responseHeaders = carbApiCapture.responseHeaders,
                responseBody = carbApiCapture.responseBody,
            )
        }

    suspend fun saveLog(
        result: AnalysisResult,
        glucose: GlucoseReading?,
        saveImagesToDevice: Boolean = false,
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

        if (saveImagesToDevice && result.imageData != null) {
            applicationScope.launch {
                try {
                    saveImageToGallery(context, result.imageData)
                } catch (e: Exception) {
                    Log.e("CarbRepository", "Failed to save image to gallery", e)
                }
            }
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
            imageData = result.imageData,
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
                imageData = entity.imageData,
            )
        }
    }

    suspend fun deleteLog(id: Long) = withContext(Dispatchers.IO) {
        carbLogDao.getLogById(id)?.thumbnailPath?.let { File(it).delete() }
        submissionLogRepository.deleteByParentId(id)
        carbLogDao.deleteLog(id)
    }

    private suspend fun saveImageToGallery(context: Context, imageData: ByteArray) {
        withContext(Dispatchers.IO) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val contentValues = android.content.ContentValues().apply {
                        put(MediaStore.Images.Media.DISPLAY_NAME, "carb-${System.currentTimeMillis()}.jpg")
                        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                        put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/CarbCalculator")
                    }
                    val uri = context.contentResolver.insert(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        contentValues
                    ) ?: return@withContext
                    context.contentResolver.openOutputStream(uri)?.use { stream ->
                        stream.write(imageData)
                    }
                } else {
                    @Suppress("DEPRECATION")
                    MediaStore.Images.Media.insertImage(
                        context.contentResolver,
                        BitmapFactory.decodeByteArray(imageData, 0, imageData.size),
                        "carb-${System.currentTimeMillis()}",
                        "Carb Calculator food image",
                    )
                }
            } catch (e: Exception) {
                Log.e("CarbRepository", "saveImageToGallery failed", e)
            }
        }
    }

    companion object {
        private const val MAX_UPLOAD_BYTES = 900_000
    }
}
