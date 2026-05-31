package com.example.domain.models

import android.net.Uri

data class VideoFile(
    val id: Long,
    val uriString: String,
    val title: String,
    val duration: Long,
    val size: Long,
    val resolution: String,
    val path: String,
    val dateAdded: Long,
    val isFavorite: Boolean = false
) {
    val durationString: String
        get() {
            val totalSeconds = duration / 1000
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            return String.format("%02d:%02d", minutes, seconds)
        }

    val sizeString: String
        get() {
            if (size <= 0) return "0 B"
            val units = arrayOf("B", "KB", "MB", "GB")
            val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
            return String.format("%.1f %s", size / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
        }

    val quality: String
        get() {
            if (resolution.isBlank() || !resolution.contains("x")) return "SD"
            val parts = resolution.split("x")
            val width = parts.getOrNull(0)?.toIntOrNull() ?: 0
            val height = parts.getOrNull(1)?.toIntOrNull() ?: 0
            val side = Math.min(width, height).takeIf { it > 0 } ?: Math.max(width, height)
            
            return when {
                side >= 2160 -> "4K"
                side >= 1440 -> "1440p (2K)"
                side >= 1080 -> "1080p FHD"
                side >= 720 -> "720p"
                else -> "SD"
            }
        }
        
    val folderName: String
        get() {
            val fileParts = path.split("/")
            return if (fileParts.size > 2) fileParts[fileParts.size - 2] else "Local Storage"
        }
}
