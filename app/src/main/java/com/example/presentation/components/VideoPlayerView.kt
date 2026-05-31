package com.example.presentation.components

import android.net.Uri
import android.content.Context
import android.media.AudioManager
import android.app.Activity
import android.content.ContextWrapper
import androidx.compose.ui.platform.LocalContext
import kotlin.math.roundToInt
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.ui.unit.sp
import androidx.annotation.OptIn
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Pause
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.viewinterop.AndroidView
import android.view.LayoutInflater
import com.example.R
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.domain.models.VideoFile
import com.example.player.VideoPlayerManager
import com.example.presentation.viewmodels.VideoDisplayMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class SwipeGestureType {
    BRIGHTNESS, VOLUME
}

private fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerItem(
    video: VideoFile,
    isActive: Boolean,
    isUIVisible: Boolean,
    videoDisplayMode: VideoDisplayMode,
    playerManager: VideoPlayerManager,
    isAppVisible: Boolean,
    onSingleTap: () -> Unit,
    onDoubleTap: () -> Unit,
    onZoomChanged: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    // Collect the dynamic playerPool state to avoid race conditions and first-video rendering deadlocks
    val playerPool by playerManager.playerPoolState.collectAsStateWithLifecycle()
    val player = if (isActive) playerPool[video.id] else null

    var isPlaying by remember { mutableStateOf(false) }
    var is2xSpeed by remember { mutableStateOf(false) }
    var showSpeedHUD by remember { mutableStateOf(false) }
    var showPlayPauseHUD by remember { mutableStateOf<Boolean?>(null) } // null = hide, true = play icon, false = pause icon
    var playPauseHUDTrigger by remember { mutableStateOf(0) }
    var showHeartAnimation by remember { mutableStateOf(false) }
    var scale by remember { mutableStateOf(1.0f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }
    
    val coroutineScope = rememberCoroutineScope()

    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val audioManager = remember(context) { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVolume = remember(audioManager) { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).toFloat() }

    var swipeGestureType by remember { mutableStateOf<SwipeGestureType?>(null) }
    var swipeGestureValue by remember { mutableStateOf(0f) }
    var swipeGestureTrigger by remember { mutableStateOf(0) }

    LaunchedEffect(swipeGestureTrigger) {
        if (swipeGestureTrigger > 0) {
            delay(1000)
            swipeGestureType = null
        }
    }

    // Sync zoom state with the parent when paging or unloading
    LaunchedEffect(isActive) {
        if (!isActive) {
            scale = 1.0f
            offsetX = 0f
            offsetY = 0f
            onZoomChanged(1.0f)
        }
    }

    // Monitor isPlaying state of the active player
    DisposableEffect(player, isActive) {
        if (isActive && player != null) {
            isPlaying = player.isPlaying
            val listener = object : Player.Listener {
                override fun onIsPlayingChanged(playing: Boolean) {
                    isPlaying = playing
                }
            }
            player.addListener(listener)
            // Initial sync
            isPlaying = player.isPlaying
            
            onDispose {
                player.removeListener(listener)
            }
        } else {
            isPlaying = false
            onDispose {}
        }
    }

    // Hold to 2x speed playback parameters & show/hide HUD
    LaunchedEffect(is2xSpeed) {
        player?.let { p ->
            p.playbackParameters = PlaybackParameters(if (is2xSpeed) 2.0f else 1.0f)
        }
        if (is2xSpeed) {
            showSpeedHUD = true
            delay(1000)
            showSpeedHUD = false
        } else {
            showSpeedHUD = false
        }
    }

    // Delay for Play/Pause HUD fading
    LaunchedEffect(playPauseHUDTrigger) {
        if (playPauseHUDTrigger > 0) {
            delay(600)
            showPlayPauseHUD = null
        }
    }

    // Dual gesture pointer input controller (taps vs transforms)
    val gestureModifier = if (isActive && player != null) {
        Modifier
            .pointerInput(video.id) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var isZooming = false
                    val startPos = down.position
                    
                    do {
                        val event = awaitPointerEvent()
                        val changes = event.changes
                        if (changes.isEmpty()) break
                        
                        if (changes.size > 1) {
                            // Two or more fingers: pinch zoom and centroid panning
                            isZooming = true
                            val zoom = event.calculateZoom()
                            val oldScale = scale
                            scale = (scale * zoom).coerceIn(1.0f, 4.0f)
                            if (scale != oldScale) {
                                onZoomChanged(scale)
                            }
                            
                            val pan = event.calculatePan()
                            if (scale > 1.0f) {
                                offsetX += pan.x
                                offsetY += pan.y
                                val maxOffsetX = (size.width.toFloat() * (scale - 1f)) / 2f
                                val maxOffsetY = (size.height.toFloat() * (scale - 1f)) / 2f
                                offsetX = offsetX.coerceIn(-maxOffsetX, maxOffsetX)
                                offsetY = offsetY.coerceIn(-maxOffsetY, maxOffsetY)
                            }
                            changes.forEach { it.consume() }
                        } else {
                            // One finger
                            val pointer = changes[0]
                            if (pointer.pressed) {
                                val currentPos = pointer.position
                                val prevPos = pointer.previousPosition
                                val diffX = currentPos.x - prevPos.x
                                val diffY = currentPos.y - prevPos.y
                                
                                val totalDragX = kotlin.math.abs(currentPos.x - startPos.x)
                                val totalDragY = kotlin.math.abs(currentPos.y - startPos.y)
                                
                                if (scale > 1.0f) {
                                    // Always consume drag/pan when zoomed
                                    offsetX += diffX
                                    offsetY += diffY
                                    val maxOffsetX = (size.width.toFloat() * (scale - 1f)) / 2f
                                    val maxOffsetY = (size.height.toFloat() * (scale - 1f)) / 2f
                                    offsetX = offsetX.coerceIn(-maxOffsetX, maxOffsetX)
                                    offsetY = offsetY.coerceIn(-maxOffsetY, maxOffsetY)
                                    pointer.consume()
                                }
                            }
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
            .pointerInput(video.id) {
                detectTapGestures(
                    onDoubleTap = { tapOffset ->
                        if (scale > 1.0f) {
                            // Reset zoom if active
                            scale = 1.0f
                            offsetX = 0f
                            offsetY = 0f
                            onZoomChanged(1.0f)
                        } else {
                            // Normal double tap to favorite with heart bounce anim
                            onDoubleTap()
                            showHeartAnimation = true
                            coroutineScope.launch {
                                delay(650)
                                showHeartAnimation = false
                            }
                        }
                    },
                    onLongPress = { tapOffset ->
                        is2xSpeed = true
                    },
                    onPress = { tapOffset ->
                        tryAwaitRelease()
                        is2xSpeed = false
                    },
                    onTap = { tapOffset ->
                        val width = size.width
                        val height = size.height
                        val isCenter = tapOffset.x in (width * 0.325f)..(width * 0.675f) &&
                                       tapOffset.y in (height * 0.325f)..(height * 0.675f)
                        if (isCenter) {
                            player.let { p ->
                                if (p.isPlaying) {
                                    p.pause()
                                    showPlayPauseHUD = false
                                } else {
                                    p.play()
                                    showPlayPauseHUD = true
                                }
                                playPauseHUDTrigger++
                            }
                        } else {
                            onSingleTap()
                        }
                    }
                )
            }
    } else {
        Modifier
    }

    // Heart animations
    val heartScaleSpec = remember {
        spring<Float>(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
            stiffness = androidx.compose.animation.core.Spring.StiffnessLow
        )
    }
    val heartAlphaSpec = remember {
        spring<Float>(
            stiffness = androidx.compose.animation.core.Spring.StiffnessVeryLow
        )
    }

    val scaleHeart by animateFloatAsState(
        targetValue = if (showHeartAnimation) 1.8f else 0.0f,
        animationSpec = heartScaleSpec,
        label = "heart_scale"
    )
    val alphaHeart by animateFloatAsState(
        targetValue = if (showHeartAnimation) 1.0f else 0.0f,
        animationSpec = heartAlphaSpec,
        label = "heart_alpha"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(isActive, isUIVisible, scale) {
                if (!isActive || isUIVisible || scale > 1.0f) return@pointerInput
                
                val screenWidth = size.width
                val screenHeight = size.height
                
                detectDragGestures(
                    onDragStart = { startOffset ->
                        val isLeft = startOffset.x < screenWidth * 0.15f
                        val isRight = startOffset.x > screenWidth * 0.85f
                        
                        if (isLeft || isRight) {
                            val gestureType = if (isLeft) SwipeGestureType.BRIGHTNESS else SwipeGestureType.VOLUME
                            swipeGestureType = gestureType
                            
                            val baselineValue = try {
                                if (gestureType == SwipeGestureType.BRIGHTNESS) {
                                    val sysBrightness = activity?.window?.attributes?.screenBrightness ?: 0.5f
                                    if (sysBrightness < 0f) 0.5f else sysBrightness
                                } else {
                                    audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVolume
                                }
                            } catch (e: Exception) {
                                0.5f
                            }
                            swipeGestureValue = baselineValue
                            swipeGestureTrigger++
                        } else {
                            swipeGestureType = null
                        }
                    },
                    onDragEnd = {
                        swipeGestureTrigger++
                    },
                    onDragCancel = {
                        swipeGestureType = null
                    },
                    onDrag = { change, dragAmount ->
                        val currentType = swipeGestureType
                        if (currentType != null) {
                            change.consume()
                            
                            val dragFraction = -dragAmount.y / screenHeight
                            val newValue = (swipeGestureValue + dragFraction).coerceIn(0f, 1f)
                            swipeGestureValue = newValue
                            swipeGestureTrigger++
                            
                            try {
                                if (currentType == SwipeGestureType.BRIGHTNESS) {
                                    activity?.let { act ->
                                        val lp = act.window.attributes
                                        lp.screenBrightness = newValue.coerceIn(0.01f, 1.0f)
                                        act.window.attributes = lp
                                    }
                                } else {
                                    val targetVol = (newValue * maxVolume).roundToInt()
                                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVol, 0)
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("VideoPlayerView", "Error updating volume/brightness", e)
                            }
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        if (player != null) {
            Box(
                modifier = gestureModifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offsetX
                        translationY = offsetY
                    }
            ) {
                // High-performance direct View Binding for Media3 player
                AndroidView(
                    factory = { ctx ->
                        val view = LayoutInflater.from(ctx).inflate(R.layout.player_view, null) as PlayerView
                        view.apply {
                            setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                            keepScreenOn = true
                            resizeMode = when (videoDisplayMode) {
                                VideoDisplayMode.FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                                VideoDisplayMode.FILL -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                VideoDisplayMode.STRETCH -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                            }
                            this.player = player
                        }
                    },
                    update = { playerView ->
                        if (playerView.player != player) {
                            playerView.player = player
                        }
                        playerView.resizeMode = when (videoDisplayMode) {
                            VideoDisplayMode.FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                            VideoDisplayMode.FILL -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                            VideoDisplayMode.STRETCH -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                        }
                    },
                    onRelease = { playerView ->
                        playerView.player = null
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Render Classic overlay play indicators when paused
                if (isActive && !isPlaying && !is2xSpeed && showPlayPauseHUD == null) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                            .align(Alignment.Center),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.PlayArrow,
                            contentDescription = "Paused",
                            tint = Color.White.copy(alpha = 0.9f),
                            modifier = Modifier.size(40.dp)
                        )
                    }
                }
            }
        } else {
            // High fidelity fallback container during buffering or scroll transitions
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.material3.CircularProgressIndicator(
                    color = Color.White.copy(alpha = 0.25f),
                    modifier = Modifier.size(36.dp)
                )
            }
        }

        // Speed indicator tag HUD
        AnimatedVisibility(
            visible = showSpeedHUD,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 96.dp)
        ) {
            Row(
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Filled.FastForward,
                    contentDescription = "Fast playback",
                    tint = Color(0xFFFFCC00),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "2X PLAYBACK ACTIVE",
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }

        // Temporary Play/Pause Indicator HUD in the center
        if (showPlayPauseHUD != null) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                    .align(Alignment.Center),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (showPlayPauseHUD == true) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                    contentDescription = "Action",
                    tint = Color.White,
                    modifier = Modifier.size(40.dp)
                )
            }
        }

        // Gesture HUD for Volume / Brightness (Adaptive Vertical HUD on swiping side)
        val hudAlignment = if (swipeGestureType == SwipeGestureType.BRIGHTNESS) Alignment.CenterStart else Alignment.CenterEnd
        val hudPaddingMod = if (swipeGestureType == SwipeGestureType.BRIGHTNESS) {
            Modifier.padding(start = 36.dp)
        } else {
            Modifier.padding(end = 36.dp)
        }

        VolumeBrightnessHUD(
            type = swipeGestureType,
            value = swipeGestureValue,
            modifier = Modifier
                .align(hudAlignment)
                .then(hudPaddingMod)
        )

        // Smooth Favorite Heart feedback layer
        if (alphaHeart > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(alphaHeart),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Favorite,
                    contentDescription = "Favorited",
                    tint = Color.Red,
                    modifier = Modifier
                        .scale(scaleHeart)
                        .size(80.dp)
                )
            }
        }
    }
}

@Composable
fun VolumeBrightnessHUD(
    type: SwipeGestureType?,
    value: Float,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = type != null,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        if (type != null) {
            Column(
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(24.dp))
                    .padding(vertical = 16.dp, horizontal = 12.dp)
                    .width(44.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = if (type == SwipeGestureType.BRIGHTNESS) {
                        Icons.Filled.WbSunny
                    } else {
                        if (value <= 0f) Icons.Filled.VolumeOff
                        else if (value < 0.5f) Icons.Filled.VolumeDown
                        else Icons.Filled.VolumeUp
                    },
                    contentDescription = if (type == SwipeGestureType.BRIGHTNESS) "Brightness" else "Volume",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
                
                Spacer(modifier = Modifier.height(10.dp))
                
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(120.dp)
                        .background(Color.White.copy(alpha = 0.3f), RoundedCornerShape(2.dp)),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(value)
                            .background(Color.White, RoundedCornerShape(2.dp))
                    )
                }
                
                Spacer(modifier = Modifier.height(10.dp))
                
                Text(
                    text = "${(value * 100).roundToInt()}%",
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 11.sp
                )
            }
        }
    }
}
