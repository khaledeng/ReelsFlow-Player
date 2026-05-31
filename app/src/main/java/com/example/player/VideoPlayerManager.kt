package com.example.player

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import com.example.domain.models.VideoFile
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

@OptIn(UnstableApi::class)
class VideoPlayerManager(private val context: Context) : DefaultLifecycleObserver {

    // COLD START: Coroutine scope for running async staggered preload jobs
    private val mainScope = CoroutineScope(Dispatchers.Main + Job())
    private var preloadJob: Job? = null

    private val POOL_SIZE = 3
    
    private val rawPlayers = MutableList<ExoPlayer?>(POOL_SIZE) { null }

    // Map to maintain structural mappings of video state positions on background/foreground transitions
    private val savedPositions = mutableMapOf<Long, Long>()

    // Track state parameters of last assigned videos for seamless foreground restore
    private var lastSelectedIndex: Int = -1
    private var lastVideosList: List<VideoFile> = emptyList()

    private fun getPlayerInstance(index: Int): ExoPlayer {
        var player = rawPlayers[index]
        if (player == null) {
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.CONTENT_TYPE_MOVIE)
                .build()

            val loadControl = DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    15_000,   // Min buffer duration (15s)
                    30_000,   // Max buffer duration (30s)
                    1_500,    // Playback starting buffer (1.5s)
                    2_000     // Re-buffer duration (2s)
                )
                .build()

            player = ExoPlayer.Builder(context)
                .setAudioAttributes(audioAttributes, true) // P0 - Handle system audio focus automatically
                .setHandleAudioBecomingNoisy(true)        // P5 - Automatically pause playback when audio becomes noisy (e.g., unplugged headphones)
                .setLoadControl(loadControl)              // P5 - Specialize buffering limits for rapid micro-reels scanning
                .setWakeMode(C.WAKE_MODE_NONE)            // P5 - Completely avoid WakeLocks consumption when app is minimized
                .build().apply {
                    repeatMode = Player.REPEAT_MODE_ALL
                    playWhenReady = false
                }
            rawPlayers[index] = player
        }
        return player
    }

    // List wrapper to dynamically initialize ExoPlayers lazily on-demand without breaking existing indices references
    private val players = object : AbstractList<ExoPlayer>() {
        override val size: Int get() = POOL_SIZE
        override fun get(index: Int): ExoPlayer = getPlayerInstance(index)
    }

    // Maps videoId to its assigned index in the 'players' pool
    private val videoIdToPlayerIndex = mutableMapOf<Long, Int>()

    private val _playerPoolState = MutableStateFlow<Map<Long, ExoPlayer>>(emptyMap())
    val playerPoolState = _playerPoolState.asStateFlow()

    private val _activePlayer = MutableStateFlow<ExoPlayer?>(null)
    val activePlayer = _activePlayer.asStateFlow()

    /**
     * Called when a new video index is selected.
     * Manages assigning recycled player slots to current, previous, and next videos.
     */
    fun onPageSelected(currentIndex: Int, videos: List<VideoFile>, playImmediately: Boolean = true) {
        lastSelectedIndex = currentIndex
        lastVideosList = videos

        if (videos.isEmpty() || currentIndex < 0) {
            pauseAll()
            return
        }

        val size = videos.size
        val realCurrentIndex = currentIndex % size
        val currentVideo = videos[realCurrentIndex]

        val realPrevIndex = if (realCurrentIndex > 0) realCurrentIndex - 1 else size - 1
        val realNextIndex = if (realCurrentIndex < size - 1) realCurrentIndex + 1 else 0

        val prevVideo = if (size > 1) videos[realPrevIndex] else null
        val nextVideo = if (size > 2) videos[realNextIndex] else null

        val requiredIds = mutableSetOf<Long>()
        requiredIds.add(currentVideo.id)
        prevVideo?.let { requiredIds.add(it.id) }
        nextVideo?.let { requiredIds.add(it.id) }

        // Find video ID mapping slots that are no longer needed
        val unneededVideoIds = videoIdToPlayerIndex.keys.filterNot { requiredIds.contains(it) }.toSet()

        // Reclaim unneeded player indexes
        val freePlayerIndexes = mutableListOf<Int>()
        unneededVideoIds.forEach { vid ->
            videoIdToPlayerIndex.remove(vid)?.let { index ->
                freePlayerIndexes.add(index)
                val p = players[index]
                p.stop()
                p.clearMediaItems()
                savedPositions.remove(vid) // Discard obsolete item indexes
            }
        }

        // Fill remaining free slots from unassigned physical indexes
        val assignedIndexes = videoIdToPlayerIndex.values.toSet()
        for (i in 0 until POOL_SIZE) {
            if (!assignedIndexes.contains(i) && !freePlayerIndexes.contains(i)) {
                freePlayerIndexes.add(i)
            }
        }

        // Cancel any pending staggered preloading job
        preloadJob?.cancel()

        // 1. Setup/play current player immediately so time-to-first-frame is as fast as possible
        val currentPoolIdx = getOrAssignPlayerIndex(currentVideo, freePlayerIndexes)
        val currentPlayer = currentPoolIdx?.let { players[it] }
        if (currentPlayer != null) {
            currentPlayer.playWhenReady = playImmediately
            if (playImmediately) {
                if (!currentPlayer.isPlaying) {
                    currentPlayer.play()
                }
            } else {
                currentPlayer.pause()
            }
            _activePlayer.value = currentPlayer
        }

        // COLD START: Immediately publish the active player state so video starts rendering, skipping yet uninitialized preloads
        val initialPool = mutableMapOf<Long, ExoPlayer>()
        videoIdToPlayerIndex.forEach { (vid, index) ->
            val p = rawPlayers[index]
            if (p != null) {
                initialPool[vid] = p
            }
        }
        _playerPoolState.value = initialPool

        // 2 & 3. Preloading adjacent videos is deferred to prevent multiple concurrent ExoPlayer creation freezes
        preloadJob = mainScope.launch {
            delay(500) // COLD START: Delay preload work by 500ms to let active video transition and play smoothly
            
            // Pre-cache previous video
            prevVideo?.let { prev ->
                getOrAssignPlayerIndex(prev, freePlayerIndexes)?.let { idx ->
                    players[idx].playWhenReady = false
                }
            }

            // Pre-cache next video
            nextVideo?.let { next ->
                getOrAssignPlayerIndex(next, freePlayerIndexes)?.let { idx ->
                    players[idx].playWhenReady = false
                }
            }

            // Publish completed preload mappings reactively to trigger screen bindings
            val updatedPool = mutableMapOf<Long, ExoPlayer>()
            videoIdToPlayerIndex.forEach { (vid, index) ->
                val p = rawPlayers[index]
                if (p != null) {
                    updatedPool[vid] = p
                }
            }
            _playerPoolState.value = updatedPool
        }
    }

    private fun getOrAssignPlayerIndex(video: VideoFile, freeIndexes: MutableList<Int>): Int? {
        videoIdToPlayerIndex[video.id]?.let { return it }

        if (freeIndexes.isNotEmpty()) {
            val assignedIndex = freeIndexes.removeAt(0)
            videoIdToPlayerIndex[video.id] = assignedIndex
            val player = players[assignedIndex]
            player.stop()
            player.clearMediaItems()
            val mediaItem = MediaItem.fromUri(Uri.parse(video.uriString))
            player.setMediaItem(mediaItem)
            
            // Restore saved marker if available
            val savedPos = savedPositions[video.id] ?: 0L
            if (savedPos > 0L) {
                player.seekTo(savedPos)
            }
            
            player.prepare()
            return assignedIndex
        }
        return null
    }

    fun getPlayer(videoId: Long): ExoPlayer? {
        val index = videoIdToPlayerIndex[videoId] ?: return null
        return players[index]
    }

    fun pauseAll() {
        rawPlayers.forEach { it?.pause() }
    }

    /**
     * Completely release player resources when the application shuts down.
     */
    fun releaseAll() {
        // Cancel preloads
        preloadJob?.cancel()
        rawPlayers.forEach {
            it?.stop()
            it?.release()
        }
        rawPlayers.fill(null)
        videoIdToPlayerIndex.clear()
        savedPositions.clear()
        _activePlayer.value = null
        _playerPoolState.value = emptyMap()
    }

    /**
     * P0 Lifecycle: App goes to background. Unload system hardware decoders and drop memory caches immediately.
     */
    override fun onStop(owner: LifecycleOwner) {
        // BACKGROUND FIX: Save playback metrics
        videoIdToPlayerIndex.forEach { (videoId, index) ->
            val player = rawPlayers[index]
            if (player != null) {
                savedPositions[videoId] = player.currentPosition
            }
        }

        // BACKGROUND FIX: Stop current active player, completely release preload players in pool to free up decoders
        val size = lastVideosList.size
        val realCurrentIndex = if (size > 0) lastSelectedIndex % size else -1
        val currentVideo = if (realCurrentIndex >= 0) lastVideosList[realCurrentIndex] else null
        val currentPlayerIndex = currentVideo?.let { videoIdToPlayerIndex[it.id] } ?: -1

        for (i in 0 until POOL_SIZE) {
            val player = rawPlayers[i]
            if (player != null) {
                if (i == currentPlayerIndex) {
                    // BACKGROUND FIX: Stop current player and clear media items (retained for fast warming/resume)
                    player.playWhenReady = false
                    player.stop()
                    player.clearMediaItems()
                } else {
                    // BACKGROUND FIX: Completely release preloaded players to alleviate system-wide background resource pressure
                    player.stop()
                    player.release()
                    rawPlayers[i] = null
                }
            }
        }

        // Unlink references to prevent rendering loops when hidden
        _activePlayer.value = null
        _playerPoolState.value = emptyMap()
    }

    /**
     * P0 Lifecycle: App enters foreground. Warm, bind, restore visual positions, and prepare video players.
     */
    override fun onStart(owner: LifecycleOwner) {
        val currentIndex = lastSelectedIndex
        val videos = lastVideosList
        if (videos.isEmpty() || currentIndex < 0) {
            return
        }

        val size = videos.size
        val realCurrentIndex = currentIndex % size
        val currentVideo = videos[realCurrentIndex]

        val realPrevIndex = if (realCurrentIndex > 0) realCurrentIndex - 1 else size - 1
        val realNextIndex = if (realCurrentIndex < size - 1) realCurrentIndex + 1 else 0

        val prevVideo = if (size > 1) videos[realPrevIndex] else null
        val nextVideo = if (size > 2) videos[realNextIndex] else null

        val requiredVideos = listOfNotNull(currentVideo, prevVideo, nextVideo)
        val updatedPool = mutableMapOf<Long, ExoPlayer>()

        requiredVideos.forEach { video ->
            val index = videoIdToPlayerIndex[video.id]
            if (index != null) {
                val player = players[index]
                // Reconstruct and prepare ExoPlayer engine
                player.stop()
                player.clearMediaItems()
                val mediaItem = MediaItem.fromUri(Uri.parse(video.uriString))
                player.setMediaItem(mediaItem)

                val savedPos = savedPositions[video.id] ?: 0L
                if (savedPos > 0L) {
                    player.seekTo(savedPos)
                }

                player.prepare()

                if (video.id == currentVideo.id) {
                    player.playWhenReady = true
                    player.play()
                    _activePlayer.value = player
                } else {
                    player.playWhenReady = false
                }

                updatedPool[video.id] = player
            }
        }
        _playerPoolState.value = updatedPool
    }

    override fun onDestroy(owner: LifecycleOwner) {
        releaseAll()
    }
}
