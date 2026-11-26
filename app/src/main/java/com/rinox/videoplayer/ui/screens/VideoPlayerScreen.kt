package com.rinox.videoplayer.ui.screens

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.os.Build
import android.util.Rational
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.rinox.videoplayer.data.AppSettings
import com.rinox.videoplayer.data.SettingsManager
import com.rinox.videoplayer.data.VideoItem
import com.rinox.videoplayer.player.RinoxPlayerEngine
import com.rinox.videoplayer.player.TrackInfoItem
import com.rinox.videoplayer.ui.theme.DarkCard
import com.rinox.videoplayer.ui.theme.RinoxCyan
import com.rinox.videoplayer.utils.FormatUtils
import kotlinx.coroutines.delay
import kotlin.math.abs
@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerScreen(
    playlist: List<VideoItem>,
    initialIndex: Int,
    settings: AppSettings,
    settingsManager: SettingsManager,
    accentColor: Color,
    isInPipMode: Boolean,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val lifecycleOwner = LocalLifecycleOwner.current

    var currentIndex by remember { mutableIntStateOf(initialIndex.coerceIn(0, (playlist.size - 1).coerceAtLeast(0))) }
    val currentVideo = playlist.getOrNull(currentIndex) ?: return

    val engine = remember { RinoxPlayerEngine(context) }
    var decoderMode by remember { mutableStateOf(settings.defaultDecoder) }
    var isPlaying by remember { mutableStateOf(true) }
    var currentPosMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(currentVideo.durationMs.coerceAtLeast(1L)) }
    var controlsVisible by remember { mutableStateOf(true) }
    var isLocked by remember { mutableStateOf(false) }
    var backgroundPlayEnabled by remember { mutableStateOf(false) }
    var playbackSpeed by remember { mutableFloatStateOf(1.0f) }
    var is2xHolding by remember { mutableStateOf(false) }

    val aspectModes = listOf(
        AspectRatioFrameLayout.RESIZE_MODE_FIT to "Fit",
        AspectRatioFrameLayout.RESIZE_MODE_FILL to "Stretch",
        AspectRatioFrameLayout.RESIZE_MODE_ZOOM to "Crop",
        AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH to "100%"
    )
    var aspectIndex by remember { mutableIntStateOf(0) }

    // Tap-seek accumulator (supports up to 100 consecutive taps)
    var accumulatedSeekSec by remember { mutableIntStateOf(0) }
    var lastTapTimestamp by remember { mutableLongStateOf(0L) }

    // Swipe HUD indicators
    var hudText by remember { mutableStateOf<String?>(null) }
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxSystemVol = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }
    var volumePercent by remember {
        mutableIntStateOf(((audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) * 100) / maxSystemVol).coerceIn(0, 100))
    }
    var brightnessPercent by remember { mutableIntStateOf(50) }

    // Track dialogs
    var showAudioDialog by remember { mutableStateOf(false) }
    var showSubDialog by remember { mutableStateOf(false) }
    var showSpeedDialog by remember { mutableStateOf(false) }
    var audioTracks by remember { mutableStateOf<List<TrackInfoItem>>(emptyList()) }
    var subTracks by remember { mutableStateOf<List<TrackInfoItem>>(emptyList()) }
    // Smart Auto-Orientation: Reels/TikTok (Portrait) stays Portrait, Movies (Landscape) go Landscape
    LaunchedEffect(currentVideo.path) {
        activity?.requestedOrientation = if (currentVideo.height > currentVideo.width && currentVideo.width > 0) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        } else {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
    }

    // Immersive Full-Screen Hide System Bars
    DisposableEffect(Unit) {
        val window = activity?.window
        if (window != null) {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            val controller = WindowInsetsControllerCompat(window, window.decorView)
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            if (window != null) {
                val controller = WindowInsetsControllerCompat(window, window.decorView)
                controller.show(WindowInsetsCompat.Type.systemBars())
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            engine.release()
        }
    }

    // Zero Ghost-Audio Guarantee on Minimize/Stop
    DisposableEffect(lifecycleOwner, backgroundPlayEnabled, isInPipMode) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && !backgroundPlayEnabled && !isInPipMode) {
                engine.exoPlayer?.pause()
                isPlaying = false
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Build & Prepare Hybrid Player
    LaunchedEffect(currentVideo.path, decoderMode) {
        val savedPos = if (settings.resumePlayback) {
            context.getSharedPreferences("rinox_positions", Context.MODE_PRIVATE)
                .getLong(currentVideo.path, 0L)
        } else 0L

        val player = engine.buildPlayer(
            decoderMode = decoderMode,
            onErrorFallback = { fallbackMode ->
                decoderMode = fallbackMode
                Toast.makeText(context, "Switched to SW FFmpeg Decoder", Toast.LENGTH_SHORT).show()
            },
            onVideoEnded = {
                context.getSharedPreferences("rinox_positions", Context.MODE_PRIVATE)
                    .edit().remove(currentVideo.path).apply()
                if (currentIndex + 1 < playlist.size) {
                    currentIndex += 1
                } else {
                    onClose()
                }
            },
            onTracksChanged = {
                audioTracks = engine.getTracks(C.TRACK_TYPE_AUDIO)
                subTracks = engine.getTracks(C.TRACK_TYPE_TEXT)
            }
        )
        engine.prepareMedia(currentVideo.uri, currentVideo.path, savedPos, settings.autoLoadSubtitles)
        engine.applySafeAudioBoost(volumePercent)
        settingsManager.markVideoWatched(currentVideo.path)
        settingsManager.saveLastPlayedPath(currentVideo.path)
    }
    // Live Progress & Position Saver
    LaunchedEffect(currentVideo.path) {
        while (true) {
            delay(400L)
            engine.exoPlayer?.let { p ->
                isPlaying = p.isPlaying
                currentPosMs = p.currentPosition.coerceAtLeast(0L)
                if (p.duration > 0L) durationMs = p.duration
                if (p.isPlaying && currentPosMs > 2000L) {
                    context.getSharedPreferences("rinox_positions", Context.MODE_PRIVATE)
                        .edit().putLong(currentVideo.path, currentPosMs).apply()
                }
            }
            if (accumulatedSeekSec != 0 && System.currentTimeMillis() - lastTapTimestamp > 900L) {
                accumulatedSeekSec = 0
            }
        }
    }

    // Auto-hide controls after 3.5 seconds
    LaunchedEffect(controlsVisible, isPlaying) {
        if (controlsVisible && isPlaying) {
            delay(3500L)
            controlsVisible = false
        }
    }

    BackHandler {
        if (isLocked) {
            Toast.makeText(context, "Unlock screen first", Toast.LENGTH_SHORT).show()
        } else {
            engine.release()
            onClose()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Video Surface
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    resizeMode = aspectModes[aspectIndex].first
                    player = engine.exoPlayer
                }
            },
            update = { view ->
                if (view.player != engine.exoPlayer) {
                    view.player = engine.exoPlayer
                }
                view.resizeMode = aspectModes[aspectIndex].first
            },
            modifier = Modifier.fillMaxSize()
        )
        // Full-Screen Gesture Layer (Disabled when locked or in PiP)
        if (!isLocked && !isInPipMode) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(currentVideo.path, settings.seekStepSeconds, settings.enableEdgeSwipes) {
                        detectTapGestures(
                            onTap = { offset ->
                                val now = System.currentTimeMillis()
                                val widthPx = size.width.toFloat()
                                if (now - lastTapTimestamp < 650L && (offset.x < widthPx * 0.35f || offset.x > widthPx * 0.65f)) {
                                    // Consecutive tap-seek (up to 100 taps!)
                                    val dir = if (offset.x > widthPx * 0.65f) 1 else -1
                                    val nextAcc = (accumulatedSeekSec + dir * settings.seekStepSeconds)
                                        .coerceIn(-100 * settings.seekStepSeconds, 100 * settings.seekStepSeconds)
                                    accumulatedSeekSec = nextAcc
                                    lastTapTimestamp = now
                                    val targetPos = ((engine.exoPlayer?.currentPosition ?: 0L) + dir * settings.seekStepSeconds * 1000L)
                                        .coerceIn(0L, durationMs)
                                    engine.exoPlayer?.seekTo(targetPos)
                                    currentPosMs = targetPos
                                    hudText = if (accumulatedSeekSec > 0) "+${accumulatedSeekSec}s" else "${accumulatedSeekSec}s"
                                } else {
                                    lastTapTimestamp = now
                                    accumulatedSeekSec = 0
                                    controlsVisible = !controlsVisible
                                }
                            },
                            onDoubleTap = { offset ->
                                val widthPx = size.width.toFloat()
                                lastTapTimestamp = System.currentTimeMillis()
                                if (offset.x > widthPx * 0.65f) {
                                    accumulatedSeekSec = (accumulatedSeekSec + settings.seekStepSeconds)
                                        .coerceAtMost(100 * settings.seekStepSeconds)
                                    val targetPos = ((engine.exoPlayer?.currentPosition ?: 0L) + settings.seekStepSeconds * 1000L)
                                        .coerceIn(0L, durationMs)
                                    engine.exoPlayer?.seekTo(targetPos)
                                    currentPosMs = targetPos
                                    hudText = "+${accumulatedSeekSec}s"
                                } else if (offset.x < widthPx * 0.35f) {
                                    accumulatedSeekSec = (accumulatedSeekSec - settings.seekStepSeconds)
                                        .coerceAtLeast(-100 * settings.seekStepSeconds)
                                    val targetPos = ((engine.exoPlayer?.currentPosition ?: 0L) - settings.seekStepSeconds * 1000L)
                                        .coerceIn(0L, durationMs)
                                    engine.exoPlayer?.seekTo(targetPos)
                                    currentPosMs = targetPos
                                    hudText = "${accumulatedSeekSec}s"
                                } else {
                                    engine.exoPlayer?.let { p ->
                                        if (p.isPlaying) p.pause() else p.play()
                                    }
                                }
                            },
                            onLongPress = {
                                is2xHolding = true
                                engine.exoPlayer?.setPlaybackSpeed(2.0f)
                                hudText = "2.0X Speed"
                            },
                            onPress = {
                                tryAwaitRelease()
                                if (is2xHolding) {
                                    is2xHolding = false
                                    engine.exoPlayer?.setPlaybackSpeed(playbackSpeed)
                                    hudText = null
                                }
                            }
                        )
                    }
                    .pointerInput(settings.enableEdgeSwipes) {
                        var dragMode = 0 // 1 = Brightness, 2 = Volume, 3 = Seek
                        detectDragGestures(
                            onDragStart = { startOffset ->
                                val w = size.width.toFloat()
                                dragMode = when {
                                    settings.enableEdgeSwipes && startOffset.x < w * 0.25f -> 1
                                    settings.enableEdgeSwipes && startOffset.x > w * 0.75f -> 2
                                    else -> 3
                                }
                            },
                            onDragEnd = { hudText = null; dragMode = 0 },
                            onDragCancel = { hudText = null; dragMode = 0 },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                val w = size.width.toFloat().coerceAtLeast(1f)
                                val h = size.height.toFloat().coerceAtLeast(1f)
                                when (dragMode) {
                                    1 -> {
                                        val delta = (-dragAmount.y / h * 100f).toInt()
                                        brightnessPercent = (brightnessPercent + delta).coerceIn(1, 100)
                                        activity?.window?.attributes = activity?.window?.attributes?.apply {
                                            screenBrightness = brightnessPercent / 100f
                                        }
                                        hudText = "Brightness: $brightnessPercent%"
                                    }
                                    2 -> {
                                        val delta = (-dragAmount.y / h * 150f).toInt()
                                        volumePercent = (volumePercent + delta).coerceIn(0, 200)
                                        val sysVol = ((volumePercent.coerceAtMost(100) * maxSystemVol) / 100)
                                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, sysVol, 0)
                                        engine.applySafeAudioBoost(volumePercent)
                                        hudText = "Volume: $volumePercent%"
                                    }
                                    3 -> {
                                        if (abs(dragAmount.x) > abs(dragAmount.y)) {
                                            val seekDeltaMs = ((dragAmount.x / w) * 60000L).toLong()
                                            val newPos = ((engine.exoPlayer?.currentPosition ?: 0L) + seekDeltaMs)
                                                .coerceIn(0L, durationMs)
                                            engine.exoPlayer?.seekTo(newPos)
                                            currentPosMs = newPos
                                            hudText = FormatUtils.formatDuration(newPos)
                                        }
                                    }
                                }
                            }
                        )
                    }
            )
        }
        // Center HUD Overlay (Seek / Brightness / Volume / 2.0X)
        if (hudText != null && !isInPipMode) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xCC000000))
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            ) {
                Text(text = hudText!!, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }

        // Lock Button when Screen is Locked
        if (isLocked && !isInPipMode) {
            IconButton(
                onClick = { isLocked = false; controlsVisible = true },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(20.dp)
                    .clip(CircleShape)
                    .background(Color(0xAA000000))
            ) {
                Icon(Icons.Rounded.Lock, contentDescription = "Unlock", tint = RinoxCyan)
            }
        }

        // MX-Style Overlay Controls
        AnimatedVisibility(
            visible = controlsVisible && !isLocked && !isInPipMode,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0x55000000))
            ) {
                // Top Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { engine.release(); onClose() }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                    Text(
                        text = currentVideo.displayName,
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(horizontal = 6.dp)
                    )
                    TextButton(onClick = {
                        decoderMode = when (decoderMode) {
                            "HW" -> "HW+"
                            "HW+" -> "SW"
                            else -> "HW"
                        }
                    }) {
                        Text(decoderMode, color = RinoxCyan, fontWeight = FontWeight.Bold)
                    }
                    IconButton(onClick = { showAudioDialog = true }) {
                        Icon(Icons.Rounded.Audiotrack, contentDescription = "Audio", tint = Color.White)
                    }
                    IconButton(onClick = { showSubDialog = true }) {
                        Icon(Icons.Rounded.Subtitles, contentDescription = "Subtitles", tint = Color.White)
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        IconButton(onClick = {
                            try {
                                val params = PictureInPictureParams.Builder()
                                    .setAspectRatio(Rational(16, 9))
                                    .build()
                                activity?.enterPictureInPictureMode(params)
                            } catch (_: Exception) {}
                        }) {
                            Icon(Icons.Rounded.PictureInPictureAlt, contentDescription = "PiP", tint = Color.White)
                        }
                    }
                }
                // Left Floating Quick Toolbar
                Column(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 12.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color(0x99000000))
                        .padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    IconButton(onClick = { isLocked = true; controlsVisible = false }) {
                        Icon(Icons.Rounded.LockOpen, contentDescription = "Lock", tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                    TextButton(onClick = { showSpeedDialog = true }) {
                        Text("${playbackSpeed}x", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    IconButton(onClick = {
                        backgroundPlayEnabled = !backgroundPlayEnabled
                        Toast.makeText(
                            context,
                            if (backgroundPlayEnabled) "Background Audio ON" else "Background Audio OFF",
                            Toast.LENGTH_SHORT
                        ).show()
                    }) {
                        Icon(
                            Icons.Rounded.Headphones,
                            contentDescription = "Bg Play",
                            tint = if (backgroundPlayEnabled) RinoxCyan else Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Bottom Seekbar & Transport Bar
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(FormatUtils.formatDuration(currentPosMs), color = Color.White, fontSize = 12.sp)
                        Slider(
                            value = currentPosMs.toFloat(),
                            onValueChange = { pos ->
                                currentPosMs = pos.toLong()
                                engine.exoPlayer?.seekTo(currentPosMs)
                            },
                            valueRange = 0f..durationMs.toFloat().coerceAtLeast(1f),
                            colors = SliderDefaults.colors(thumbColor = accentColor, activeTrackColor = accentColor),
                            modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                        )
                        Text(FormatUtils.formatDuration(durationMs), color = Color.White, fontSize = 12.sp)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = {
                            activity?.requestedOrientation =
                                if (activity?.requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE)
                                    ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                                else ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                        }) {
                            Icon(Icons.Rounded.ScreenRotation, contentDescription = "Rotate", tint = Color.White)
                        }
                        IconButton(onClick = { if (currentIndex > 0) currentIndex -= 1 }) {
                            Icon(Icons.Rounded.SkipPrevious, contentDescription = "Prev", tint = Color.White, modifier = Modifier.size(32.dp))
                        }
                        IconButton(
                            onClick = { engine.exoPlayer?.let { if (it.isPlaying) it.pause() else it.play() } },
                            modifier = Modifier.size(54.dp).clip(CircleShape).background(accentColor)
                        ) {
                            Icon(
                                if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = "PlayPause",
                                tint = Color.White,
                                modifier = Modifier.size(34.dp)
                            )
                        }
                        IconButton(onClick = { if (currentIndex + 1 < playlist.size) currentIndex += 1 }) {
                            Icon(Icons.Rounded.SkipNext, contentDescription = "Next", tint = Color.White, modifier = Modifier.size(32.dp))
                        }
                        TextButton(onClick = { aspectIndex = (aspectIndex + 1) % aspectModes.size }) {
                            Text(aspectModes[aspectIndex].second, color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
    if (showSpeedDialog) {
        AlertDialog(
            onDismissRequest = { showSpeedDialog = false },
            containerColor = DarkCard,
            title = { Text("Playback Speed", color = Color.White) },
            text = {
                Column {
                    listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f, 3.0f, 4.0f).forEach { spd ->
                        TextButton(onClick = {
                            playbackSpeed = spd
                            engine.exoPlayer?.setPlaybackSpeed(spd)
                            showSpeedDialog = false
                        }) {
                            Text("${spd}x", color = if (playbackSpeed == spd) RinoxCyan else Color.White)
                        }
                    }
                }
            },
            confirmButton = {}
        )
    }

    if (showAudioDialog) {
        AlertDialog(
            onDismissRequest = { showAudioDialog = false },
            containerColor = DarkCard,
            title = { Text("Audio Tracks", color = Color.White) },
            text = {
                Column {
                    if (audioTracks.isEmpty()) Text("Default Audio Track", color = Color.Gray)
                    audioTracks.forEach { tr ->
                        TextButton(onClick = {
                            engine.selectTrack(C.TRACK_TYPE_AUDIO, tr.groupIndex, tr.trackIndex)
                            showAudioDialog = false
                        }) {
                            Text(tr.label, color = if (tr.isSelected) RinoxCyan else Color.White)
                        }
                    }
                }
            },
            confirmButton = {}
        )
    }

    if (showSubDialog) {
        AlertDialog(
            onDismissRequest = { showSubDialog = false },
            containerColor = DarkCard,
            title = { Text("Subtitles", color = Color.White) },
            text = {
                Column {
                    TextButton(onClick = {
                        engine.disableTrackType(C.TRACK_TYPE_TEXT)
                        showSubDialog = false
                    }) { Text("Disable Subtitles", color = Color.LightGray) }
                    subTracks.forEach { tr ->
                        TextButton(onClick = {
                            engine.selectTrack(C.TRACK_TYPE_TEXT, tr.groupIndex, tr.trackIndex)
                            showSubDialog = false
                        }) {
                            Text(tr.label, color = if (tr.isSelected) RinoxCyan else Color.White)
                        }
                    }
                }
            },
            confirmButton = {}
        )
    }
}
