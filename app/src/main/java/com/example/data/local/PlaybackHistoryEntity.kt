package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "playback_history")
data class PlaybackHistoryEntity(
    @PrimaryKey val filePath: String,
    val lastPlayedTimestamp: Long = System.currentTimeMillis(),
    val playCount: Int = 1
)
