package com.example.di

import android.content.Context
import com.example.data.local.VideoDatabase
import com.example.data.repository.VideoRepositoryImpl
import com.example.domain.repository.VideoRepository
import com.example.player.VideoPlayerManager

interface AppContainer {
    val videoRepository: VideoRepository
    val videoPlayerManager: VideoPlayerManager
}

class AppContainerImpl(private val context: Context) : AppContainer {
    
    private val database: VideoDatabase by lazy {
        VideoDatabase.getDatabase(context)
    }

    override val videoRepository: VideoRepository by lazy {
        VideoRepositoryImpl(
            context = context,
            videoDao = database.videoDao()
        )
    }

    override val videoPlayerManager: VideoPlayerManager by lazy {
        VideoPlayerManager(context)
    }
}
