package com.wakecapture.app.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [CaptureEntity::class], version = 1, exportSchema = true)
abstract class CaptureDatabase : RoomDatabase() {
    abstract fun captureDao(): CaptureDao
}
