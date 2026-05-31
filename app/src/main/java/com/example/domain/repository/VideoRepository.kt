package com.example.domain.repository

import android.content.IntentSender
import com.example.domain.models.VideoFile
import kotlinx.coroutines.flow.Flow

sealed class DeleteResult {
    object Success : DeleteResult()
    object Failure : DeleteResult()
    data class RequiresUserConsent(val intentSender: IntentSender) : DeleteResult()
}

interface VideoRepository {
    fun getVideos(): Flow<List<VideoFile>>
    suspend fun scanLocalVideos()
    suspend fun toggleFavorite(video: VideoFile)
    suspend fun deleteVideo(video: VideoFile): DeleteResult
}
