package com.example.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface VideoDao {

    // Favorites Queries
    @Query("SELECT * FROM favorite_videos ORDER BY timestamp DESC")
    fun getAllFavorites(): Flow<List<FavoriteVideoEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavorite(favorite: FavoriteVideoEntity)

    @Query("DELETE FROM favorite_videos WHERE filePath = :filePath")
    suspend fun deleteFavorite(filePath: String)

    @Query("SELECT EXISTS(SELECT 1 FROM favorite_videos WHERE filePath = :filePath)")
    suspend fun isFavorite(filePath: String): Boolean

    @Query("SELECT EXISTS(SELECT 1 FROM favorite_videos WHERE filePath = :filePath)")
    fun isFavoriteFlow(filePath: String): Flow<Boolean>

    // History Queries
    @Query("SELECT * FROM playback_history ORDER BY lastPlayedTimestamp DESC")
    fun getPlaybackHistoryFlow(): Flow<List<PlaybackHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(history: PlaybackHistoryEntity)

    @Query("SELECT * FROM playback_history WHERE filePath = :filePath")
    suspend fun getHistoryItem(filePath: String): PlaybackHistoryEntity?

    @Query("DELETE FROM playback_history WHERE filePath = :filePath")
    suspend fun deleteHistoryItem(filePath: String)

    @Query("DELETE FROM playback_history")
    suspend fun clearHistory()
}
