package com.kevcoder.carbcalculator.data.repository

import android.content.Context
import com.kevcoder.carbcalculator.data.local.db.CarbLogDao
import com.kevcoder.carbcalculator.data.local.db.CarbLogEntity
import com.kevcoder.carbcalculator.data.remote.carbapi.CarbApiCapture
import com.kevcoder.carbcalculator.data.remote.carbapi.CarbApiService
import com.kevcoder.carbcalculator.domain.model.AnalysisResult
import com.kevcoder.carbcalculator.domain.model.CarbLog
import com.kevcoder.carbcalculator.domain.model.FoodItem
import com.kevcoder.carbcalculator.domain.model.GlucoseReading
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CarbRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val carbApiService: CarbApiService,
    private val carbApiCapture: CarbApiCapture,
    private val carbLogDao: CarbLogDao,
    private val moshi: Moshi,
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

    suspend fun analyzeFood(imageFile: File, description: String?): AnalyzeFoodResult =
        withContext(Dispatchers.IO) {
            carbApiCapture.clear()
            val imagePart = MultipartBody.Part.createFormData(
                name = "image",
                filename = imageFile.name,
                body = imageFile.asRequestBody("image/jpeg".toMediaType()),
            )
            val descriptionBody = description?.toRequestBody("text/plain".toMediaType())
            val response = carbApiService.analyze(imagePart, descriptionBody)
            AnalyzeFoodResult(
                analysisResult = AnalysisResult(
                    items = response.items.map { FoodItem(it.name, it.estimatedCarbs) },
                    totalCarbs = response.totalCarbs,
                    foodDescription = description,
                    imagePath = imageFile.absolutePath,
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
}
