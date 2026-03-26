package com.kevcoder.carbcalculator.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [CarbLogEntity::class, SubmissionLogEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class CarbCalculatorDatabase : RoomDatabase() {
    abstract fun carbLogDao(): CarbLogDao
    abstract fun submissionLogDao(): SubmissionLogDao

    companion object {
        const val DATABASE_NAME = "carb_calculator.db"

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS submission_logs (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        requestTimestamp INTEGER NOT NULL,
                        imagePath TEXT,
                        imageSizeBytes INTEGER,
                        foodDescription TEXT,
                        status TEXT NOT NULL,
                        foodItemsJson TEXT,
                        totalCarbs REAL,
                        errorMessage TEXT,
                        responseTimestamp INTEGER,
                        savedLogId INTEGER
                    )
                    """.trimIndent()
                )
            }
        }
    }
}
