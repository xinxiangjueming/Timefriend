package com.example.timefriend.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.timefriend.data.TimeCategory
import com.example.timefriend.data.TimeRecord

/**
 * Room entity for time records.
 *
 * [uid] is an auto-generated surrogate primary key, because the original
 * [id] (a millisecond timestamp) can collide when two records are created
 * within the same millisecond. The original [id] is kept as a plain column
 * to stay compatible with the WebDAV backup format.
 */
@Entity(
    tableName = "records",
    indices = [
        Index(value = ["date"]),
        Index(value = ["category"])
    ]
)
data class TimeRecordEntity(
    @PrimaryKey(autoGenerate = true) val uid: Long = 0,
    val id: Long,
    val category: String,
    val startTime: Long,
    val endTime: Long,
    val date: String
)

fun TimeRecord.toEntity() = TimeRecordEntity(
    id = id,
    category = category.name,
    startTime = startTime,
    endTime = endTime,
    date = date
)

fun TimeRecordEntity.toRecord() = TimeRecord(
    id = id,
    category = TimeCategory.valueOf(category),
    startTime = startTime,
    endTime = endTime,
    date = date
)
