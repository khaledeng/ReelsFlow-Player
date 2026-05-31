package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "favorite_videos")
data class FavoriteVideoEntity(
    @PrimaryKey val filePath: String,
    val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis()
)
