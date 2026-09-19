package com.wakecapture.app.di

import android.content.Context
import androidx.room.Room
import com.wakecapture.app.data.CaptureDao
import com.wakecapture.app.data.CaptureDatabase
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
    fun provideCaptureDatabase(@ApplicationContext context: Context): CaptureDatabase {
        return Room.databaseBuilder(
            context,
            CaptureDatabase::class.java,
            "wake_capture.db"
        ).build()
    }

    @Provides
    fun provideCaptureDao(database: CaptureDatabase): CaptureDao {
        return database.captureDao()
    }
}
