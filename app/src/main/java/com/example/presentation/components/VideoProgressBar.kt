package com.example.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AmoledTextSecondary
import kotlinx.coroutines.delay
import androidx.lifecycle.repeatOnLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoProgressBar(
    player: androidx.media3.exoplayer.ExoPlayer?,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    if (player == null) {
        // Yield styled empty container of matching height to prevent layout shifts
        Box(modifier = modifier.height(30.dp))
        return
    }

    var positionMs by remember(player) { mutableStateOf(player.currentPosition) }
    var durationMs by remember(player) { mutableStateOf(if (player.duration > 0) player.duration else 0L) }
    var isDragging by remember { mutableStateOf(false) }
    var sliderValue by remember { mutableStateOf(0f) }
    var isPlaying by remember(player) { mutableStateOf(player.isPlaying) }

    DisposableEffect(player) {
        val listener = object : androidx.media3.common.Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }
        }
        player.addListener(listener)
        isPlaying = player.isPlaying
        onDispose {
            player.removeListener(listener)
        }
    }

    LaunchedEffect(positionMs) {
        if (!isDragging) {
            sliderValue = positionMs.toFloat()
        }
    }

    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    LaunchedEffect(player, isPlaying, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
            while (true) {
                if (!isDragging) {
                    positionMs = player.currentPosition
                    val d = player.duration
                    if (d > 0) {
                        durationMs = d
                    }
                }
                delay(if (isPlaying) 200L else 500L)
            }
        }
    }

    val safeDuration = if (durationMs > 0) durationMs else 1L
    val displayValue = sliderValue

    val formattedPosition = formatTime(displayValue.toLong())
    val formattedDuration = formatTime(durationMs)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp)
    ) {
        // Timeline counter indicators
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$formattedPosition / $formattedDuration",
                color = if (isDragging) Color(0xFFFFCC00) else AmoledTextSecondary,
                fontSize = 12.sp,
                style = MaterialTheme.typography.bodySmall
            )
        }

        // Custom stylized minimalist slider bar
        Slider(
            value = displayValue.coerceIn(0f, safeDuration.toFloat()),
            onValueChange = {
                isDragging = true
                sliderValue = it
            },
            onValueChangeFinished = {
                isDragging = false
                onSeek(sliderValue.toLong())
            },
            valueRange = 0f..safeDuration.toFloat(),
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color.White,
                inactiveTrackColor = Color.White.copy(alpha = 0.25f),
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp),
            thumb = {
                // Slick, ultra-minimal tiny dot slider thumb
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .padding(1.dp)
                ) {
                    Surface(
                        shape = androidx.compose.foundation.shape.CircleShape,
                        color = if (isDragging) Color(0xFFFFCC00) else Color.White,
                        modifier = Modifier.fillMaxSize()
                    ) {}
                }
            }
        )
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}
