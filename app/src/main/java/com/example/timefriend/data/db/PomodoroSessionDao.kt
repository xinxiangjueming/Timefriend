package com.example.timefriend.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PomodoroSessionDao {

    /** Insert a session row (focus completed); returns the generated row id. */
    @Insert
    suspend fun insert(session: PomodoroSessionEntity): Long

    /** Fill in the rest minutes after the rest phase ends. */
    @Query("UPDATE pomodoro_sessions SET restMinutes = :restMinutes WHERE id = :id")
    suspend fun updateRest(id: Long, restMinutes: Int)

    /** Observe sessions in an inclusive date range (auto-refreshes). */
    @Query("SELECT * FROM pomodoro_sessions WHERE date BETWEEN :startDate AND :endDate ORDER BY date")
    fun observeByDateRange(startDate: String, endDate: String): Flow<List<PomodoroSessionEntity>>

    @Query("SELECT * FROM pomodoro_sessions WHERE date BETWEEN :startDate AND :endDate ORDER BY date")
    suspend fun getByDateRange(startDate: String, endDate: String): List<PomodoroSessionEntity>

    @Query("SELECT * FROM pomodoro_sessions ORDER BY date")
    suspend fun getAll(): List<PomodoroSessionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(sessions: List<PomodoroSessionEntity>)

    @Query("DELETE FROM pomodoro_sessions")
    suspend fun clearAll()
}
