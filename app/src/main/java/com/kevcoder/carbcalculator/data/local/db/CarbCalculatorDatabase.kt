package com.kevcoder.carbcalculator.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [CarbLogEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class CarbCalculatorDatabase : RoomDatabase() {
    abstract fun carbLogDao(): CarbLogDao

    companion object {
        const val DATABASE_NAME = "carb_calculator.db"
    }
}
