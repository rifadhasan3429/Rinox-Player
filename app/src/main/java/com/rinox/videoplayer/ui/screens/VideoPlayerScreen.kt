package com.rinox.videoplayer.ui.screens

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Color as AndroidColor
import android.media.AudioManager
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Build
import android.util.Rational
import android.util.TypedValue
import android.view.KeyEvent
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
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
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.text.CueGroup
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.SubtitleView
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
import kotlin.math.roundToInt
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
    var activePlayer by remember { mutableStateOf<ExoPlayer?>(null) }
    var decoderMode by remember { mutableStateOf(settings.defaultDecoder) }
    var isPlaying by remember { mutableStateOf(true) }
    var currentPosMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(currentVideo.durationMs.coerceAtLeast(1L)) }
    var controlsVisible by remember { mutableStateOf(true) }
    var isLocked by remember { mutableStateOf(false) }
    var lockIconVisible by remember { mutableStateOf(false) }
    var backgroundPlayEnabled by remember { mutableStateOf(false) }
    var playbackSpeed by remember { mutableFloatStateOf(1.0f) }
    var is2xHolding by remember { mutableStateOf(false) }
    var playbackErrorMessage by remember { mutableStateOf<String?>(null) }
    var videoAspectRatio by remember { mutableFloatStateOf(16f / 9f) }

    var customSubUri by remember { mutableStateOf<Uri?>(null) }
    var customSubName by remember { mutableStateOf<String?>(null) }
    val subPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            customSubUri = uri
            customSubName = uri.lastPathSegment ?: "External Subtitle"
            val resumeAt = activePlayer?.currentPosition ?: currentPosMs
            engine.prepareMedia(currentVideo.uri, currentVideo.path, resumeAt, true, customSubUri, customSubName)
            Toast.makeText(context, "Subtitle Loaded!", Toast.LENGTH_SHORT).show()
        }
    }

    val aspectModes = listOf(
        AspectRatioFrameLayout.RESIZE_MODE_FIT to "Fit",
        AspectRatioFrameLayout.RESIZE_MODE_FILL to "Stretch",
        AspectRatioFrameLayout.RESIZE_MODE_ZOOM to "Crop",
        AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH to "100%"
    )
    var aspectIndex by remember { mutableIntStateOf(0) }

    var zoomScale by remember { mutableFloatStateOf(1f) }
    var panX by remember { mutableFloatStateOf(0f) }
    var panY by remember { mutableFloatStateOf(0f) }
    var dynamicSubSizeSp by remember { mutableFloatStateOf(settings.subFontSize.toFloat()) }
    var liveFps by remember { mutableIntStateOf(currentVideo.fps.coerceAtLeast(24)) }

    var accumulatedSeekSec by remember { mutableIntStateOf(0) }
    var lastTapTimestamp by remember { mutableLongStateOf(0L) }
    var hudText by remember { mutableStateOf<String?>(null) }

    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxSystemVol = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }
    var smoothVolume by remember {
        mutableFloatStateOf(((audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) * 100f) / maxSystemVol).coerceIn(0f, 100f))
    }
    var smoothBrightness by remember { mutableFloatStateOf(50f) }

    var showAudioDialog by remember { mutableStateOf(false) }
    var showSubDialog by remember { mutableStateOf(false) }
    var showSpeedDialog by remember { mutableStateOf(false) }
    var showEqDialog by remember { mutableStateOf(false) }
    var selectedEqPreset by remember { mutableIntStateOf(-1) }
    var audioTracks by remember { mutableStateOf<List<TrackInfoItem>>(emptyList()) }
    var subTracks by remember { mutableStateOf<List<TrackInfoItem>>(emptyList()) }
    DisposableEffect(Unit) {
        val mediaSession = MediaSession(context, "RinoxPlayerMediaGuard").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onMediaButtonEvent(mediaButtonIntent: Intent): Boolean {
                    val keyEvent: KeyEvent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        mediaButtonIntent.getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        mediaButtonIntent.getParcelableExtra(Intent.EXTRA_KEY_EVENT)
                    }
                    if (keyEvent != null && (
                                keyEvent.keyCode == KeyEvent.KEYCODE_HEADSETHOOK ||
                                keyEvent.keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE ||
                                keyEvent.keyCode == KeyEvent.KEYCODE_MEDIA_PLAY ||
                                keyEvent.keyCode == KeyEvent.KEYCODE_MEDIA_PAUSE
                            )
                    ) {
                        return true
                    }
                    return super.onMediaButtonEvent(mediaButtonIntent)
                }
            })
            val state = PlaybackState.Builder()
                .setActions(
                    PlaybackState.ACTION_PLAY or
                        PlaybackState.ACTION_PAUSE or
                        PlaybackState.ACTION_PLAY_PAUSE
                )
                .setState(PlaybackState.STATE_PLAYING, 0L, 1.0f)
                .build()
            setPlaybackState(state)
            isActive = true
        }
        onDispose {
            try {
                mediaSession.isActive = false
                mediaSession.release()
            } catch (_: Exception) {}
        }
    }

    LaunchedEffect(currentVideo.path) {
        zoomScale = 1f; panX = 0f; panY = 0f
        customSubUri = null; customSubName = null
        playbackErrorMessage = null
        liveFps = currentVideo.fps.coerceAtLeast(24)
        if (currentVideo.width > 0 && currentVideo.height > 0) {
            videoAspectRatio = currentVideo.width.toFloat() / currentVideo.height.toFloat()
        }
        activity?.requestedOrientation = if (currentVideo.height > currentVideo.width && currentVideo.width > 0) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        } else {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
    }

    LaunchedEffect(isLocked, lockIconVisible) {
        if (isLocked && lockIconVisible) {
            delay(2000L)
            lockIconVisible = false
        }
    }

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
            activePlayer = null
            engine.release()
        }
    }
    DisposableEffect(lifecycleOwner, backgroundPlayEnabled) {
        val observer = LifecycleEventObserver { _, event ->
            val inPip = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                activity?.isInPictureInPictureMode == true
            } else false
            if (event == Lifecycle.Event.ON_STOP && !backgroundPlayEnabled && !inPip) {
                activePlayer?.pause()
                isPlaying = false
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(currentVideo.path, decoderMode) {
        val savedPos = if (settings.resumePlayback) {
            context.getSharedPreferences("rinox_positions", Context.MODE_PRIVATE)
                .getLong(currentVideo.path, 0L)
        } else 0L

        val builtPlayer = engine.buildPlayer(
            decoderMode = decoderMode,
            onErrorFallback = { fallbackMode ->
                decoderMode = fallbackMode
                Toast.makeText(context, "Switched to $fallbackMode Decoder", Toast.LENGTH_SHORT).show()
            },
            onFatalError = { reason ->
                playbackErrorMessage = reason
            },
            onVideoEnded = {
                context.getSharedPreferences("rinox_positions", Context.MODE_PRIVATE)
                    .edit().remove(currentVideo.path).apply()
                if (currentIndex + 1 < playlist.size) currentIndex += 1 else onClose()
            },
            onTracksChanged = {
                audioTracks = engine.getAudioTracks()
                subTracks = engine.getSubtitleTracks()
                val vf = engine.exoPlayer?.videoFormat
                val detectedFps = vf?.frameRate?.roundToInt() ?: 0
                if (detectedFps > 0) liveFps = detectedFps
                if (vf != null && vf.width > 0 && vf.height > 0) {
                    videoAspectRatio = (vf.width * vf.pixelWidthHeightRatio) / vf.height.toFloat()
                }
            }
        )
        activePlayer = builtPlayer
        engine.prepareMedia(currentVideo.uri, currentVideo.path, savedPos, settings.autoLoadSubtitles, customSubUri, customSubName)
        engine.applySmoothVolume(smoothVolume.roundToInt())
        if (selectedEqPreset >= 0) engine.applyEqualizerPreset(selectedEqPreset)
        settingsManager.markVideoWatched(currentVideo.path)
        settingsManager.saveLastPlayedPath(currentVideo.path)
    }

    LaunchedEffect(currentVideo.path) {
        while (true) {
            delay(200L)
            activePlayer?.let { p ->
                isPlaying = p.isPlaying
                currentPosMs = p.currentPosition.coerceAtLeast(0L)
                if (p.duration > 0L) durationMs = p.duration
                val vf = p.videoFormat
                val fpsVal = vf?.frameRate?.roundToInt() ?: 0
                if (fpsVal > 0) liveFps = fpsVal
                val vs: VideoSize = p.videoSize
                if (vs.width > 0 && vs.height > 0) {
                    videoAspectRatio = (vs.width * vs.pixelWidthHeightRatio) / vs.height.toFloat()
                }
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

    LaunchedEffect(controlsVisible, isPlaying) {
        if (controlsVisible && isPlaying) {
            delay(3500L)
            controlsVisible = false
        }
    }

    BackHandler {
        if (isLocked) {
            lockIconVisible = true
        } else {
            activePlayer = null
            engine.release()
            onClose()
        }
    }
    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        val boundPlayer = activePlayer
        val boundAspect = videoAspectRatio
        val boundResizeMode = aspectModes[aspectIndex].first
        val boundSubSize = dynamicSubSizeSp

        AndroidView(
            factory = { ctx ->
                val aspectFrame = AspectRatioFrameLayout(ctx).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.Gravity.CENTER
                    )
                    setAspectRatio(boundAspect)
                    resizeMode = boundResizeMode
                }
                val textureView = TextureView(ctx).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
                val subtitleView = SubtitleView(ctx).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    visibility = View.VISIBLE
                    setApplyEmbeddedStyles(true)
                    setApplyEmbeddedFontSizes(false)
                    setStyle(
                        CaptionStyleCompat(
                            AndroidColor.WHITE,
                            AndroidColor.argb(110, 0, 0, 0),
                            AndroidColor.TRANSPARENT,
                            CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW,
                            AndroidColor.BLACK,
                            null
                        )
                    )
                    setFixedTextSize(TypedValue.COMPLEX_UNIT_SP, boundSubSize)
                }
                aspectFrame.addView(textureView)
                FrameLayout(ctx).apply {
                    addView(aspectFrame)
                    addView(subtitleView)
                    tag = Triple(aspectFrame, textureView, subtitleView)
                }
            },
            update = { root ->
                @Suppress("UNCHECKED_CAST")
                val triple = root.tag as? Triple<AspectRatioFrameLayout, TextureView, SubtitleView>
                if (triple != null) {
                    val (aspectFrame, textureView, subtitleView) = triple
                    aspectFrame.setAspectRatio(boundAspect)
                    aspectFrame.resizeMode = boundResizeMode
                    subtitleView.setFixedTextSize(TypedValue.COMPLEX_UNIT_SP, boundSubSize)
                    if (boundPlayer != null && root.getTag(Int.MAX_VALUE) !== boundPlayer) {
                        boundPlayer.setVideoTextureView(textureView)
                        boundPlayer.addListener(object : Player.Listener {
                            override fun onCues(cueGroup: CueGroup) {
                                subtitleView.setCues(cueGroup.cues)
                            }
                        })
                        subtitleView.setCues(boundPlayer.currentCues.cues)
                        root.setTag(Int.MAX_VALUE, boundPlayer)
                    }
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = zoomScale,
                    scaleY = zoomScale,
                    translationX = panX,
                    translationY = panY
                )
        )

        if (isLocked && !isInPipMode) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = { lockIconVisible = !lockIconVisible })
                    }
            )
        }
        if (!isLocked && !isInPipMode) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTransformGestures { centroid, pan, zoom, _ ->
                            if (zoom != 1f || (zoomScale > 1.02f && (pan.x != 0f || pan.y != 0f))) {
                                val h = size.height.toFloat().coerceAtLeast(1f)
                                if (centroid.y > h * 0.72f && zoomScale <= 1.05f && zoom != 1f) {
                                    dynamicSubSizeSp = (dynamicSubSizeSp * zoom).coerceIn(12f, 42f)
                                    hudText = "Subtitle Size: ${dynamicSubSizeSp.roundToInt()}sp"
                                } else {
                                    zoomScale = (zoomScale * zoom).coerceIn(1f, 4f)
                                    if (zoomScale > 1.02f) {
                                        panX += pan.x
                                        panY += pan.y
                                        hudText = "Zoom: ${(zoomScale * 100).roundToInt()}%"
                                    } else {
                                        panX = 0f; panY = 0f
                                        hudText = "Zoom: 100%"
                                    }
                                }
                            }
                        }
                    }
                    .pointerInput(currentVideo.path, settings.seekStepSeconds) {
                        detectTapGestures(
                            onTap = { offset ->
                                hudText = null
                                val now = System.currentTimeMillis()
                                val widthPx = size.width.toFloat()
                                if (now - lastTapTimestamp < 650L && (offset.x < widthPx * 0.35f || offset.x > widthPx * 0.65f)) {
                                    val dir = if (offset.x > widthPx * 0.65f) 1 else -1
                                    val nextAcc = (accumulatedSeekSec + dir * settings.seekStepSeconds)
                                        .coerceIn(-100 * settings.seekStepSeconds, 100 * settings.seekStepSeconds)
                                    accumulatedSeekSec = nextAcc
                                    lastTapTimestamp = now
                                    val targetPos = ((activePlayer?.currentPosition ?: 0L) + dir * settings.seekStepSeconds * 1000L)
                                        .coerceIn(0L, durationMs)
                                    activePlayer?.seekTo(targetPos)
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
                                    val targetPos = ((activePlayer?.currentPosition ?: 0L) + settings.seekStepSeconds * 1000L)
                                        .coerceIn(0L, durationMs)
                                    activePlayer?.seekTo(targetPos)
                                    currentPosMs = targetPos
                                    hudText = "+${accumulatedSeekSec}s"
                                } else if (offset.x < widthPx * 0.35f) {
                                    accumulatedSeekSec = (accumulatedSeekSec - settings.seekStepSeconds)
                                        .coerceAtLeast(-100 * settings.seekStepSeconds)
                                    val targetPos = ((activePlayer?.currentPosition ?: 0L) - settings.seekStepSeconds * 1000L)
                                        .coerceIn(0L, durationMs)
                                    activePlayer?.seekTo(targetPos)
                                    currentPosMs = targetPos
                                    hudText = "${accumulatedSeekSec}s"
                                } else {
                                    activePlayer?.let { p -> if (p.isPlaying) p.pause() else p.play() }
                                }
                            },
                            onLongPress = {
                                is2xHolding = true
                                activePlayer?.setPlaybackSpeed(2.0f)
                                hudText = "2.0X Speed"
                            },
                            onPress = {
                                tryAwaitRelease()
                                if (is2xHolding) {
                                    is2xHolding = false
                                    activePlayer?.setPlaybackSpeed(playbackSpeed)
                                    hudText = null
                                }
                            }
                        )
                    }
                    .pointerInput(settings.enableEdgeSwipes) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val startX = down.position.x
                            val w = size.width.toFloat().coerceAtLeast(1f)
                            val h = size.height.toFloat().coerceAtLeast(1f)
                            val dragMode = when {
                                settings.enableEdgeSwipes && startX < w * 0.25f -> 1
                                settings.enableEdgeSwipes && startX > w * 0.75f -> 2
                                else -> 3
                            }
                            var totalDx = 0f
                            var totalDy = 0f
                            var dragging = false

                            while (true) {
                                val event = awaitPointerEvent()
                                if (event.changes.count { it.pressed } > 1) {
                                    if (dragging) hudText = null
                                    break
                                }
                                val change = event.changes.firstOrNull() ?: break
                                if (!change.pressed) {
                                    if (dragging) hudText = null
                                    break
                                }
                                val dx = change.position.x - change.previousPosition.x
                                val dy = change.position.y - change.previousPosition.y
                                totalDx += dx
                                totalDy += dy

                                if (!dragging && (abs(totalDx) > 18f || abs(totalDy) > 18f)) {
                                    dragging = true
                                }
                                if (dragging) {
                                    change.consume()
                                    when (dragMode) {
                                        1 -> {
                                            smoothBrightness = (smoothBrightness + (-dy / h) * 100f).coerceIn(1f, 100f)
                                            activity?.window?.attributes = activity?.window?.attributes?.apply {
                                                screenBrightness = smoothBrightness / 100f
                                            }
                                            hudText = "Brightness: ${smoothBrightness.roundToInt()}%"
                                        }
                                        2 -> {
                                            smoothVolume = (smoothVolume + (-dy / h) * 160f).coerceIn(0f, 200f)
                                            val volInt = smoothVolume.roundToInt()
                                            val sysVol = ((volInt.coerceAtMost(100) * maxSystemVol) / 100)
                                            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, sysVol, 0)
                                            engine.applySmoothVolume(volInt)
                                            hudText = "Volume: $volInt%"
                                        }
                                        3 -> {
                                            if (abs(dx) > abs(dy)) {
                                                val seekDeltaMs = ((dx / w) * 45000L).toLong()
                                                val newPos = ((activePlayer?.currentPosition ?: 0L) + seekDeltaMs)
                                                    .coerceIn(0L, durationMs)
                                                activePlayer?.seekTo(newPos)
                                                currentPosMs = newPos
                                                hudText = FormatUtils.formatDuration(newPos)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
            )
        }
        if (hudText != null && !isInPipMode) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xB3000000))
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            ) {
                Text(text = hudText!!, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }

        AnimatedVisibility(
            visible = isLocked && lockIconVisible && !isInPipMode,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopStart).padding(20.dp)
        ) {
            IconButton(
                onClick = { isLocked = false; controlsVisible = true },
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.14f))
                    .border(1.dp, Color.White.copy(alpha = 0.35f), CircleShape)
            ) {
                Icon(Icons.Rounded.Lock, contentDescription = "Unlock", tint = Color.White, modifier = Modifier.size(20.dp))
            }
        }

        AnimatedVisibility(
            visible = controlsVisible && !isLocked && !isInPipMode,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(modifier = Modifier.fillMaxSize().background(Color(0x44000000))) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .padding(horizontal = 10.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { activePlayer = null; engine.release(); onClose() }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                    Column(modifier = Modifier.weight(1f).padding(horizontal = 4.dp)) {
                        Text(
                            text = currentVideo.displayName,
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "$liveFps FPS • $decoderMode",
                            color = RinoxCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    TextButton(onClick = {
                        decoderMode = when (decoderMode) {
                            "HW" -> "HW+"
                            "HW+" -> "SW"
                            else -> "HW"
                        }
                    }) {
                        Text(decoderMode, color = RinoxCyan, fontWeight = FontWeight.Bold)
                    }
                    IconButton(onClick = { showEqDialog = true }) {
                        Icon(Icons.Rounded.GraphicEq, contentDescription = "Equalizer", tint = if (selectedEqPreset >= 0) RinoxCyan else Color.White)
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

                Column(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 12.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color.White.copy(alpha = 0.14f))
                        .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(24.dp))
                        .padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    IconButton(onClick = { isLocked = true; lockIconVisible = true; controlsVisible = false }) {
                        Icon(Icons.Rounded.LockOpen, contentDescription = "Lock", tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                    TextButton(onClick = { showSpeedDialog = true }) {
                        Text("${playbackSpeed}x", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    IconButton(onClick = {
                        backgroundPlayEnabled = !backgroundPlayEnabled
                        Toast.makeText(context, if (backgroundPlayEnabled) "Background Audio ON" else "Background Audio OFF", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(Icons.Rounded.Headphones, contentDescription = "Bg Play", tint = if (backgroundPlayEnabled) RinoxCyan else Color.White, modifier = Modifier.size(20.dp))
                    }
                }

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
                                activePlayer?.seekTo(currentPosMs)
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
                            onClick = { activePlayer?.let { if (it.isPlaying) it.pause() else it.play() } },
                            modifier = Modifier.size(54.dp).clip(CircleShape).background(accentColor)
                        ) {
                            Icon(if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, contentDescription = "PlayPause", tint = Color.White, modifier = Modifier.size(34.dp))
                        }
                        IconButton(onClick = { if (currentIndex + 1 < playlist.size) currentIndex += 1 }) {
                            Icon(Icons.Rounded.SkipNext, contentDescription = "Next", tint = Color.White, modifier = Modifier.size(32.dp))
                        }
                        TextButton(onClick = {
                            zoomScale = 1f; panX = 0f; panY = 0f
                            aspectIndex = (aspectIndex + 1) % aspectModes.size
                        }) {
                            Text(aspectModes[aspectIndex].second, color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
    if (playbackErrorMessage != null) {
        AlertDialog(
            onDismissRequest = {},
            containerColor = DarkCard,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = Color(0xFFEF5350))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Playback Error", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Text(
                    text = "Can't play this video because:\n${playbackErrorMessage}",
                    color = Color.LightGray,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    playbackErrorMessage = null
                    decoderMode = "SW"
                }) {
                    Text("Retry (SW)", color = RinoxCyan)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    playbackErrorMessage = null
                    activePlayer = null
                    engine.release()
                    onClose()
                }) {
                    Text("Close", color = Color(0xFFEF5350))
                }
            }
        )
    }

    if (showEqDialog) {
        val presets = remember { engine.getEqualizerPresets() }
        AlertDialog(
            onDismissRequest = { showEqDialog = false },
            containerColor = DarkCard,
            title = { Text("MX-Style Audio Equalizer", color = Color.White) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Select Studio Audio Preset:", color = Color.LightGray, fontSize = 13.sp)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            FilterChip(
                                selected = selectedEqPreset == -1,
                                onClick = { selectedEqPreset = -1; engine.applyEqualizerPreset(-1) },
                                label = { Text("Off") }
                            )
                        }
                        items(presets.size) { idx ->
                            FilterChip(
                                selected = selectedEqPreset == idx,
                                onClick = { selectedEqPreset = idx; engine.applyEqualizerPreset(idx) },
                                label = { Text(presets[idx]) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showEqDialog = false }) { Text("Done", color = RinoxCyan) }
            }
        )
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
                            activePlayer?.setPlaybackSpeed(spd)
                            showSpeedDialog = false
                        }) { Text("${spd}x", color = if (playbackSpeed == spd) RinoxCyan else Color.White) }
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
                        }) { Text(tr.label, color = if (tr.isSelected) RinoxCyan else Color.White) }
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
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Button(
                        onClick = {
                            showSubDialog = false
                            subPickerLauncher.launch(arrayOf("*/*"))
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Open Subtitle (.srt / .ass / .vtt)")
                    }
                    TextButton(onClick = {
                        engine.disableTrackType(C.TRACK_TYPE_TEXT)
                        showSubDialog = false
                    }) { Text("Disable Subtitles", color = Color.LightGray) }
                    subTracks.forEach { tr ->
                        TextButton(onClick = {
                            engine.selectTrack(C.TRACK_TYPE_TEXT, tr.groupIndex, tr.trackIndex)
                            showSubDialog = false
                        }) { Text(tr.label, color = if (tr.isSelected) RinoxCyan else Color.White) }
                    }
                }
            },
            confirmButton = {}
        )
    }
}
