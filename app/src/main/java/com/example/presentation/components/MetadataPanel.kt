package com.example.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.models.VideoFile
import com.example.ui.theme.AmoledTextSecondary
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun MetadataPanel(
    video: VideoFile,
    modifier: Modifier = Modifier
) {
    val dateString = rememberFormattedDate(video.dateAdded)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Filename heading
        Text(
            text = video.title,
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        // Metadata specs row
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(vertical = 4.dp)
        ) {
            // Quality tag chip
            VideoSpecChip(
                text = video.quality,
                backgroundColor = Color.White.copy(alpha = 0.2f),
                textColor = Color.White
            )

            // Resolution
            VideoSpecChip(
                text = video.resolution,
                backgroundColor = Color.Black.copy(alpha = 0.4f),
                textColor = AmoledTextSecondary
            )

            // Duration
            VideoSpecChip(
                text = video.durationString,
                backgroundColor = Color.Black.copy(alpha = 0.4f),
                textColor = AmoledTextSecondary
            )

            // File Size
            VideoSpecChip(
                text = video.sizeString,
                backgroundColor = Color.Black.copy(alpha = 0.4f),
                textColor = AmoledTextSecondary
            )
        }

        // Location Info & Created dates
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            // Folder name
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Folder,
                    contentDescription = "Folder location",
                    tint = AmoledTextSecondary,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = video.folderName,
                    color = AmoledTextSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Creation Date
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Schedule,
                    contentDescription = "Created date",
                    tint = AmoledTextSecondary,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = dateString,
                    color = AmoledTextSecondary,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun VideoSpecChip(
    text: String,
    backgroundColor: Color,
    textColor: Color
) {
    Box(
        modifier = Modifier
            .background(backgroundColor, RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun rememberFormattedDate(seconds: Long): String {
    return androidx.compose.runtime.remember(seconds) {
        try {
            val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
            sdf.format(Date(seconds * 1000L))
        } catch (e: Exception) {
            "Unknown Date"
        }
    }
}
