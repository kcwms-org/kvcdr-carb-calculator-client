package com.kevcoder.carbcalculator.di

import android.content.Context
import androidx.room.Room
import com.kevcoder.carbcalculator.data.local.db.CarbCalculatorDatabase
import com.kevcoder.carbcalculator.data.local.db.CarbLogDao
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
        ).build()

    @Provides
    fun provideCarbLogDao(db: CarbCalculatorDatabase): CarbLogDao = db.carbLogDao()
}
