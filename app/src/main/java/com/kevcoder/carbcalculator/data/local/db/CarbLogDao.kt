package com.kevcoder.carbcalculator.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CarbLogDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: CarbLogEntity): Long

    @Query("SELECT * FROM carb_logs ORDER BY timestamp DESC")
    fun getAllLogs(): Flow<List<CarbLogEntity>>

    @Query("SELECT * FROM carb_logs WHERE id = :id LIMIT 1")
    suspend fun getLogById(id: Long): CarbLogEntity?

    @Query("DELETE FROM carb_logs WHERE id = :id")
    suspend fun deleteLog(id: Long)
}
