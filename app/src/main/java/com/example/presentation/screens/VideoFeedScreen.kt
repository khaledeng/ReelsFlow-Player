package com.example.presentation.screens

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.border
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import com.example.domain.models.PlaybackMode
import com.example.domain.models.VideoFile
import com.example.domain.repository.DeleteResult
import com.example.presentation.components.DeleteConfirmDialog
import com.example.presentation.components.FolderSelectionDialog
import com.example.presentation.components.MetadataPanel
import com.example.presentation.components.RightActionButtons
import com.example.presentation.components.VideoPlayerItem
import com.example.presentation.components.VideoProgressBar
import com.example.presentation.viewmodels.VideoFeedUiState
import com.example.presentation.viewmodels.VideoFeedViewModel
import com.example.presentation.viewmodels.VideoDisplayMode
import androidx.compose.foundation.shape.CircleShape
import com.example.ui.theme.AccentColor
import com.example.ui.theme.AmoledBlack
import com.example.ui.theme.AmoledGray
import com.example.ui.theme.AmoledTextSecondary
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.flowOf

@Composable
fun VideoFeedScreen(
    viewModel: VideoFeedViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val showFavoritesOnly by viewModel.showFavoritesOnly.collectAsStateWithLifecycle()
    var showFolderDialog by remember { mutableStateOf(false) }
    val selectedFolders by viewModel.selectedFolders.collectAsStateWithLifecycle()
    val availableFolders by viewModel.availableFolders.collectAsStateWithLifecycle()
    val playbackMode by viewModel.playbackMode.collectAsStateWithLifecycle()

    // Permissions logic
    val requiredPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_VIDEO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                requiredPermission
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasPermission = isGranted
    }

    LaunchedEffect(hasPermission) {
        if (hasPermission) {
            viewModel.scanVideos()
        }
    }

    val deleteConsentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            Toast.makeText(context, "Video deleted successfully.", Toast.LENGTH_SHORT).show()
            viewModel.scanVideos()
        }
    }

    Surface(
        color = AmoledBlack,
        modifier = modifier.fillMaxSize()
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (!hasPermission) {
                PermissionRequiredLayout(
                    permissionName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) "Video Media" else "External Storage",
                    onRequestPermission = { permissionLauncher.launch(requiredPermission) },
                    onSkipWithDemo = {
                        hasPermission = true
                        viewModel.setPermissionGranted(true)
                    }
                )
            } else {
                when (val state = uiState) {
                    is VideoFeedUiState.Loading -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = Color.White)
                        }
                    }
                    is VideoFeedUiState.Empty -> {
                        EmptyStorageLayout(
                            onRefreshClick = { viewModel.scanVideos() },
                            onChooseFolderClick = { showFolderDialog = true }
                        )
                    }
                    is VideoFeedUiState.Success -> {
                        if (state.videos.isEmpty()) {
                            FilteredEmptyStateLayout(
                                showFavoritesOnly = showFavoritesOnly,
                                selectedFolders = selectedFolders,
                                onBackToFeed = {
                                    viewModel.disableFavoritesFilter()
                                    viewModel.clearFolderFilters()
                                },
                                onRefreshClick = { viewModel.scanVideos() },
                                onChooseFolderClick = { showFolderDialog = true }
                            )
                        } else {
                            key(selectedFolders, showFavoritesOnly, playbackMode, state.videos.size) {
                                VideoPagerFeed(
                                    viewModel = viewModel,
                                    videos = state.videos,
                                    showFavoritesOnly = showFavoritesOnly,
                                    onToggleFilter = { viewModel.toggleFilterFavorites() },
                                    onFavoriteToggle = { video -> viewModel.toggleFavorite(video) },
                                    onShareVideo = { video -> viewModel.shareVideo(context, video) },
                                    onDeleteVideo = { video, onCompleted ->
                                        viewModel.deleteVideo(video) { result ->
                                            when (result) {
                                                is DeleteResult.Success -> {
                                                    Toast.makeText(context, "Video deleted.", Toast.LENGTH_SHORT).show()
                                                    onCompleted()
                                                }
                                                is DeleteResult.RequiresUserConsent -> {
                                                    deleteConsentLauncher.launch(
                                                        IntentSenderRequest.Builder(result.intentSender).build()
                                                    )
                                                }
                                                is DeleteResult.Failure -> {
                                                    Toast.makeText(context, "Could not delete video.", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        }
                                    },
                                    onShowFolderSelection = { showFolderDialog = true }
                                )
                            }
                        }
                    }
                    is VideoFeedUiState.PermissionNeeded -> {
                        PermissionRequiredLayout(
                            permissionName = "Local Media",
                            onRequestPermission = { permissionLauncher.launch(requiredPermission) },
                            onSkipWithDemo = {
                                hasPermission = true
                                viewModel.setPermissionGranted(true)
                            }
                        )
                    }
                }
            }

            // Globally overlayed FolderSelectionDialog
            AnimatedVisibility(
                visible = showFolderDialog,
                enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 }),
                modifier = Modifier.fillMaxSize()
            ) {
                FolderSelectionDialog(
                    folders = availableFolders,
                    selectedFolders = selectedFolders,
                    onFolderToggled = { folder ->
                        viewModel.selectFolder(folder, multiSelect = true)
                    },
                    onClearFilters = {
                        viewModel.clearFolderFilters()
                    },
                    onDismiss = { showFolderDialog = false }
                )
            }
        }
    }
}

@Composable
fun VideoPagerFeed(
    viewModel: VideoFeedViewModel,
    videos: List<VideoFile>,
    showFavoritesOnly: Boolean,
    onToggleFilter: () -> Unit,
    onFavoriteToggle: (VideoFile) -> Unit,
    onShareVideo: (VideoFile) -> Unit,
    onDeleteVideo: (VideoFile, () -> Unit) -> Unit,
    onShowFolderSelection: () -> Unit
) {
    // INFINITE LOOP: Align startPage to index 0 of the list with Int.MAX_VALUE pages for infinite scrolling
    val listSize = videos.size
    val startPage = if (listSize > 0) {
        Int.MAX_VALUE / 2 - (Int.MAX_VALUE / 2 % listSize)
    } else 0
    val pagerState = rememberPagerState(
        initialPage = startPage,
        pageCount = { if (listSize > 0) Int.MAX_VALUE else 0 }
    )
    val realCurrentPage = if (videos.isNotEmpty()) pagerState.currentPage % videos.size else 0
    var isUIVisible by remember { mutableStateOf(true) }
    var videoToDelete by remember { mutableStateOf<VideoFile?>(null) }

    // Multi-gesture / Interactive Zoom and Fullscreen states
    var zoomScale by remember { mutableStateOf(1.0f) }
    var isFullscreen by remember { mutableStateOf(false) }
    var isMinimalTimelineHidden by remember { mutableStateOf(false) }
    val interactionFlow = remember { MutableSharedFlow<Unit>(extraBufferCapacity = 1) }
    var isPagerScrollAllowed by remember { mutableStateOf(true) }
    val coroutineScope = rememberCoroutineScope()

    val context = LocalContext.current

    val showOnboarding by viewModel.showOnboarding.collectAsStateWithLifecycle()

    // Observe sorting / filtering configurations from ViewModel
    val playbackMode by viewModel.playbackMode.collectAsStateWithLifecycle()
    val selectedFolders by viewModel.selectedFolders.collectAsStateWithLifecycle()
    val availableFolders by viewModel.availableFolders.collectAsStateWithLifecycle()
    val videoDisplayMode by viewModel.videoDisplayMode.collectAsStateWithLifecycle()

    // Player position parameters
    val activePlayer by viewModel.playerManager.activePlayer.collectAsStateWithLifecycle()
    val isAppVisible by viewModel.isAppVisible.collectAsStateWithLifecycle()

    var isActivePlayerPlaying by remember(activePlayer) { mutableStateOf(activePlayer?.isPlaying == true) }
    DisposableEffect(activePlayer) {
        if (activePlayer != null) {
            val listener = object : androidx.media3.common.Player.Listener {
                override fun onIsPlayingChanged(playing: Boolean) {
                    isActivePlayerPlaying = playing
                }
              }
              activePlayer?.addListener(listener)
              isActivePlayerPlaying = activePlayer?.isPlaying == true
              onDispose {
                  activePlayer?.removeListener(listener)
              }
        } else {
            isActivePlayerPlaying = false
            onDispose {}
        }
    }

    val pLifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(pLifecycleOwner, viewModel.playerManager) {
        val appLifecycleObserver = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) {
                // BACKGROUND FIX: Save playback progress and set app invisible on Stop to prevent render iterations
                viewModel.savePlaybackState(realCurrentPage)
                viewModel.setAppVisible(false)
            } else if (event == androidx.lifecycle.Lifecycle.Event.ON_START) {
                // BACKGROUND FIX: Restore progress and resume on Start
                viewModel.setAppVisible(true)
                viewModel.restorePlaybackState()
                if (!viewModel.showOnboarding.value) {
                    viewModel.playerManager.activePlayer.value?.play()
                }
            }
        }
        val videoManagerObserver = viewModel.playerManager
        pLifecycleOwner.lifecycle.addObserver(appLifecycleObserver)
        pLifecycleOwner.lifecycle.addObserver(videoManagerObserver)
        onDispose {
            pLifecycleOwner.lifecycle.removeObserver(appLifecycleObserver)
            pLifecycleOwner.lifecycle.removeObserver(videoManagerObserver)
        }
    }

    // HUD transient notification overlay parameters
    var hudMessage by remember { mutableStateOf<String?>(null) }
    var hudTrigger by remember { mutableStateOf(false) }

    // Ensure system elements are always visible (Time, Wifi, battery etc)
    LaunchedEffect(Unit) {
        val window = (context as? Activity)?.window
        if (window != null) {
            val controller = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
            controller.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        }
    }

    // Synchronize current active page with preloading queue & reset scale scale
    LaunchedEffect(pagerState.currentPage, videos, showOnboarding) {
        viewModel.playerManager.onPageSelected(
            currentIndex = pagerState.currentPage,
            videos = videos,
            playImmediately = !showOnboarding
        )
        if (videos.isNotEmpty()) {
            viewModel.markVideoAsViewed(videos[realCurrentPage].id)
        }
        zoomScale = 1.0f
        interactionFlow.tryEmit(Unit)
    }

    // Active auto-hide inactivity rules system using non-recomposing flow triggers
    LaunchedEffect(isUIVisible, activePlayer, isActivePlayerPlaying, isAppVisible) {
        if (isUIVisible && isActivePlayerPlaying && isAppVisible) {
            merge(
                interactionFlow,
                flowOf(Unit)
            ).collectLatest {
                delay(5000)
                isUIVisible = false
            }
        }
    }

    LaunchedEffect(isUIVisible) {
        if (isUIVisible) {
            isMinimalTimelineHidden = false
        }
    }

    // Sync HUD status toaster updates
    LaunchedEffect(hudTrigger) {
        if (hudMessage != null) {
            delay(1600)
            hudMessage = null
        }
    }

    val onSeek: (Long) -> Unit = { newPosition ->
        activePlayer?.let { p ->
            p.seekTo(newPosition)
            interactionFlow.tryEmit(Unit)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // High Performance Vertical infinite scroll feed
        VerticalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = zoomScale == 1.0f && isPagerScrollAllowed,
            key = { index ->
                if (videos.isNotEmpty()) "${videos[index % videos.size].id}_$index" else index
            }
        ) { page ->
            if (videos.isNotEmpty()) {
                val realPage = page % videos.size
                val video = videos[realPage]
                val isActive = page == pagerState.currentPage

                VideoPlayerItem(
                    video = video,
                    isActive = isActive,
                    isUIVisible = isUIVisible,
                    videoDisplayMode = videoDisplayMode,
                    playerManager = viewModel.playerManager,
                    isAppVisible = isAppVisible,
                    onSingleTap = { 
                        isUIVisible = !isUIVisible
                        interactionFlow.tryEmit(Unit)
                    },
                    onDoubleTap = { 
                        isPagerScrollAllowed = false
                        onFavoriteToggle(video)
                        interactionFlow.tryEmit(Unit)
                        coroutineScope.launch {
                            kotlinx.coroutines.delay(600)
                            isPagerScrollAllowed = true
                        }
                    },
                    onZoomChanged = { scale ->
                        zoomScale = scale
                        interactionFlow.tryEmit(Unit)
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // Overlay Header row & chip filter switcher
        AnimatedVisibility(
            visible = isUIVisible,
            enter = fadeIn() + slideInVertically(initialOffsetY = { -it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { -it }),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Column(
                modifier = Modifier
                    .statusBarsPadding()
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 16.dp)
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                event.changes.forEach { it.consume() }
                            }
                        }
                    },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Elegant row containing centralized ALL FILES/FAVORITES switcher and directory multi-filter icon next to it
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(24.dp))
                            .padding(horizontal = 4.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        FilterTabButton(
                            selected = !showFavoritesOnly,
                            label = "ALL FILES",
                            onClick = { 
                                if (showFavoritesOnly) onToggleFilter()
                                interactionFlow.tryEmit(Unit)
                            }
                        )
                        FilterTabButton(
                            selected = showFavoritesOnly,
                            label = "FAVORITES",
                            onClick = { 
                                if (!showFavoritesOnly) onToggleFilter()
                                interactionFlow.tryEmit(Unit)
                            }
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    IconButton(
                        onClick = { 
                            onShowFolderSelection()
                            interactionFlow.tryEmit(Unit)
                        },
                        modifier = Modifier
                            .size(38.dp)
                            .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(24.dp))
                    ) {
                        Icon(
                            imageVector = Icons.Default.VideoLibrary,
                            contentDescription = "Directory Multi-Filter",
                            tint = if (selectedFolders.isNotEmpty()) Color(0xFFFFCC00) else Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        // Action Overlay floating sidebar (right alignment)
        if (videos.isNotEmpty()) {
            val currentVideo = videos[realCurrentPage]

            AnimatedVisibility(
                visible = isUIVisible,
                enter = fadeIn() + slideInHorizontally(initialOffsetX = { it }),
                exit = fadeOut() + slideOutHorizontally(targetOffsetX = { it }),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 8.dp)
            ) {
                RightActionButtons(
                    video = currentVideo,
                    playbackMode = playbackMode,
                    videoDisplayMode = videoDisplayMode,
                    onFavoriteClick = { 
                        onFavoriteToggle(currentVideo) 
                        interactionFlow.tryEmit(Unit)
                    },
                    onShareClick = { 
                        onShareVideo(currentVideo) 
                        interactionFlow.tryEmit(Unit)
                    },
                    onPlaybackModeClick = {
                        val nextMode = when (playbackMode) {
                            PlaybackMode.SEQUENTIAL -> PlaybackMode.RANDOM
                            PlaybackMode.RANDOM -> PlaybackMode.NEWEST_FIRST
                            PlaybackMode.NEWEST_FIRST -> PlaybackMode.OLDEST_FIRST
                            PlaybackMode.OLDEST_FIRST -> PlaybackMode.SEQUENTIAL
                        }
                        viewModel.setPlaybackMode(nextMode)
                        hudMessage = when (nextMode) {
                            PlaybackMode.SEQUENTIAL -> "Mode: Sequential List"
                            PlaybackMode.RANDOM -> "Mode: Shuffled Random"
                            PlaybackMode.NEWEST_FIRST -> "Sort: Newest First"
                            PlaybackMode.OLDEST_FIRST -> "Sort: Oldest First"
                        }
                        hudTrigger = !hudTrigger
                        interactionFlow.tryEmit(Unit)
                    },
                    onVideoDisplayModeClick = {
                        viewModel.toggleVideoDisplayMode()
                        val nextModeName = when (videoDisplayMode) {
                            VideoDisplayMode.FILL -> "Fit Aspect"
                            VideoDisplayMode.FIT -> "Stretch to Match"
                            VideoDisplayMode.STRETCH -> "Fill Screen"
                        }
                        hudMessage = "Display: $nextModeName"
                        hudTrigger = !hudTrigger
                        interactionFlow.tryEmit(Unit)
                    },
                    onDeleteClick = { 
                        videoToDelete = currentVideo 
                        interactionFlow.tryEmit(Unit)
                    }
                )
            }

            // Bottom unified metadata, timeline, and control panel on a smooth vertical gradient overlay
            AnimatedVisibility(
                visible = isUIVisible,
                enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.35f),
                                    Color.Black.copy(alpha = 0.75f),
                                    Color.Black.copy(alpha = 0.95f)
                                )
                            )
                        )
                        .navigationBarsPadding()
                        .padding(bottom = 16.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            // Intercept taps so they don't propagate to parent list items but allow dragging/scrolling
                        },
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // 1. Metadata Panel (Internal padding aligns cleanly)
                    MetadataPanel(
                        video = currentVideo,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // 2. Timeline (VideoProgressBar) - High-efficiency player-bound state isolation
                    VideoProgressBar(
                        player = activePlayer,
                        onSeek = onSeek,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // 4. Enhanced Minimal Fullscreen Timeline overlay (High-efficiency player-bound state isolation)
        val isMinimalTimelineVisible = !isUIVisible && !isMinimalTimelineHidden
        MinimalTimelineOverlay(
            player = activePlayer,
            isVisible = isMinimalTimelineVisible,
            onHideClick = { isMinimalTimelineHidden = true },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
        )

        // Center HUD transient popup banner
        AnimatedVisibility(
            visible = hudMessage != null,
            enter = fadeIn() + scaleIn(initialScale = 0.8f),
            exit = fadeOut() + scaleOut(targetScale = 0.8f),
            modifier = Modifier.align(Alignment.Center)
        ) {
            hudMessage?.let { msg ->
                Box(
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = msg,
                        color = Color(0xFFFFCC00),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }



        // Destructive deletion dialog confirmation
        videoToDelete?.let { video ->
            DeleteConfirmDialog(
                videoTitle = video.title,
                onConfirm = {
                    onDeleteVideo(video) {
                        videoToDelete = null
                    }
                },
                onDismiss = { videoToDelete = null }
            )
        }

        // Onboarding gestures tutorial overlay popup
        AnimatedVisibility(
            visible = showOnboarding,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            OnboardingTutorialOverlay(
                onDismiss = { viewModel.dismissOnboarding() }
            )
        }
    }
}

@Composable
fun FolderChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .background(
                color = if (isSelected) Color(0xFFFFCC00) else Color.Black.copy(alpha = 0.5f),
                shape = RoundedCornerShape(16.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (isSelected) Color.Black else Color.White.copy(alpha = 0.8f),
            fontSize = 12.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
private fun FilterTabButton(
    selected: Boolean,
    label: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .background(
                color = if (selected) Color.White else Color.Transparent,
                shape = RoundedCornerShape(20.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (selected) Color.Black else Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
    }
}

@Composable
fun PermissionRequiredLayout(
    permissionName: String,
    onRequestPermission: () -> Unit,
    onSkipWithDemo: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AmoledBlack)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.VideoLibrary,
            contentDescription = "Permission requested",
            tint = Color.White,
            modifier = Modifier.size(80.dp)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "ReelsFlow Player requires $permissionName Permission",
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "To experience immersive scroll-to-play on your offline local videos, ReelsFlow Player needs authority to index media files directly from your device storage. Your files stay 100% private and offline.",
            color = AmoledTextSecondary,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp
        )
        Spacer(modifier = Modifier.height(36.dp))
        Button(
            onClick = onRequestPermission,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.White,
                contentColor = Color.Black
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("btn_grant_permission")
        ) {
            Text("Grant Storage Access", fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedButton(
            onClick = onSkipWithDemo,
            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.5f)),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("btn_skip_with_demo")
        ) {
            Text("تشغيل الفيديوهات التجريبية (Demo)", fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
    }
}

@Composable
fun EmptyStorageLayout(
    onRefreshClick: () -> Unit,
    onChooseFolderClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AmoledBlack)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.VideoLibrary,
            contentDescription = "No local videos",
            tint = Color.White.copy(alpha = 0.8f),
            modifier = Modifier.size(80.dp)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Open Storage is Empty",
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "We couldn't locate any playable offline video files on your device. \n\nPlace your MP4/MKV video files inside your Downloads, Movies, or Camera folder, then refresh below to resume playing!",
            color = AmoledTextSecondary,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp
        )
        Spacer(modifier = Modifier.height(36.dp))

        Column(
            modifier = Modifier.fillMaxWidth().widthIn(max = 320.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Button(
                onClick = onRefreshClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Color.Black
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = "Refresh", modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Rescan Device Media", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }

            OutlinedButton(
                onClick = onChooseFolderClick,
                border = ButtonDefaults.outlinedButtonBorder(true),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Text("Choose Folder", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }
    }
}

@Composable
fun FilteredEmptyStateLayout(
    showFavoritesOnly: Boolean,
    selectedFolders: Set<String>,
    onBackToFeed: () -> Unit,
    onRefreshClick: () -> Unit,
    onChooseFolderClick: () -> Unit
) {
    val isFolderFilterActive = selectedFolders.isNotEmpty()
    val isFavoritesFilterActive = showFavoritesOnly

    val title = when {
        isFolderFilterActive && isFavoritesFilterActive -> "No Favourites in Selected Folders"
        isFolderFilterActive -> "Empty Folder"
        else -> "No Favourited Videos"
    }

    val description = when {
        isFolderFilterActive && isFavoritesFilterActive -> "Disable your filters or add some videos in this folder to your favourites to view them here!"
        isFolderFilterActive -> "We couldn't find any videos inside the selected folder: ${selectedFolders.joinToString(", ")}. Try rescanning files or selecting a different folder."
        else -> "Double-tap any local video while playing in the feed or click the Star icon on the right side to add it to your premium library!"
    }

    val icon = if (isFolderFilterActive) Icons.Filled.Folder else Icons.Filled.Favorite

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AmoledBlack)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = "Empty state icon",
            tint = Color.White.copy(alpha = 0.8f),
            modifier = Modifier.size(80.dp)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = title,
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = description,
            color = AmoledTextSecondary,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp
        )
        Spacer(modifier = Modifier.height(36.dp))

        Column(
            modifier = Modifier.fillMaxWidth().widthIn(max = 320.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Button(
                onClick = onRefreshClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Color.Black
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = "Refresh", modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Rescan Device Media", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }

            OutlinedButton(
                onClick = onBackToFeed,
                border = ButtonDefaults.outlinedButtonBorder(true),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Text("Back to Feed", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }

            OutlinedButton(
                onClick = onChooseFolderClick,
                border = ButtonDefaults.outlinedButtonBorder(true),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Text("Choose Folder", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }
    }
}

@Composable
fun OnboardingTutorialOverlay(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f))
            .clickable(enabled = false) {} // block background clicks
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 440.dp)
                .background(AmoledGray, RoundedCornerShape(28.dp))
                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(28.dp))
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Header Section
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(Color.White.copy(alpha = 0.08f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.VideoLibrary,
                    contentDescription = "Welcome to ReelsFlow Player",
                    tint = AccentColor,
                    modifier = Modifier.size(28.dp)
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "Welcome to ReelsFlow Player",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "Learn these quick gestures to master your offline immersive experience.",
                    color = AmoledTextSecondary,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Gestures List
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                TutorialGestureRow(
                    icon = Icons.Default.SwapVert,
                    accentColor = Color(0xFF34C759),
                    title = "Smart Swipe Feed",
                    description = "Swipe up or down to play next or previous offline videos in your library."
                )

                TutorialGestureRow(
                    icon = Icons.Default.TouchApp,
                    accentColor = Color(0xFF007AFF),
                    title = "Double-Tap to Favorite",
                    description = "Double-tap anywhere during play to star and add video to Favorites."
                )

                TutorialGestureRow(
                    icon = Icons.Default.Bolt,
                    accentColor = AccentColor,
                    title = "Hold for 2X Speed",
                    description = "Press and hold the screen to boost playback speed dynamically to 2X."
                )

                TutorialGestureRow(
                    icon = Icons.Default.ZoomIn,
                    accentColor = Color(0xFFAF52DE),
                    title = "Pinch to Zoom 4X",
                    description = "Pinch outwards with two fingers to crop or inspect details in up to 4x."
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Color.Black
                ),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("btn_onboarding_get_started")
            ) {
                Text(
                    text = "Begin Experience",
                    color = Color.Black,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
        }
    }
}

@Composable
private fun TutorialGestureRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accentColor: Color,
    title: String,
    description: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .padding(top = 2.dp)
                .size(36.dp)
                .background(accentColor.copy(alpha = 0.12f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(18.dp)
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = description,
                color = AmoledTextSecondary,
                fontSize = 12.sp,
                lineHeight = 16.sp
            )
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}

@Composable
private fun MinimalTimelineOverlay(
    player: androidx.media3.exoplayer.ExoPlayer?,
    isVisible: Boolean,
    onHideClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = isVisible && player != null,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        if (player == null) return@AnimatedVisibility

        var currentPosition by remember(player) { mutableStateOf(player.currentPosition) }
        var videoDuration by remember(player) { mutableStateOf(if (player.duration > 0) player.duration else 0L) }
        var isDragging by remember { mutableStateOf(false) }
        var sliderValue by remember { mutableStateOf(0f) }

        LaunchedEffect(currentPosition) {
            if (!isDragging) {
                sliderValue = currentPosition.toFloat()
            }
        }

        val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
        LaunchedEffect(player, lifecycleOwner, isDragging) {
            lifecycleOwner.lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                while (true) {
                    if (!isDragging) {
                        currentPosition = player.currentPosition
                        val d = player.duration
                        if (d > 0) {
                            videoDuration = d
                        }
                    }
                    delay(200)
                }
            }
        }

        val safeDuration = if (videoDuration > 0) videoDuration else 1L
        val displayValue = sliderValue

        val formattedPosition = formatTime(displayValue.toLong())
        val formattedDuration = formatTime(safeDuration)
        
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$formattedPosition / $formattedDuration",
                    color = if (isDragging) Color(0xFFFFCC00) else Color.White.copy(alpha = 0.5f),
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 11.sp
                )

                IconButton(
                    onClick = onHideClick,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.VisibilityOff,
                        contentDescription = "Hide Timeline",
                        tint = Color.White.copy(alpha = 0.50f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            var barWidth by remember { mutableStateOf(0) }
            val progressFraction = if (safeDuration > 0) displayValue / safeDuration else 0f

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp)
                    .onSizeChanged { barWidth = it.width }
                    .pointerInput(player, safeDuration) {
                        detectTapGestures(
                            onPress = { offset ->
                                if (barWidth > 0) {
                                    isDragging = true
                                    val fraction = (offset.x / barWidth).coerceIn(0f, 1f)
                                    sliderValue = fraction * safeDuration
                                    player.seekTo(sliderValue.toLong())
                                    try {
                                        awaitRelease()
                                    } finally {
                                        isDragging = false
                                        player.seekTo(sliderValue.toLong())
                                    }
                                }
                            }
                        )
                    }
                    .pointerInput(player, safeDuration) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                if (barWidth > 0) {
                                    isDragging = true
                                    val fraction = (offset.x / barWidth).coerceIn(0f, 1f)
                                    sliderValue = fraction * safeDuration
                                    player.seekTo(sliderValue.toLong())
                                }
                            },
                            onDragEnd = {
                                isDragging = false
                                player.seekTo(sliderValue.toLong())
                            },
                            onDragCancel = {
                                isDragging = false
                            },
                            onDrag = { change, dragAmount ->
                                if (barWidth > 0) {
                                    change.consume()
                                    val fraction = (change.position.x / barWidth).coerceIn(0f, 1f)
                                    sliderValue = fraction * safeDuration
                                }
                            }
                        )
                    },
                contentAlignment = Alignment.CenterStart
            ) {
                // Background Track
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (isDragging) 4.dp else 2.dp)
                        .background(Color.White.copy(alpha = 0.15f), RoundedCornerShape(2.dp))
                )
                // Active Played Track
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progressFraction.coerceIn(0f, 1f))
                        .height(if (isDragging) 4.dp else 2.dp)
                        .background(if (isDragging) Color(0xFFFFCC00) else Color.White.copy(alpha = 0.55f), RoundedCornerShape(2.dp))
                )
            }
        }
    }
}
