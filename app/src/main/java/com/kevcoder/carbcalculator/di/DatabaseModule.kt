package com.kevcoder.carbcalculator.di

import android.content.Context
import androidx.room.Room
import androidx.work.WorkManager
import com.kevcoder.carbcalculator.data.local.db.CarbCalculatorDatabase
import com.kevcoder.carbcalculator.data.local.db.CarbLogDao
import com.kevcoder.carbcalculator.data.local.db.SubmissionLogDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): CarbCalculatorDatabase =
        Room.databaseBuilder(
            context,
            CarbCalculatorDatabase::class.java,
            CarbCalculatorDatabase.DATABASE_NAME,
        )
            .addMigrations(CarbCalculatorDatabase.MIGRATION_1_2, CarbCalculatorDatabase.MIGRATION_2_3)
            .build()

    @Provides
    fun provideCarbLogDao(db: CarbCalculatorDatabase): CarbLogDao = db.carbLogDao()

    @Provides
    fun provideSubmissionLogDao(db: CarbCalculatorDatabase): SubmissionLogDao = db.submissionLogDao()

    @Provides
    @Singleton
    fun provideWorkManager(@ApplicationContext context: Context): WorkManager =
        WorkManager.getInstance(context)
}
