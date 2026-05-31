package com.example.presentation.viewmodels

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.example.ReelsFlowApplication
import com.example.domain.models.PlaybackMode
import com.example.domain.models.VideoFile
import com.example.domain.repository.DeleteResult
import com.example.domain.repository.VideoRepository
import com.example.player.VideoPlayerManager
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

enum class VideoDisplayMode {
    FIT,    // Shows entire video with black bars
    FILL,   // Fills screen and crops excess
    STRETCH // Stretches video to screen size
}

sealed class VideoFeedUiState {
    object Loading : VideoFeedUiState()
    object PermissionNeeded : VideoFeedUiState()
    data class Success(
        val videos: List<VideoFile>,
        val showFavoritesOnly: Boolean = false
    ) : VideoFeedUiState()
    object Empty : VideoFeedUiState()
}

class VideoFeedViewModel(
    private val repository: VideoRepository,
    val playerManager: VideoPlayerManager,
    context: Context
) : ViewModel() {

    private val prefs = context.getSharedPreferences("reelsflow_prefs", Context.MODE_PRIVATE)

    private val _isAppVisible = MutableStateFlow(true)
    val isAppVisible = _isAppVisible.asStateFlow()

    // Saved playback parameters to perfectly restore position and state after backgrounding
    private var savedVideoId: Long? = null
    private var savedPageIndex: Int = -1
    private var savedPlaybackPositionMs: Long = 0L
    private var savedPlaybackMode: PlaybackMode? = null
    private var savedShuffleSeed: Long? = null

    fun setAppVisible(visible: Boolean) {
        _isAppVisible.value = visible
    }

    fun savePlaybackState(currentPageIndex: Int) {
        savedPageIndex = currentPageIndex
        savedPlaybackMode = _playbackMode.value
        savedShuffleSeed = _shuffleSeed.value

        val player = playerManager.activePlayer.value
        if (player != null) {
            savedPlaybackPositionMs = player.currentPosition
        }

        val state = uiState.value
        if (state is VideoFeedUiState.Success && currentPageIndex >= 0 && currentPageIndex < state.videos.size) {
            savedVideoId = state.videos[currentPageIndex].id
        }
    }

    fun restorePlaybackState() {
        val vidId = savedVideoId
        val pos = savedPlaybackPositionMs
        if (vidId != null && pos > 0L) {
            val player = playerManager.getPlayer(vidId)
            // Restore position to ensure no frame loss/drift
            player?.seekTo(pos)
        }
    }

    private val _showOnboarding = MutableStateFlow(prefs.getBoolean("show_onboarding_tutorial_v1", true))
    val showOnboarding = _showOnboarding.asStateFlow()

    fun dismissOnboarding() {
        _showOnboarding.value = false
        prefs.edit().putBoolean("show_onboarding_tutorial_v1", false).apply()
    }

    private val _showFavoritesOnly = MutableStateFlow(false)
    val showFavoritesOnly = _showFavoritesOnly.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing = _isRefreshing.asStateFlow()

    private val _initialScanCompleted = MutableStateFlow(false)
    val initialScanCompleted = _initialScanCompleted.asStateFlow()

    private val _selectedFolders = MutableStateFlow<Set<String>>(emptySet())
    val selectedFolders = _selectedFolders.asStateFlow()

    private val _playbackMode = MutableStateFlow(PlaybackMode.RANDOM)
    val playbackMode = _playbackMode.asStateFlow()

    private val _shuffleSeed = MutableStateFlow(System.currentTimeMillis() + System.nanoTime())
    val shuffleSeed = _shuffleSeed.asStateFlow()

    private val recentlyViewedIds = java.util.concurrent.CopyOnWriteArrayList<Long>(run {
        val stored = prefs.getString("recently_viewed_ids", "") ?: ""
        if (stored.isEmpty()) {
            emptyList<Long>()
        } else {
            stored.split(",").mapNotNull { it.toLongOrNull() }
        }
    })

    fun markVideoAsViewed(videoId: Long) {
        recentlyViewedIds.remove(videoId) // Avoid duplicate tracking
        recentlyViewedIds.add(videoId)
        // Cap history to keep enough unviewed space
        while (recentlyViewedIds.size > 12) {
            recentlyViewedIds.removeAt(0)
        }
        prefs.edit().putString("recently_viewed_ids", recentlyViewedIds.joinToString(",")).apply()
    }

    private val _videoDisplayMode = MutableStateFlow(
        VideoDisplayMode.valueOf(prefs.getString("video_display_mode", VideoDisplayMode.FILL.name) ?: VideoDisplayMode.FILL.name)
    )
    val videoDisplayMode = _videoDisplayMode.asStateFlow()

    // Expose reactive available folders
    val availableFolders: StateFlow<List<String>> = repository.getVideos()
        .map { videos ->
            videos.map { it.folderName }.distinct().sorted()
        }
        .flowOn(kotlinx.coroutines.Dispatchers.Default)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val favoriteIdsAtFilteringTime = MutableStateFlow<Set<Long>>(emptySet())

    private val FilterConfigFlow = combine(
        _showFavoritesOnly,
        _selectedFolders,
        _playbackMode,
        _shuffleSeed
    ) { favs, folders, mode, seed ->
        FilterConfig(favs, folders, mode, seed)
    }

    private val stateLock = Any()
    private var cachedVideosRawList: List<VideoFile> = emptyList()
    private var cachedFilterConfig: FilterConfig? = null
    private var cachedInitialScanCompleted: Boolean = false
    private var cachedSortedVideos: List<VideoFile> = emptyList()

    val uiState: StateFlow<VideoFeedUiState> = combine(
        repository.getVideos(),
        FilterConfigFlow,
        favoriteIdsAtFilteringTime,
        _initialScanCompleted
    ) { videos, config, favIdsAtFilter, initialScanCompleted ->
        synchronized(stateLock) {
            val isOnlyFavoriteChange = cachedSortedVideos.isNotEmpty() &&
                    initialScanCompleted == cachedInitialScanCompleted &&
                    config == cachedFilterConfig &&
                    videos.size == cachedVideosRawList.size &&
                    videos.zip(cachedVideosRawList).all { (v1, v2) ->
                        v1.id == v2.id &&
                        v1.path == v2.path &&
                        v1.title == v2.title &&
                        v1.duration == v2.duration
                    }

            val sortedVideos: List<VideoFile>
            if (isOnlyFavoriteChange) {
                val favMap = videos.associate { it.id to it.isFavorite }
                val updated = cachedSortedVideos.map { video ->
                    video.copy(isFavorite = favMap[video.id] ?: video.isFavorite)
                }
                sortedVideos = if (config.favoritesOnly) {
                    updated.filter { favIdsAtFilter.contains(it.id) }
                } else {
                    updated
                }
            } else {
                // 1. Filter by favorites
                var filteredVideos = if (config.favoritesOnly) {
                    videos.filter { favIdsAtFilter.contains(it.id) }
                } else {
                    videos
                }

                // 2. Filter by selected folders (apply folder selection rules)
                if (config.selectedFolders.isNotEmpty()) {
                    filteredVideos = filteredVideos.filter { config.selectedFolders.contains(it.folderName) }
                }

                // 3. Apply playback / sorting modes
                sortedVideos = when (config.playbackMode) {
                    PlaybackMode.SEQUENTIAL -> {
                        // Natural alphabet folder structure mapping
                        filteredVideos.sortedWith(compareBy({ it.folderName.lowercase() }, { it.title.lowercase() }))
                    }
                    PlaybackMode.NEWEST_FIRST -> {
                        filteredVideos.sortedByDescending { it.dateAdded }
                    }
                    PlaybackMode.OLDEST_FIRST -> {
                        filteredVideos.sortedBy { it.dateAdded }
                    }
                    PlaybackMode.RANDOM -> {
                        // Generate a fully random shuffle based on the refreshed seed
                        val rand = java.util.Random(config.shuffleSeed)
                        val shuffled = filteredVideos.shuffled(rand)
                        // Bias index so that recently viewed videos are sorted towards the end
                        shuffled.sortedBy { video ->
                            val historyIndex = recentlyViewedIds.indexOf(video.id)
                            if (historyIndex >= 0) {
                                // The more recent, the further back it goes
                                1000 + historyIndex
                            } else {
                                0
                            }
                        }
                    }
                }
            }

            cachedVideosRawList = videos
            cachedFilterConfig = config
            cachedInitialScanCompleted = initialScanCompleted
            cachedSortedVideos = sortedVideos

            when {
                !initialScanCompleted -> VideoFeedUiState.Loading
                videos.isEmpty() -> VideoFeedUiState.Empty
                else -> VideoFeedUiState.Success(sortedVideos, config.favoritesOnly)
            }
        }
    }.flowOn(kotlinx.coroutines.Dispatchers.Default)
    .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = VideoFeedUiState.Loading
    )

    fun scanVideos() {
        viewModelScope.launch {
            _isRefreshing.value = true
            repository.scanLocalVideos()
            try {
                // Wait for the flow on Dispatchers.IO to propagate the scanned list
                repository.getVideos().first()
            } catch (e: Exception) {
                // ignore
            }
            // Yield execution to allow Compose/Flow observers on Main thread to catch up first
            kotlinx.coroutines.yield()
            _initialScanCompleted.value = true
            _isRefreshing.value = false
        }
    }

    fun setPermissionGranted(granted: Boolean) {
        if (granted) {
            scanVideos()
        }
    }

    fun toggleFavorite(video: VideoFile) {
        viewModelScope.launch {
            repository.toggleFavorite(video)
        }
    }

    fun toggleFilterFavorites() {
        val nextValue = !_showFavoritesOnly.value
        if (nextValue) {
            viewModelScope.launch {
                val currentVideos = repository.getVideos().first()
                favoriteIdsAtFilteringTime.value = currentVideos.filter { it.isFavorite }.map { it.id }.toSet()
                _showFavoritesOnly.value = nextValue
            }
        } else {
            _showFavoritesOnly.value = nextValue
        }
    }

    fun deleteVideo(video: VideoFile, callback: (DeleteResult) -> Unit) {
        viewModelScope.launch {
            val result = repository.deleteVideo(video)
            callback(result)
        }
    }

    fun selectFolder(folder: String, multiSelect: Boolean) {
        val current = _selectedFolders.value
        if (multiSelect) {
            if (current.contains(folder)) {
                _selectedFolders.value = current - folder
            } else {
                _selectedFolders.value = current + folder
            }
        } else {
            if (current.size == 1 && current.contains(folder)) {
                _selectedFolders.value = emptySet() // Deselect if selected again
            } else {
                _selectedFolders.value = setOf(folder)
            }
        }
    }

    fun setPlaybackMode(mode: PlaybackMode) {
        if (mode == PlaybackMode.RANDOM) {
            _shuffleSeed.value = java.util.UUID.randomUUID().hashCode().toLong() + System.nanoTime()
        }
        _playbackMode.value = mode
    }

    fun setVideoDisplayMode(mode: VideoDisplayMode) {
        _videoDisplayMode.value = mode
        prefs.edit().putString("video_display_mode", mode.name).apply()
    }

    fun toggleVideoDisplayMode() {
        val nextMode = when (_videoDisplayMode.value) {
            VideoDisplayMode.FILL -> VideoDisplayMode.FIT
            VideoDisplayMode.FIT -> VideoDisplayMode.STRETCH
            VideoDisplayMode.STRETCH -> VideoDisplayMode.FILL
        }
        setVideoDisplayMode(nextMode)
    }

    fun clearFolderFilters() {
        _selectedFolders.value = emptySet()
    }

    fun disableFavoritesFilter() {
        _showFavoritesOnly.value = false
    }

    fun shareVideo(context: Context, video: VideoFile) {
        try {
            val videoUri = Uri.parse(video.uriString)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "video/*"
                putExtra(Intent.EXTRA_STREAM, videoUri)
                putExtra(Intent.EXTRA_TITLE, video.title)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Share offline video with..."))
        } catch (e: Exception) {
            android.util.Log.e("VideoFeedViewModel", "Error sharing video", e)
        }
    }

    override fun onCleared() {
        super.onCleared()
        playerManager.releaseAll()
    }

    companion object {
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(
                modelClass: Class<T>,
                extras: CreationExtras
            ): T {
                val application = checkNotNull(extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]) as ReelsFlowApplication
                return VideoFeedViewModel(
                    application.container.videoRepository,
                    application.container.videoPlayerManager,
                    application
                ) as T
            }
        }
    }
}

private data class FilterConfig(
    val favoritesOnly: Boolean,
    val selectedFolders: Set<String>,
    val playbackMode: PlaybackMode,
    val shuffleSeed: Long
)
