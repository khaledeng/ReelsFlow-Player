package com.example.presentation.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.ui.draw.scale
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGestures
import com.example.domain.models.PlaybackMode
import com.example.domain.models.VideoFile
import com.example.presentation.viewmodels.VideoDisplayMode
import com.example.ui.theme.AccentColor
import com.example.ui.theme.DeleteRed

@Composable
fun RightActionButtons(
    video: VideoFile,
    playbackMode: PlaybackMode,
    videoDisplayMode: VideoDisplayMode,
    onFavoriteClick: () -> Unit,
    onShareClick: () -> Unit,
    onPlaybackModeClick: () -> Unit,
    onVideoDisplayModeClick: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(72.dp)
            .padding(vertical = 12.dp)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        event.changes.forEach { it.consume() }
                    }
                }
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        val scaleIcon by animateFloatAsState(
            targetValue = if (video.isFavorite) 1.3f else 1.0f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium
            ),
            label = "heart_scale"
        )

        // Favorite (Heart) Button
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                    .clickable { onFavoriteClick() }
                    .testTag("btn_favorite"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (video.isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = "Favorite",
                    tint = if (video.isFavorite) Color.Red else Color.White,
                    modifier = Modifier
                        .size(24.dp)
                        .scale(scaleIcon)
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Favorite",
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 10.sp,
                style = androidx.compose.material3.MaterialTheme.typography.labelSmall
            )
        }

        // Share Button
        SidebarIconButton(
            icon = Icons.Outlined.Share,
            contentDescription = "Share",
            tint = Color.White,
            onClick = onShareClick,
            testTag = "btn_share"
        )

        // Dynamic Playback Mode Button
        val (modeIcon, modeLabel) = when (playbackMode) {
            PlaybackMode.SEQUENTIAL -> Icons.Filled.List to "Sequential"
            PlaybackMode.RANDOM -> Icons.Filled.Shuffle to "Shuffle"
            PlaybackMode.NEWEST_FIRST -> Icons.Filled.ArrowUpward to "Newest"
            PlaybackMode.OLDEST_FIRST -> Icons.Filled.ArrowDownward to "Oldest"
        }

        SidebarIconButton(
            icon = modeIcon,
            contentDescription = modeLabel,
            tint = Color(0xFFFFCC00),
            onClick = onPlaybackModeClick,
            testTag = "btn_playback_mode"
        )

        // Aspect Ratio Mode Button
        val aspectIcon = when (videoDisplayMode) {
            VideoDisplayMode.FIT -> Icons.Outlined.AspectRatio
            VideoDisplayMode.FILL -> Icons.Filled.AspectRatio
            VideoDisplayMode.STRETCH -> Icons.Filled.Crop
        }
        val aspectLabel = when (videoDisplayMode) {
            VideoDisplayMode.FIT -> "Fit"
            VideoDisplayMode.FILL -> "Fill"
            VideoDisplayMode.STRETCH -> "Stretch"
        }

        SidebarIconButton(
            icon = aspectIcon,
            contentDescription = aspectLabel,
            tint = Color.White,
            onClick = onVideoDisplayModeClick,
            testTag = "btn_aspect_ratio_toggle"
        )

        // Delete Button
        SidebarIconButton(
            icon = Icons.Outlined.Delete,
            contentDescription = "Delete",
            tint = DeleteRed,
            onClick = onDeleteClick,
            testTag = "btn_delete"
        )
    }
}

@Composable
private fun SidebarIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    tint: Color,
    onClick: () -> Unit,
    testTag: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                .clickable { onClick() }
                .testTag(testTag),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = tint,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = contentDescription,
            color = Color.White.copy(alpha = 0.8f),
            fontSize = 10.sp,
            style = androidx.compose.material3.MaterialTheme.typography.labelSmall
        )
    }
}
