package com.kevcoder.carbcalculator.workers

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.kevcoder.carbcalculator.data.local.datastore.AppPreferencesDataStore
import com.kevcoder.carbcalculator.data.repository.SubmissionLogRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

@HiltWorker
class SubmissionPurgeWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val submissionLogRepository: SubmissionLogRepository,
    private val dataStore: AppPreferencesDataStore,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val interval = dataStore.submissionPurgeInterval.first()
        val cutoffMs = cutoffMillis(interval) ?: return Result.success() // "never" = no-op
        submissionLogRepository.purgeOlderThan(cutoffMs)
        return Result.success()
    }

    private fun cutoffMillis(interval: String): Long? {
        val now = System.currentTimeMillis()
        return when (interval) {
            AppPreferencesDataStore.PURGE_HOURLY -> now - 60 * 60 * 1000L
            AppPreferencesDataStore.PURGE_DAILY -> now - 24 * 60 * 60 * 1000L
            AppPreferencesDataStore.PURGE_WEEKLY -> now - 7 * 24 * 60 * 60 * 1000L
            AppPreferencesDataStore.PURGE_MONTHLY -> now - 30 * 24 * 60 * 60 * 1000L
            else -> null
        }
    }
}
