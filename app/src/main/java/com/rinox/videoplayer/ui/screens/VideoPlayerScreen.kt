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
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.TextStyle
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
import com.rinox.videoplayer.ui.theme.RinoxBlue
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
    var quickRowExpanded by remember { mutableStateOf(true) }
    var rightPanelVisible by remember { mutableStateOf(false) }

    var isLocked by remember { mutableStateOf(false) }
    var lockIconVisible by remember { mutableStateOf(false) }
    var backgroundPlayEnabled by remember { mutableStateOf(false) }
    var playbackSpeed by remember { mutableFloatStateOf(1.0f) }
    var is2xHolding by remember { mutableStateOf(false) }
    var isMuted by remember { mutableStateOf(false) }
    var loopMode by remember { mutableIntStateOf(0) } // 0 = Off, 1 = Loop One, 2 = Loop All
    var sleepTimerMinutes by remember { mutableIntStateOf(0) }
    var sleepRemainingSec by remember { mutableIntStateOf(0) }

    // A-B Repeat States
    var abPointAMs by remember { mutableLongStateOf(-1L) }
    var abPointBMs by remember { mutableLongStateOf(-1L) }

    // Smart Resume Banner States
    var resumeBannerPosMs by remember { mutableLongStateOf(0L) }
    var showResumeBanner by remember { mutableStateOf(false) }

    // Display Style States
    var seekBarFlat by remember { mutableStateOf(false) }
    var showRemainingTime by remember { mutableStateOf(false) }

    var playbackErrorMessage by remember { mutableStateOf<String?>(null) }
    var videoAspectRatio by remember { mutableFloatStateOf(16f / 9f) }

    // World-Class Subtitle Customization States
    var customSubUri by remember { mutableStateOf<Uri?>(null) }
    var customSubName by remember { mutableStateOf<String?>(null) }
    var dynamicSubSizeSp by remember { mutableFloatStateOf(settings.subFontSize.toFloat().coerceIn(10f, 54f)) }
    var subTextColorIndex by remember { mutableIntStateOf(0) } // 0=White, 1=Yellow, 2=Cyan, 3=Green, 4=Pink
    var subBgOpacity by remember { mutableFloatStateOf(0.45f) }
    var subEdgeStyle by remember { mutableIntStateOf(1) } // 0=None, 1=Drop Shadow, 2=Outline Border
    var subBottomPaddingFraction by remember { mutableFloatStateOf(0.08f) } // Vertical position
    var subEmbeddedStylesEnabled by remember { mutableStateOf(false) } // For ASS/SSA styling
    var subSyncOffsetMs by remember { mutableLongStateOf(0L) }
    val subPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Exception) {}
            customSubUri = uri
            customSubName = uri.lastPathSegment ?: "External Subtitle"
            val resumeAt = activePlayer?.currentPosition ?: currentPosMs
            engine.prepareMedia(currentVideo.uri, currentVideo.path, resumeAt, true, customSubUri, customSubName)
            Toast.makeText(context, "Subtitle Loaded: $customSubName", Toast.LENGTH_SHORT).show()
        }
    }

    val aspectModes = listOf(
        AspectRatioFrameLayout.RESIZE_MODE_FIT to "Fit",
        AspectRatioFrameLayout.RESIZE_MODE_FILL to "Stretch",
        AspectRatioFrameLayout.RESIZE_MODE_ZOOM to "Crop",
        AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH to "100%"
    )
    var aspectIndex by remember { mutableIntStateOf(0) }

    // Zoom: 25% (0.25f) to 400% (4.0f)
    var zoomScale by remember { mutableFloatStateOf(1f) }
    var panX by remember { mutableFloatStateOf(0f) }
    var panY by remember { mutableFloatStateOf(0f) }
    var liveFps by remember { mutableIntStateOf(currentVideo.fps.coerceAtLeast(24)) }

    var accumulatedSeekSec by remember { mutableIntStateOf(0) }
    var lastTapTimestamp by remember { mutableLongStateOf(0L) }
    var hudText by remember { mutableStateOf<String?>(null) }
    var isTapSeekHud by remember { mutableStateOf(false) }

    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxSystemVol = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }
    var smoothVolume by remember {
        mutableFloatStateOf(((audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) * 100f) / maxSystemVol).coerceIn(0f, 100f))
    }
    var smoothBrightness by remember { mutableFloatStateOf(50f) }

    var showAudioDialog by remember { mutableStateOf(false) }
    var showSubDialog by remember { mutableStateOf(false) }
    var showSubStyleDialog by remember { mutableStateOf(false) }
    var showSpeedDialog by remember { mutableStateOf(false) }
    var showSleepDialog by remember { mutableStateOf(false) }
    var showEqDialog by remember { mutableStateOf(false) }
    var selectedEqPreset by remember { mutableIntStateOf(-1) }
    var audioTracks by remember { mutableStateOf<List<TrackInfoItem>>(emptyList()) }
    var subTracks by remember { mutableStateOf<List<TrackInfoItem>>(emptyList()) }
    // Toggle System Status Bar visibility alongside Player Controls
    LaunchedEffect(controlsVisible, isLocked, isInPipMode) {
        val window = activity?.window ?: return@LaunchedEffect
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        if (controlsVisible && !isLocked && !isInPipMode) {
            controller.show(WindowInsetsCompat.Type.statusBars())
            controller.hide(WindowInsetsCompat.Type.navigationBars())
            controller.isAppearanceLightStatusBars = false
        } else {
            controller.hide(WindowInsetsCompat.Type.systemBars())
        }
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    // Sleep Timer Countdown
    LaunchedEffect(sleepTimerMinutes) {
        if (sleepTimerMinutes > 0) {
            sleepRemainingSec = sleepTimerMinutes * 60
            while (sleepRemainingSec > 0) {
                delay(1000L)
                sleepRemainingSec -= 1
            }
            activePlayer?.pause()
            isPlaying = false
            sleepTimerMinutes = 0
            Toast.makeText(context, "Sleep Timer: Playback Paused", Toast.LENGTH_SHORT).show()
        } else {
            sleepRemainingSec = 0
        }
    }

    // Smart Resume Banner Auto-Hide after 5 seconds
    LaunchedEffect(showResumeBanner) {
        if (showResumeBanner) {
            delay(5000L)
            showResumeBanner = false
        }
    }

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
        abPointAMs = -1L; abPointBMs = -1L
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

        if (savedPos > 5000L) {
            resumeBannerPosMs = savedPos
            showResumeBanner = true
        } else {
            showResumeBanner = false
        }

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
                when {
                    loopMode == 1 -> {
                        activePlayer?.seekTo(0L)
                        activePlayer?.play()
                    }
                    currentIndex + 1 < playlist.size -> currentIndex += 1
                    loopMode == 2 && playlist.isNotEmpty() -> currentIndex = 0
                    else -> onClose()
                }
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
        engine.applySmoothVolume(if (isMuted) 0 else smoothVolume.roundToInt())
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

                if (abPointAMs >= 0L && abPointBMs > abPointAMs && currentPosMs >= abPointBMs) {
                    p.seekTo(abPointAMs)
                }

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
            if (hudText != null && System.currentTimeMillis() - lastTapTimestamp > 700L && isTapSeekHud) {
                accumulatedSeekSec = 0
                hudText = null
                isTapSeekHud = false
            }
        }
    }

    LaunchedEffect(controlsVisible, isPlaying, rightPanelVisible) {
        if (controlsVisible && isPlaying && !rightPanelVisible) {
            delay(3800L)
            controlsVisible = false
        }
    }

    BackHandler {
        when {
            rightPanelVisible -> rightPanelVisible = false
            isLocked -> lockIconVisible = true
            else -> {
                activePlayer = null
                engine.release()
                onClose()
            }
        }
    }
    val subColorInt = when (subTextColorIndex) {
        1 -> AndroidColor.YELLOW
        2 -> AndroidColor.CYAN
        3 -> AndroidColor.GREEN
        4 -> AndroidColor.MAGENTA
        else -> AndroidColor.WHITE
    }
    val subBgColorInt = AndroidColor.argb((subBgOpacity * 255).roundToInt().coerceIn(0, 255), 0, 0, 0)
    val subEdgeType = when (subEdgeStyle) {
        0 -> CaptionStyleCompat.EDGE_TYPE_NONE
        2 -> CaptionStyleCompat.EDGE_TYPE_OUTLINE
        else -> CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        val boundPlayer = activePlayer
        val boundAspect = videoAspectRatio
        val boundResizeMode = aspectModes[aspectIndex].first
        val boundSubSize = dynamicSubSizeSp
        val boundSubPadding = subBottomPaddingFraction
        val boundEmbedded = subEmbeddedStylesEnabled

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
                    setApplyEmbeddedStyles(boundEmbedded)
                    setApplyEmbeddedFontSizes(boundEmbedded)
                    setBottomPaddingFraction(boundSubPadding)
                    setStyle(
                        CaptionStyleCompat(
                            subColorInt,
                            subBgColorInt,
                            AndroidColor.TRANSPARENT,
                            subEdgeType,
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
                    subtitleView.setApplyEmbeddedStyles(boundEmbedded)
                    subtitleView.setApplyEmbeddedFontSizes(boundEmbedded)
                    subtitleView.setBottomPaddingFraction(boundSubPadding)
                    subtitleView.setStyle(
                        CaptionStyleCompat(
                            subColorInt,
                            subBgColorInt,
                            AndroidColor.TRANSPARENT,
                            subEdgeType,
                            AndroidColor.BLACK,
                            null
                        )
                    )
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
                            if (zoom != 1f || (abs(zoomScale - 1f) > 0.02f && (pan.x != 0f || pan.y != 0f))) {
                                val h = size.height.toFloat().coerceAtLeast(1f)
                                if (centroid.y > h * 0.70f && abs(zoomScale - 1f) <= 0.05f && zoom != 1f) {
                                    dynamicSubSizeSp = (dynamicSubSizeSp * zoom).coerceIn(10f, 54f)
                                    isTapSeekHud = false
                                    hudText = "Subtitle Size: ${dynamicSubSizeSp.roundToInt()}sp"
                                } else {
                                    zoomScale = (zoomScale * zoom).coerceIn(0.25f, 4.0f)
                                    isTapSeekHud = false
                                    if (abs(zoomScale - 1f) > 0.03f) {
                                        panX += pan.x
                                        panY += pan.y
                                        hudText = "${(zoomScale * 100).roundToInt()}%"
                                    } else {
                                        zoomScale = 1f
                                        panX = 0f; panY = 0f
                                        hudText = "100%"
                                    }
                                }
                            }
                        }
                    }
                    .pointerInput(currentVideo.path, settings.seekStepSeconds) {
                        detectTapGestures(
                            onTap = { offset ->
                                if (rightPanelVisible) {
                                    rightPanelVisible = false
                                    return@detectTapGestures
                                }
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
                                    isTapSeekHud = true
                                    hudText = if (accumulatedSeekSec > 0) "+${accumulatedSeekSec}s" else "${accumulatedSeekSec}s"
                                } else {
                                    hudText = null
                                    isTapSeekHud = false
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
                                    isTapSeekHud = true
                                    hudText = "+${accumulatedSeekSec}s"
                                } else if (offset.x < widthPx * 0.35f) {
                                    accumulatedSeekSec = (accumulatedSeekSec - settings.seekStepSeconds)
                                        .coerceAtLeast(-100 * settings.seekStepSeconds)
                                    val targetPos = ((activePlayer?.currentPosition ?: 0L) - settings.seekStepSeconds * 1000L)
                                        .coerceIn(0L, durationMs)
                                    activePlayer?.seekTo(targetPos)
                                    currentPosMs = targetPos
                                    isTapSeekHud = true
                                    hudText = "${accumulatedSeekSec}s"
                                } else {
                                    activePlayer?.let { p -> if (p.isPlaying) p.pause() else p.play() }
                                }
                            },
                            onLongPress = {
                                is2xHolding = true
                                isTapSeekHud = false
                                activePlayer?.setPlaybackSpeed(2.0f)
                                hudText = "2.0x Speed"
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
                                    isTapSeekHud = false
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
                                            engine.applySmoothVolume(if (isMuted) 0 else volInt)
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
                    .then(
                        if (isTapSeekHud) Modifier else Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xA6000000))
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    )
            ) {
                Text(
                    text = hudText!!,
                    style = TextStyle(
                        color = Color.White,
                        fontSize = if (isTapSeekHud) 24.sp else 16.sp,
                        fontWeight = FontWeight.Bold,
                        shadow = Shadow(color = Color.Black, blurRadius = 8f)
                    )
                )
            }
        }

        AnimatedVisibility(
            visible = isLocked && lockIconVisible && !isInPipMode,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(14.dp)
        ) {
            IconButton(
                onClick = { isLocked = false; controlsVisible = true },
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f))
                    .border(1.dp, Color.White.copy(alpha = 0.35f), CircleShape)
            ) {
                Icon(Icons.Rounded.Lock, contentDescription = "Unlock", tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }
        AnimatedVisibility(
            visible = controlsVisible && !isLocked && !isInPipMode,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(modifier = Modifier.fillMaxSize().background(Color(0x3D000000))) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { activePlayer = null; engine.release(); onClose() },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = Color.White, modifier = Modifier.size(20.dp))
                        }

                        Column(modifier = Modifier.weight(1f).padding(horizontal = 4.dp)) {
                            Text(
                                text = currentVideo.displayName,
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "$liveFps FPS • $decoderMode",
                                color = RinoxCyan,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color.White.copy(alpha = 0.14f))
                                .clickable {
                                    decoderMode = when (decoderMode) {
                                        "HW" -> "HW+"
                                        "HW+" -> "SW"
                                        else -> "HW"
                                    }
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(decoderMode, color = RinoxCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        IconButton(onClick = { showAudioDialog = true }, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Rounded.Audiotrack, contentDescription = "Audio", tint = Color.White, modifier = Modifier.size(19.dp))
                        }
                        IconButton(onClick = { showSubDialog = true }, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Rounded.Subtitles, contentDescription = "Subtitles", tint = Color.White, modifier = Modifier.size(19.dp))
                        }
                        IconButton(onClick = { rightPanelVisible = !rightPanelVisible }, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Rounded.MoreVert, contentDescription = "More Options", tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 6.dp, top = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.45f))
                                .clickable { quickRowExpanded = !quickRowExpanded },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                if (quickRowExpanded) Icons.Rounded.ChevronLeft else Icons.Rounded.ChevronRight,
                                contentDescription = "Toggle Shortcuts",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        if (quickRowExpanded) {
                            Spacer(modifier = Modifier.width(6.dp))
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                item {
                                    QuickShortcutChip(
                                        icon = Icons.Rounded.TextFields,
                                        label = "Sub ${dynamicSubSizeSp.roundToInt()}sp",
                                        active = true,
                                        onClick = { showSubStyleDialog = true }
                                    )
                                }
                                item {
                                    QuickShortcutChip(
                                        icon = Icons.Rounded.Speed,
                                        label = "${playbackSpeed}x",
                                        active = playbackSpeed != 1.0f,
                                        onClick = { showSpeedDialog = true }
                                    )
                                }
                                item {
                                    QuickShortcutChip(
                                        icon = Icons.Rounded.Bedtime,
                                        label = if (sleepRemainingSec > 0) "${(sleepRemainingSec + 59) / 60}m" else "Sleep",
                                        active = sleepRemainingSec > 0,
                                        onClick = { showSleepDialog = true }
                                    )
                                }
                                item {
                                    QuickShortcutChip(
                                        icon = Icons.Rounded.Headphones,
                                        label = "Bg Play",
                                        active = backgroundPlayEnabled,
                                        onClick = {
                                            backgroundPlayEnabled = !backgroundPlayEnabled
                                            Toast.makeText(context, if (backgroundPlayEnabled) "Background Audio ON" else "Background Audio OFF", Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                }
                                item {
                                    QuickShortcutChip(
                                        icon = Icons.Rounded.Repeat,
                                        label = when (loopMode) { 1 -> "Loop 1"; 2 -> "Loop All"; else -> "Loop" },
                                        active = loopMode > 0,
                                        onClick = {
                                            loopMode = (loopMode + 1) % 3
                                            val msg = when (loopMode) { 1 -> "Repeat One"; 2 -> "Repeat All"; else -> "Repeat Off" }
                                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                }
                                item {
                                    QuickShortcutChip(
                                        icon = Icons.Rounded.GraphicEq,
                                        label = "EQ",
                                        active = selectedEqPreset >= 0,
                                        onClick = { showEqDialog = true }
                                    )
                                }
                                item {
                                    QuickShortcutChip(
                                        icon = if (isMuted) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp,
                                        label = if (isMuted) "Muted" else "Mute",
                                        active = isMuted,
                                        onClick = {
                                            isMuted = !isMuted
                                            engine.applySmoothVolume(if (isMuted) 0 else smoothVolume.roundToInt())
                                        }
                                    )
                                }
                                item {
                                    QuickShortcutChip(
                                        icon = Icons.Rounded.SyncAlt,
                                        label = when {
                                            abPointAMs < 0L -> "A-B"
                                            abPointBMs < 0L -> "Set B"
                                            else -> "A-B ON"
                                        },
                                        active = abPointAMs >= 0L,
                                        onClick = {
                                            when {
                                                abPointAMs < 0L -> {
                                                    abPointAMs = currentPosMs
                                                    Toast.makeText(context, "Point A set at ${FormatUtils.formatDuration(abPointAMs)}", Toast.LENGTH_SHORT).show()
                                                }
                                                abPointBMs < 0L -> {
                                                    if (currentPosMs > abPointAMs + 1000L) {
                                                        abPointBMs = currentPosMs
                                                        Toast.makeText(context, "A-B Repeat Active", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                                else -> {
                                                    abPointAMs = -1L; abPointBMs = -1L
                                                    Toast.makeText(context, "A-B Repeat Cleared", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    if (showResumeBanner && resumeBannerPosMs > 5000L) {
                        Row(
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .padding(bottom = 8.dp)
                                .clip(CircleShape)
                                .background(Color(0xCC141923))
                                .border(1.dp, RinoxCyan.copy(alpha = 0.45f), CircleShape)
                                .padding(horizontal = 14.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Resumed ${FormatUtils.formatDuration(resumeBannerPosMs)}",
                                color = Color.White,
                                fontSize = 11.sp
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Start Over",
                                color = RinoxCyan,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.clickable {
                                    activePlayer?.seekTo(0L)
                                    currentPosMs = 0L
                                    showResumeBanner = false
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                Icons.Rounded.Close,
                                contentDescription = "Dismiss",
                                tint = Color.Gray,
                                modifier = Modifier
                                    .size(14.dp)
                                    .clickable { showResumeBanner = false }
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(FormatUtils.formatDuration(currentPosMs), color = Color.White, fontSize = 11.sp)
                        Slider(
                            value = currentPosMs.toFloat(),
                            onValueChange = { pos ->
                                currentPosMs = pos.toLong()
                                activePlayer?.seekTo(currentPosMs)
                            },
                            valueRange = 0f..durationMs.toFloat().coerceAtLeast(1f),
                            colors = SliderDefaults.colors(
                                thumbColor = if (seekBarFlat) Color.Transparent else RinoxBlue,
                                activeTrackColor = RinoxBlue,
                                inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                            ),
                            modifier = Modifier.weight(1f).height(28.dp).padding(horizontal = 8.dp)
                        )
                        val rightTimeText = if (showRemainingTime) {
                            "-${FormatUtils.formatDuration((durationMs - currentPosMs).coerceAtLeast(0L))}"
                        } else {
                            FormatUtils.formatDuration(durationMs)
                        }
                        Text(
                            text = rightTimeText,
                            color = Color.White,
                            fontSize = 11.sp,
                            modifier = Modifier.clickable { showRemainingTime = !showRemainingTime }
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { isLocked = true; lockIconVisible = true; controlsVisible = false },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Rounded.LockOpen, contentDescription = "Lock", tint = Color.White, modifier = Modifier.size(19.dp))
                        }
                        IconButton(
                            onClick = { if (currentIndex > 0) currentIndex -= 1 },
                            modifier = Modifier.size(38.dp)
                        ) {
                            Icon(Icons.Rounded.SkipPrevious, contentDescription = "Prev", tint = Color.White, modifier = Modifier.size(26.dp))
                        }
                        IconButton(
                            onClick = { activePlayer?.let { if (it.isPlaying) it.pause() else it.play() } },
                            modifier = Modifier.size(44.dp).clip(CircleShape).background(RinoxBlue)
                        ) {
                            Icon(if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, contentDescription = "PlayPause", tint = Color.White, modifier = Modifier.size(26.dp))
                        }
                        IconButton(
                            onClick = { if (currentIndex + 1 < playlist.size) currentIndex += 1 },
                            modifier = Modifier.size(38.dp)
                        ) {
                            Icon(Icons.Rounded.SkipNext, contentDescription = "Next", tint = Color.White, modifier = Modifier.size(26.dp))
                        }
                        IconButton(
                            onClick = {
                                activity?.requestedOrientation =
                                    if (activity?.requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE)
                                        ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                                    else ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Rounded.ScreenRotation, contentDescription = "Rotate", tint = Color.White, modifier = Modifier.size(19.dp))
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color.White.copy(alpha = 0.14f))
                                .clickable {
                                    zoomScale = 1f; panX = 0f; panY = 0f
                                    aspectIndex = (aspectIndex + 1) % aspectModes.size
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(aspectModes[aspectIndex].second, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
        AnimatedVisibility(
            visible = rightPanelVisible && !isInPipMode,
            enter = slideInHorizontally { it } + fadeIn(),
            exit = slideOutHorizontally { it } + fadeOut(),
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            Column(
                modifier = Modifier
                    .width(235.dp)
                    .fillMaxHeight()
                    .background(Color(0xE6111620))
                    .statusBarsPadding()
                    .padding(14.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Player Menu", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    IconButton(onClick = { rightPanelVisible = false }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close", tint = Color.Gray, modifier = Modifier.size(18.dp))
                    }
                }

                HorizontalDivider(color = Color.White.copy(alpha = 0.12f))

                Text("Display", color = RinoxCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                PanelActionRow(Icons.Rounded.AspectRatio, "Aspect: ${aspectModes[aspectIndex].second}") {
                    zoomScale = 1f; panX = 0f; panY = 0f
                    aspectIndex = (aspectIndex + 1) % aspectModes.size
                }
                PanelActionRow(Icons.Rounded.LinearScale, if (seekBarFlat) "Seek Bar: Flat" else "Seek Bar: Round") {
                    seekBarFlat = !seekBarFlat
                }
                PanelActionRow(Icons.Rounded.Schedule, if (showRemainingTime) "Time: Remaining" else "Time: Total") {
                    showRemainingTime = !showRemainingTime
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    PanelActionRow(Icons.Rounded.PictureInPictureAlt, "Pop-up / PiP Mode") {
                        rightPanelVisible = false
                        try {
                            val params = PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build()
                            activity?.enterPictureInPictureMode(params)
                        } catch (_: Exception) {}
                    }
                }

                HorizontalDivider(color = Color.White.copy(alpha = 0.12f))

                Text("Audio & Subtitles", color = RinoxCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                PanelActionRow(Icons.Rounded.Audiotrack, "Audio Track") {
                    rightPanelVisible = false; showAudioDialog = true
                }
                PanelActionRow(Icons.Rounded.GraphicEq, "Equalizer") {
                    rightPanelVisible = false; showEqDialog = true
                }
                PanelActionRow(Icons.Rounded.Subtitles, "Subtitle Track & Open") {
                    rightPanelVisible = false; showSubDialog = true
                }
                PanelActionRow(Icons.Rounded.Tune, "Subtitle Size, Style & Sync") {
                    rightPanelVisible = false; showSubStyleDialog = true
                }

                HorizontalDivider(color = Color.White.copy(alpha = 0.12f))

                Text("Tools", color = RinoxCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                PanelActionRow(Icons.Rounded.Bedtime, "Sleep Timer") {
                    rightPanelVisible = false; showSleepDialog = true
                }
                PanelActionRow(Icons.Rounded.Speed, "Playback Speed (${playbackSpeed}x)") {
                    rightPanelVisible = false; showSpeedDialog = true
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
                Text("Can't play this video because:\n$playbackErrorMessage", color = Color.LightGray, fontSize = 14.sp)
            },
            confirmButton = {
                TextButton(onClick = { playbackErrorMessage = null; decoderMode = "SW" }) {
                    Text("Retry (SW)", color = RinoxCyan)
                }
            },
            dismissButton = {
                TextButton(onClick = { playbackErrorMessage = null; activePlayer = null; engine.release(); onClose() }) {
                    Text("Close", color = Color(0xFFEF5350))
                }
            }
        )
    }

    if (showSubStyleDialog) {
        AlertDialog(
            onDismissRequest = { showSubStyleDialog = false },
            containerColor = DarkCard,
            title = { Text("Subtitle Size, Style & Sync", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Subtitle Size: ${dynamicSubSizeSp.roundToInt()}sp", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        OutlinedButton(onClick = { dynamicSubSizeSp = (dynamicSubSizeSp - 2f).coerceAtLeast(10f) }) { Text("-2") }
                        Spacer(modifier = Modifier.width(6.dp))
                        OutlinedButton(onClick = { dynamicSubSizeSp = (dynamicSubSizeSp + 2f).coerceAtMost(54f) }) { Text("+2") }
                    }
                    Slider(value = dynamicSubSizeSp, onValueChange = { dynamicSubSizeSp = it }, valueRange = 10f..54f)

                    Text("Vertical Position: ${(subBottomPaddingFraction * 100).roundToInt()}%", color = Color.LightGray, fontSize = 12.sp)
                    Slider(value = subBottomPaddingFraction, onValueChange = { subBottomPaddingFraction = it }, valueRange = 0.02f..0.45f)

                    Text("Text Color", color = Color.LightGray, fontSize = 12.sp)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        val colorNames = listOf("White", "Yellow", "Cyan", "Green", "Pink")
                        items(colorNames.size) { idx ->
                            FilterChip(
                                selected = subTextColorIndex == idx,
                                onClick = { subTextColorIndex = idx },
                                label = { Text(colorNames[idx], fontSize = 11.sp) }
                            )
                        }
                    }

                    Text("Background Box Opacity: ${(subBgOpacity * 100).roundToInt()}%", color = Color.LightGray, fontSize = 12.sp)
                    Slider(value = subBgOpacity, onValueChange = { subBgOpacity = it }, valueRange = 0f..1f)

                    Text("Text Border / Shadow", color = Color.LightGray, fontSize = 12.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("None", "Shadow", "Outline").forEachIndexed { idx, label ->
                            FilterChip(
                                selected = subEdgeStyle == idx,
                                onClick = { subEdgeStyle = idx },
                                label = { Text(label, fontSize = 11.sp) }
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Use ASS/SSA Embedded Styles", color = Color.White, fontSize = 12.sp, modifier = Modifier.weight(1f))
                        Switch(checked = subEmbeddedStylesEnabled, onCheckedChange = { subEmbeddedStylesEnabled = it })
                    }

                    Text("Sub Sync Adjustment: ${subSyncOffsetMs / 1000.0}s", color = Color.LightGray, fontSize = 12.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = {
                            subSyncOffsetMs -= 100L
                            activePlayer?.let { it.seekTo((it.currentPosition - 100L).coerceAtLeast(0L)) }
                        }) { Text("-0.1s", fontSize = 12.sp) }
                        OutlinedButton(onClick = { subSyncOffsetMs = 0L }) { Text("Reset", fontSize = 12.sp) }
                        OutlinedButton(onClick = {
                            subSyncOffsetMs += 100L
                            activePlayer?.let { it.seekTo((it.currentPosition + 100L).coerceAtMost(durationMs)) }
                        }) { Text("+0.1s", fontSize = 12.sp) }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSubStyleDialog = false }) { Text("Done", color = RinoxCyan) }
            }
        )
    }
    if (showSleepDialog) {
        AlertDialog(
            onDismissRequest = { showSleepDialog = false },
            containerColor = DarkCard,
            title = { Text("Sleep Timer", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    listOf(0 to "Off", 15 to "15 Minutes", 30 to "30 Minutes", 45 to "45 Minutes", 60 to "60 Minutes").forEach { (mins, label) ->
                        TextButton(onClick = {
                            sleepTimerMinutes = mins
                            showSleepDialog = false
                            Toast.makeText(context, if (mins == 0) "Sleep Timer Off" else "Sleep Timer: $label", Toast.LENGTH_SHORT).show()
                        }) {
                            Text(label, color = if (sleepTimerMinutes == mins) RinoxCyan else Color.White)
                        }
                    }
                }
            },
            confirmButton = {}
        )
    }

    if (showEqDialog) {
        val presets = remember { engine.getEqualizerPresets() }
        AlertDialog(
            onDismissRequest = { showEqDialog = false },
            containerColor = DarkCard,
            title = { Text("Audio Equalizer", color = Color.White) },
            text = {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        FilterChip(selected = selectedEqPreset == -1, onClick = { selectedEqPreset = -1; engine.applyEqualizerPreset(-1) }, label = { Text("Off") })
                    }
                    items(presets.size) { idx ->
                        FilterChip(selected = selectedEqPreset == idx, onClick = { selectedEqPreset = idx; engine.applyEqualizerPreset(idx) }, label = { Text(presets[idx]) })
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showEqDialog = false }) { Text("Done", color = RinoxCyan) } }
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
            title = { Text("Subtitles (All Formats)", color = Color.White) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Button(
                        onClick = { showSubDialog = false; subPickerLauncher.launch(arrayOf("*/*")) },
                        colors = ButtonDefaults.buttonColors(containerColor = RinoxBlue),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Open Subtitle (.srt/.ass/.vtt/.ttml/.sub)")
                    }
                    OutlinedButton(
                        onClick = {
                            showSubDialog = false
                            try {
                                val query = Uri.encode(currentVideo.title)
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://opensubtitles.org"))
                                context.startActivity(intent)
                            } catch (_: Exception) {}
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.Language, contentDescription = null, tint = RinoxCyan, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Search Online Subtitles", color = RinoxCyan)
                    }
                    TextButton(onClick = { showSubDialog = false; showSubStyleDialog = true }) {
                        Text("Resize, Style, Position & Sync...", color = RinoxCyan, fontWeight = FontWeight.Bold)
                    }
                    TextButton(onClick = { engine.disableTrackType(C.TRACK_TYPE_TEXT); showSubDialog = false }) {
                        Text("Disable Subtitles", color = Color.LightGray)
                    }
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

@Composable
private fun QuickShortcutChip(
    icon: ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(if (active) RinoxBlue.copy(alpha = 0.35f) else Color.Black.copy(alpha = 0.45f))
            .border(1.dp, if (active) RinoxCyan else Color.White.copy(alpha = 0.2f), CircleShape)
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = if (active) RinoxCyan else Color.White, modifier = Modifier.size(14.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text(label, color = if (active) RinoxCyan else Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun PanelActionRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(10.dp))
        Text(label, color = Color.White, fontSize = 13.sp)
    }
}
