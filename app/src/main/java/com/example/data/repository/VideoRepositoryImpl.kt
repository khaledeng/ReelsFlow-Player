package com.example.data.repository

import android.app.RecoverableSecurityException
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import com.example.data.local.FavoriteVideoEntity
import com.example.data.local.VideoDao
import com.example.domain.models.VideoFile
import com.example.domain.repository.DeleteResult
import com.example.domain.repository.VideoRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import java.io.File

class VideoRepositoryImpl(
    private val context: Context,
    private val videoDao: VideoDao
) : VideoRepository {

    private val contentResolver: ContentResolver = context.contentResolver
    private val _scannedVideos = MutableStateFlow<List<VideoFile>>(emptyList())

    // Combine scanned videos with the reactive favorites from Room DB
    override fun getVideos(): Flow<List<VideoFile>> {
        return combine(
            _scannedVideos,
            videoDao.getAllFavorites()
        ) { scanned, favorites ->
            val favPaths = favorites.map { it.filePath }.toSet()
            scanned.map { video ->
                video.copy(isFavorite = favPaths.contains(video.path))
            }
        }.flowOn(Dispatchers.IO)
    }

    override suspend fun scanLocalVideos() = withContext(Dispatchers.IO) {
        val list = mutableListOf<VideoFile>()
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }

        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.RESOLUTION,
            MediaStore.Video.Media.DATA,
            MediaStore.Video.Media.DATE_ADDED
        )

        val sortOrder = "${MediaStore.Video.Media.DATE_ADDED} DESC"

        try {
            contentResolver.query(
                collection,
                projection,
                null,
                null,
                sortOrder
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                val resolutionColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.RESOLUTION)
                val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
                val dateAddedColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val name = cursor.getString(nameColumn) ?: "Video_$id"
                    val duration = cursor.getLong(durationColumn)
                    val size = cursor.getLong(sizeColumn)
                    val resolution = cursor.getString(resolutionColumn) ?: "1920x1080"
                    val path = cursor.getString(dataColumn) ?: ""
                    val dateAdded = cursor.getLong(dateAddedColumn)

                    val uri = ContentUris.withAppendedId(
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                        id
                    )

                    // COLD START: Skip physical exists check to prevent blocking disk I/O on large media lists, relying on MediaStore integrity and non-zero size
                    if (path.isNotEmpty() && size > 1000) {
                        list.add(
                            VideoFile(
                                id = id,
                                uriString = uri.toString(),
                                title = name,
                                duration = duration,
                                size = size,
                                resolution = resolution,
                                path = path,
                                dateAdded = dateAdded,
                                isFavorite = false // Calculated dynamically
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("VideoRepositoryImpl", "Error querying MediaStore", e)
        }

        if (list.isEmpty()) {
            list.addAll(getMockVideos())
        }

        _scannedVideos.value = list
    }

    private fun getMockVideos(): List<VideoFile> {
        val now = System.currentTimeMillis() / 1000
        return listOf(
            VideoFile(
                id = -101L,
                uriString = "https://assets.mixkit.co/videos/preview/mixkit-girl-in-neon-sign-light-portrait-40320-large.mp4",
                title = "Neon Glow Portrait (Demo)",
                duration = 12000L,
                size = 4123456L,
                resolution = "1080x1920",
                path = "/mock/neon_glow.mp4",
                dateAdded = now - 10,
                isFavorite = false
            ),
            VideoFile(
                id = -102L,
                uriString = "https://assets.mixkit.co/videos/preview/mixkit-waves-in-the-ocean-near-a-cliff-43029-large.mp4",
                title = "Coastal Ocean Waves (Demo)",
                duration = 15000L,
                size = 5210987L,
                resolution = "1080x1920",
                path = "/mock/ocean_waves.mp4",
                dateAdded = now - 20,
                isFavorite = false
            ),
            VideoFile(
                id = -103L,
                uriString = "https://assets.mixkit.co/videos/preview/mixkit-forest-stream-in-the-sunlight-529-large.mp4",
                title = "Sunlit Forest Stream (Demo)",
                duration = 8000L,
                size = 2543210L,
                resolution = "1080x1920",
                path = "/mock/forest_stream.mp4",
                dateAdded = now - 30,
                isFavorite = false
            ),
            VideoFile(
                id = -104L,
                uriString = "https://assets.mixkit.co/videos/preview/mixkit-tree-with-yellow-flowers-against-the-blue-sky-40019-large.mp4",
                title = "Spring Yellow Flowers (Demo)",
                duration = 10000L,
                size = 3412389L,
                resolution = "1080x1920",
                path = "/mock/yellow_flowers.mp4",
                dateAdded = now - 40,
                isFavorite = false
            ),
            VideoFile(
                id = -105L,
                uriString = "https://assets.mixkit.co/videos/preview/mixkit-playful-cat-lying-on-a-couch-40192-large.mp4",
                title = "Playful Couch Cat (Demo)",
                duration = 14000L,
                size = 4678123L,
                resolution = "1080x1920",
                path = "/mock/couch_cat.mp4",
                dateAdded = now - 50,
                isFavorite = false
            ),
            VideoFile(
                id = -106L,
                uriString = "https://assets.mixkit.co/videos/preview/mixkit-spinning-retro-vinyl-turntable-playing-a-record-40294-large.mp4",
                title = "Retro Vinyl Record (Demo)",
                duration = 11000L,
                size = 3102931L,
                resolution = "1080x1920",
                path = "/mock/vinyl_record.mp4",
                dateAdded = now - 60,
                isFavorite = false
            ),
            VideoFile(
                id = -107L,
                uriString = "https://assets.mixkit.co/videos/preview/mixkit-raindrops-hitting-a-window-pane-43180-large.mp4",
                title = "Raindrops on Window (Demo)",
                duration = 18000L,
                size = 6100902L,
                resolution = "1080x1920",
                path = "/mock/raindrops_window.mp4",
                dateAdded = now - 70,
                isFavorite = false
            )
        )
    }

    override suspend fun toggleFavorite(video: VideoFile) = withContext(Dispatchers.IO) {
        if (video.isFavorite) {
            videoDao.deleteFavorite(video.path)
        } else {
            videoDao.insertFavorite(
                FavoriteVideoEntity(
                    filePath = video.path,
                    id = video.id
                )
            )
        }
    }

    override suspend fun deleteVideo(video: VideoFile): DeleteResult = withContext(Dispatchers.IO) {
        val uri = Uri.parse(video.uriString)
        try {
            // Remove from app's database first
            videoDao.deleteFavorite(video.path)
            videoDao.deleteHistoryItem(video.path)

            // 1. Try ContentResolver Delete
            val rowsDeleted = contentResolver.delete(uri, null, null)
            
            // 2. Try direct physical deletion fallback
            val file = File(video.path)
            val physicalDeleted = if (file.exists()) file.delete() else true

            if (rowsDeleted > 0 || physicalDeleted) {
                // Refresh local scanning list
                _scannedVideos.value = _scannedVideos.value.filter { it.id != video.id }
                DeleteResult.Success
            } else {
                DeleteResult.Failure
            }
        } catch (securityException: SecurityException) {
            // For Android Q (API 29) + and above, check for RecoverableSecurityException to prompt permission dialog
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && securityException is RecoverableSecurityException) {
                DeleteResult.RequiresUserConsent(securityException.userAction.actionIntent.intentSender)
            } else {
                Log.e("VideoRepositoryImpl", "SecurityException during deletion", securityException)
                DeleteResult.Failure
            }
        } catch (e: Exception) {
            Log.e("VideoRepositoryImpl", "Error deleting video", e)
            DeleteResult.Failure
        }
    }
}
