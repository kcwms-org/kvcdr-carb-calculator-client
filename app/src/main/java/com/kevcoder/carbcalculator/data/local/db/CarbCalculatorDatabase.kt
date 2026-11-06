package com.kevcoder.carbcalculator.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [CarbLogEntity::class, SubmissionLogEntity::class],
    version = 8,
    exportSchema = false,
)
abstract class CarbCalculatorDatabase : RoomDatabase() {
    abstract fun carbLogDao(): CarbLogDao
    abstract fun submissionLogDao(): SubmissionLogDao

    companion object {
        const val DATABASE_NAME = "carb_calculator.db"

        /** Chars kept per HTTP log column by [MIGRATION_7_8]. */
        private const val MAX_LOG_CHARS = 64_000

        /**
         * Shrinks oversized HTTP log columns (base64 images in responses, issue #58).
         * Pure SQL, so the large rows are never loaded into a cursor.
         */
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                listOf("requestHeaders", "responseHeaders", "responseBody").forEach { col ->
                    db.execSQL(
                        "UPDATE submission_logs SET $col = substr($col, 1, $MAX_LOG_CHARS) || " +
                            "'\n… [truncated by update]' WHERE length($col) > $MAX_LOG_CHARS",
                    )
                }
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE submission_logs ADD COLUMN http_status_code INTEGER")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Make carbLogId nullable to support orphaned error logs (errors before CarbLog is saved)
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS submission_logs_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        carbLogId INTEGER,
                        requestTimestamp INTEGER NOT NULL,
                        imagePath TEXT,
                        imageSizeBytes INTEGER,
                        foodDescription TEXT,
                        status TEXT NOT NULL,
                        foodItemsJson TEXT,
                        totalCarbs REAL,
                        errorMessage TEXT,
                        responseTimestamp INTEGER,
                        requestHeaders TEXT,
                        responseHeaders TEXT,
                        responseBody TEXT
                    )
                    """.trimIndent()
                )
                // Copy all existing rows (no filtering since we now allow null carbLogId)
                db.execSQL(
                    """
                    INSERT INTO submission_logs_new (
                        id, carbLogId, requestTimestamp, imagePath, imageSizeBytes,
                        foodDescription, status, foodItemsJson, totalCarbs, errorMessage,
                        responseTimestamp, requestHeaders, responseHeaders, responseBody
                    )
                    SELECT
                        id, carbLogId, requestTimestamp, imagePath, imageSizeBytes,
                        foodDescription, status, foodItemsJson, totalCarbs, errorMessage,
                        responseTimestamp, requestHeaders, responseHeaders, responseBody
                    FROM submission_logs
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE submission_logs")
                db.execSQL("ALTER TABLE submission_logs_new RENAME TO submission_logs")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_submission_logs_carbLogId ON submission_logs(carbLogId)")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE carb_logs ADD COLUMN image_data BLOB")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Create new table with carbLogId FK (NOT NULL) and CASCADE delete
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS submission_logs_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        carbLogId INTEGER NOT NULL,
                        requestTimestamp INTEGER NOT NULL,
                        imagePath TEXT,
                        imageSizeBytes INTEGER,
                        foodDescription TEXT,
                        status TEXT NOT NULL,
                        foodItemsJson TEXT,
                        totalCarbs REAL,
                        errorMessage TEXT,
                        responseTimestamp INTEGER,
                        requestHeaders TEXT,
                        responseHeaders TEXT,
                        responseBody TEXT,
                        FOREIGN KEY(carbLogId) REFERENCES carb_logs(id) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                // Copy only rows that have a valid parent (savedLogId IS NOT NULL)
                db.execSQL(
                    """
                    INSERT INTO submission_logs_new (
                        id, carbLogId, requestTimestamp, imagePath, imageSizeBytes,
                        foodDescription, status, foodItemsJson, totalCarbs, errorMessage,
                        responseTimestamp, requestHeaders, responseHeaders, responseBody
                    )
                    SELECT
                        id, savedLogId, requestTimestamp, imagePath, imageSizeBytes,
                        foodDescription, status, foodItemsJson, totalCarbs, errorMessage,
                        responseTimestamp, requestHeaders, responseHeaders, responseBody
                    FROM submission_logs
                    WHERE savedLogId IS NOT NULL
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE submission_logs")
                db.execSQL("ALTER TABLE submission_logs_new RENAME TO submission_logs")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_submission_logs_carbLogId ON submission_logs(carbLogId)")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE submission_logs ADD COLUMN requestHeaders TEXT")
                db.execSQL("ALTER TABLE submission_logs ADD COLUMN responseHeaders TEXT")
                db.execSQL("ALTER TABLE submission_logs ADD COLUMN responseBody TEXT")
            }
        }

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
