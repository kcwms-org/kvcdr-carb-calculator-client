package com.kevcoder.carbcalculator.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SubmissionLogDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(log: SubmissionLogEntity): Long

    @Query("SELECT * FROM submission_logs WHERE carbLogId = :carbLogId ORDER BY requestTimestamp DESC")
    fun getByParentId(carbLogId: Long): Flow<List<SubmissionLogEntity>>

    @Query("DELETE FROM submission_logs WHERE requestTimestamp < :cutoffTimestamp")
    suspend fun deleteOlderThan(cutoffTimestamp: Long)
}
