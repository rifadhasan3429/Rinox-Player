package com.rinox.videoplayer.player

import android.content.Context
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import java.io.File

data class TrackInfoItem(
    val id: String,
    val label: String,
    val groupIndex: Int,
    val trackIndex: Int,
    val isSelected: Boolean
)
@OptIn(UnstableApi::class)
class RinoxPlayerEngine(private val context: Context) {

    var exoPlayer: ExoPlayer? = null
        private set
    private var trackSelector: DefaultTrackSelector? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var nativeEqualizer: Equalizer? = null
    var activeDecoderMode: String = "HW"
        private set

    fun buildPlayer(
        decoderMode: String,
        onErrorFallback: (String) -> Unit,
        onVideoEnded: () -> Unit,
        onTracksChanged: () -> Unit
    ): ExoPlayer {
        release()
        activeDecoderMode = decoderMode

        val extMode = when (decoderMode) {
            "SW" -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER
            "HW+" -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON
            else -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF
        }

        val renderersFactory = DefaultRenderersFactory(context)
            .setExtensionRendererMode(extMode)
            .setEnableDecoderFallback(true)

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(1000, 15000, 250, 500)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        val selector = DefaultTrackSelector(context)
        trackSelector = selector

        val audioAttrs = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()

        val player = ExoPlayer.Builder(context, renderersFactory)
            .setTrackSelector(selector)
            .setLoadControl(loadControl)
            .setAudioAttributes(audioAttrs, true)
            .setHandleAudioBecomingNoisy(true)
            .setSeekParameters(SeekParameters.CLOSEST_SYNC)
            .build()

        player.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                when (activeDecoderMode) {
                    "HW" -> onErrorFallback("HW+")
                    "HW+" -> onErrorFallback("SW")
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    onVideoEnded()
                }
            }

            override fun onTracksChanged(tracks: Tracks) {
                onTracksChanged()
            }
        })

        exoPlayer = player
        return player
    }
    fun prepareMedia(uri: Uri, videoPath: String, startPositionMs: Long, autoLoadSubtitles: Boolean) {
        val player = exoPlayer ?: return
        val subConfigs = mutableListOf<MediaItem.SubtitleConfiguration>()

        if (autoLoadSubtitles && videoPath.isNotEmpty()) {
            try {
                val videoFile = File(videoPath)
                val parent = videoFile.parentFile
                val base = videoFile.nameWithoutExtension
                val subMap = mapOf(
                    "srt" to MimeTypes.APPLICATION_SUBRIP,
                    "vtt" to MimeTypes.TEXT_VTT,
                    "ass" to MimeTypes.TEXT_SSA,
                    "ssa" to MimeTypes.TEXT_SSA
                )
                subMap.forEach { (ext, mime) ->
                    val subFile = File(parent, "$base.$ext")
                    if (subFile.exists()) {
                        subConfigs.add(
                            MediaItem.SubtitleConfiguration.Builder(Uri.fromFile(subFile))
                                .setMimeType(mime)
                                .setLanguage("en")
                                .setLabel("External ${ext.uppercase()}")
                                .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                                .build()
                        )
                    }
                }
            } catch (_: Exception) {}
        }

        val mediaItem = MediaItem.Builder()
            .setUri(uri)
            .setSubtitleConfigurations(subConfigs)
            .build()

        player.setMediaItem(mediaItem)
        if (startPositionMs > 0L) {
            player.seekTo(startPositionMs)
        }
        player.prepare()
        player.playWhenReady = true
    }

    fun applySmoothVolume(volumePercent: Int) {
        val player = exoPlayer ?: return
        try {
            if (volumePercent <= 100) {
                player.volume = (volumePercent / 100f).coerceIn(0f, 1f)
                loudnessEnhancer?.enabled = false
            } else {
                player.volume = 1.0f
                val sessionId = player.audioSessionId
                if (sessionId != C.AUDIO_SESSION_ID_UNSET && sessionId != 0) {
                    if (loudnessEnhancer == null) {
                        loudnessEnhancer = LoudnessEnhancer(sessionId)
                    }
                    val gainMb = ((volumePercent - 100) * 12).coerceIn(0, 1200)
                    loudnessEnhancer?.setTargetGain(gainMb)
                    loudnessEnhancer?.enabled = true
                }
            }
        } catch (_: Exception) {}
    }

    fun getEqualizerPresets(): List<String> {
        val player = exoPlayer ?: return listOf("Normal", "Bass Boost", "Cinema", "Pop", "Rock", "Vocal")
        return try {
            val sessionId = player.audioSessionId
            if (sessionId != C.AUDIO_SESSION_ID_UNSET && sessionId != 0) {
                if (nativeEqualizer == null) nativeEqualizer = Equalizer(0, sessionId)
                val count = nativeEqualizer!!.numberOfPresets.toInt()
                (0 until count).map { nativeEqualizer!!.getPresetName(it.toShort()) }
            } else {
                listOf("Normal", "Bass Boost", "Cinema", "Pop", "Rock", "Vocal")
            }
        } catch (_: Exception) {
            listOf("Normal", "Bass Boost", "Cinema", "Pop", "Rock", "Vocal")
        }
    }

    fun applyEqualizerPreset(presetIndex: Int) {
        val player = exoPlayer ?: return
        try {
            val sessionId = player.audioSessionId
            if (sessionId == C.AUDIO_SESSION_ID_UNSET || sessionId == 0) return
            if (nativeEqualizer == null) {
                nativeEqualizer = Equalizer(0, sessionId)
            }
            if (presetIndex < 0) {
                nativeEqualizer?.enabled = false
            } else {
                nativeEqualizer?.usePreset(presetIndex.toShort())
                nativeEqualizer?.enabled = true
            }
        } catch (_: Exception) {}
    }

    fun getAudioTracks(): List<TrackInfoItem> = getTracks(C.TRACK_TYPE_AUDIO)

    fun getSubtitleTracks(): List<TrackInfoItem> = getTracks(C.TRACK_TYPE_TEXT)

    fun getTracks(trackType: @C.TrackType Int): List<TrackInfoItem> {
        val player = exoPlayer ?: return emptyList()
        val result = mutableListOf<TrackInfoItem>()
        player.currentTracks.groups.forEachIndexed { gIdx, group ->
            if (group.type == trackType) {
                for (tIdx in 0 until group.length) {
                    val format = group.getTrackFormat(tIdx)
                    val label = format.label ?: format.language ?: "Track ${result.size + 1}"
                    result.add(
                        TrackInfoItem(
                            id = "$gIdx-$tIdx",
                            label = label,
                            groupIndex = gIdx,
                            trackIndex = tIdx,
                            isSelected = group.isTrackSelected(tIdx)
                        )
                    )
                }
            }
        }
        return result
    }

    fun selectTrack(trackType: @C.TrackType Int, groupIndex: Int, trackIndex: Int) {
        val player = exoPlayer ?: return
        val selector = trackSelector ?: return
        val groups = player.currentTracks.groups
        if (groupIndex in groups.indices) {
            val targetGroup = groups[groupIndex].mediaTrackGroup
            selector.parameters = selector.buildUponParameters()
                .setTrackTypeDisabled(trackType, false)
                .setOverrideForType(TrackSelectionOverride(targetGroup, trackIndex))
                .build()
        }
    }

    fun disableTrackType(trackType: @C.TrackType Int) {
        val selector = trackSelector ?: return
        selector.parameters = selector.buildUponParameters()
            .setTrackTypeDisabled(trackType, true)
            .build()
    }

    fun release() {
        try {
            nativeEqualizer?.enabled = false
            nativeEqualizer?.release()
            nativeEqualizer = null
        } catch (_: Exception) {}
        try {
            loudnessEnhancer?.enabled = false
            loudnessEnhancer?.release()
            loudnessEnhancer = null
        } catch (_: Exception) {}
        try {
            exoPlayer?.stop()
            exoPlayer?.release()
            exoPlayer = null
        } catch (_: Exception) {}
    }
}
