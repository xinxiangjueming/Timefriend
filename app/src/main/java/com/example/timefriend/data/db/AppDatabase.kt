package com.example.timefriend.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [TimeRecordEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun timeRecordDao(): TimeRecordDao
}
