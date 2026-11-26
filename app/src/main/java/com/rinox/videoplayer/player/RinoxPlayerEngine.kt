package com.rinox.videoplayer.player

import android.content.Context
import android.net.Uri
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.interfaces.IMedia
import org.videolan.libvlc.util.VLCVideoLayout
import java.io.File

data class TrackInfoItem(
    val id: Int,
    val label: String,
    val isSelected: Boolean
)
class RinoxPlayerEngine(private val context: Context) {

    private var libVLC: LibVLC? = null
    var mediaPlayer: MediaPlayer? = null
        private set
    private var equalizer: MediaPlayer.Equalizer? = null
    var activeDecoderMode: String = "HW"
        private set

    fun buildPlayer(
        decoderMode: String,
        onErrorFallback: (String) -> Unit,
        onVideoEnded: () -> Unit,
        onTimeChanged: (Long, Long) -> Unit,
        onTracksUpdated: () -> Unit
    ): MediaPlayer {
        release()
        activeDecoderMode = decoderMode

        val options = arrayListOf(
            "--no-drop-late-frames",
            "--no-skip-frames",
            "--file-caching=300",
            "--network-caching=300",
            "--aout=opensles",
            "--audio-time-stretch"
        )

        val vlc = LibVLC(context, options)
        libVLC = vlc
        val player = MediaPlayer(vlc)

        player.setEventListener { event ->
            when (event.type) {
                MediaPlayer.Event.EncounteredError -> {
                    if (activeDecoderMode != "SW") {
                        onErrorFallback("SW")
                    }
                }
                MediaPlayer.Event.EndReached -> {
                    onVideoEnded()
                }
                MediaPlayer.Event.TimeChanged -> {
                    onTimeChanged(player.time, player.length)
                }
                MediaPlayer.Event.ESAdded, MediaPlayer.Event.ESSelected -> {
                    onTracksUpdated()
                }
            }
        }

        mediaPlayer = player
        return player
    }

    fun attachVideoLayout(layout: VLCVideoLayout) {
        try {
            mediaPlayer?.attachViews(layout, null, true, false)
        } catch (_: Exception) {}
    }

    fun detachVideoLayout() {
        try {
            mediaPlayer?.detachViews()
        } catch (_: Exception) {}
    }
    fun prepareMedia(uri: Uri, videoPath: String, startPositionMs: Long, autoLoadSubtitles: Boolean) {
        val vlc = libVLC ?: return
        val player = mediaPlayer ?: return

        val media = if (videoPath.isNotEmpty() && File(videoPath).exists()) {
            Media(vlc, videoPath)
        } else {
            Media(vlc, uri)
        }

        when (activeDecoderMode) {
            "SW" -> media.setHWDecoderEnabled(false, false)
            "HW+" -> media.setHWDecoderEnabled(true, false)
            else -> media.setHWDecoderEnabled(true, true)
        }

        if (autoLoadSubtitles && videoPath.isNotEmpty()) {
            try {
                val videoFile = File(videoPath)
                val parent = videoFile.parentFile
                val base = videoFile.nameWithoutExtension
                listOf("srt", "ass", "ssa", "sub", "vtt").forEach { ext ->
                    val subFile = File(parent, "$base.$ext")
                    if (subFile.exists()) {
                        media.addSlave(IMedia.Slave(IMedia.Slave.Type.Subtitle, 4, Uri.fromFile(subFile).toString()))
                    }
                }
            } catch (_: Exception) {}
        }

        player.media = media
        media.release()
        player.play()

        if (startPositionMs > 0L) {
            player.time = startPositionMs
        }
    }

    fun applySafeVolume(volumePercent: Int) {
        try {
            mediaPlayer?.volume = volumePercent.coerceIn(0, 200)
        } catch (_: Exception) {}
    }

    fun applyEqualizerPreset(presetIndex: Int, customBands: FloatArray? = null) {
        val player = mediaPlayer ?: return
        try {
            if (presetIndex < 0 && customBands == null) {
                player.setEqualizer(null)
                equalizer = null
                return
            }
            val eq = if (presetIndex >= 0 && presetIndex < MediaPlayer.Equalizer.getPresetCount()) {
                MediaPlayer.Equalizer.createFromPreset(presetIndex)
            } else {
                MediaPlayer.Equalizer.create()
            }
            customBands?.forEachIndexed { idx, amp ->
                if (idx < MediaPlayer.Equalizer.getBandCount()) {
                    eq.setAmp(idx, amp.coerceIn(-20f, 20f))
                }
            }
            equalizer = eq
            player.setEqualizer(eq)
        } catch (_: Exception) {}
    }

    fun getEqualizerPresets(): List<String> {
        return try {
            val count = MediaPlayer.Equalizer.getPresetCount()
            (0 until count).map { MediaPlayer.Equalizer.getPresetName(it) }
        } catch (_: Exception) {
            listOf("Flat", "Bass Boost", "Cinema", "Pop", "Rock", "Vocal")
        }
    }

    fun getAudioTracks(): List<TrackInfoItem> {
        val player = mediaPlayer ?: return emptyList()
        val current = player.audioTrack
        return player.audioTracks?.map {
            TrackInfoItem(id = it.id, label = it.name ?: "Track ${it.id}", isSelected = it.id == current)
        } ?: emptyList()
    }

    fun getSubtitleTracks(): List<TrackInfoItem> {
        val player = mediaPlayer ?: return emptyList()
        val current = player.spuTrack
        return player.spuTracks?.map {
            TrackInfoItem(id = it.id, label = it.name ?: "Sub ${it.id}", isSelected = it.id == current)
        } ?: emptyList()
    }

    fun selectAudioTrack(id: Int) {
        try { mediaPlayer?.audioTrack = id } catch (_: Exception) {}
    }

    fun selectSubtitleTrack(id: Int) {
        try { mediaPlayer?.spuTrack = id } catch (_: Exception) {}
    }

    fun setAspectRatioMode(modeIndex: Int) {
        val player = mediaPlayer ?: return
        try {
            when (modeIndex) {
                0 -> { player.aspectRatio = null; player.scale = 0f }
                1 -> { player.aspectRatio = "16:9"; player.scale = 0f }
                2 -> { player.aspectRatio = null; player.scale = 1.35f }
                else -> { player.aspectRatio = null; player.scale = 1.0f }
            }
        } catch (_: Exception) {}
    }

    fun release() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.detachViews()
            mediaPlayer?.release()
            mediaPlayer = null
        } catch (_: Exception) {}
        try {
            libVLC?.release()
            libVLC = null
        } catch (_: Exception) {}
    }
}
