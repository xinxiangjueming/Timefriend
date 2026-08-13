package com.example.timefriend.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TimeRecordDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: TimeRecordEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(records: List<TimeRecordEntity>)

    /** Observe records of a single date (auto-refreshes on table changes). */
    @Query("SELECT * FROM records WHERE date = :date ORDER BY startTime")
    fun observeByDate(date: String): Flow<List<TimeRecordEntity>>

    /** Observe records in an inclusive date range (auto-refreshes). */
    @Query("SELECT * FROM records WHERE date BETWEEN :startDate AND :endDate ORDER BY date, startTime")
    fun observeByDateRange(startDate: String, endDate: String): Flow<List<TimeRecordEntity>>

    /** Observe all distinct dates that have records (calendar dots, auto-refreshes). */
    @Query("SELECT DISTINCT date FROM records ORDER BY date")
    fun observeDistinctDates(): Flow<List<String>>

    @Query("SELECT * FROM records WHERE date = :date ORDER BY startTime")
    suspend fun getByDate(date: String): List<TimeRecordEntity>

    @Query("SELECT DISTINCT date FROM records ORDER BY date")
    suspend fun getDistinctDates(): List<String>

    @Query("SELECT * FROM records ORDER BY date, startTime")
    suspend fun getAll(): List<TimeRecordEntity>

    @Query("DELETE FROM records WHERE date = :date AND category = :category")
    suspend fun deleteByDateAndCategory(date: String, category: String)

    @Query("DELETE FROM records WHERE date = :date")
    suspend fun deleteByDate(date: String)

    @Query("DELETE FROM records")
    suspend fun clearAll()
}
