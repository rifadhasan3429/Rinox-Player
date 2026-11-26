package com.rinox.videoplayer.data

import android.net.Uri

data class VideoItem(
    val id: Long,
    val title: String,
    val displayName: String,
    val path: String,
    val folderName: String,
    val folderPath: String,
    val durationMs: Long,
    val sizeBytes: Long,
    val width: Int,
    val height: Int,
    val mimeType: String,
    val dateAdded: Long,
    val uri: Uri,
    val fps: Int = 30,
    val hasSubtitle: Boolean = false,
    val subtitleTypes: List<String> = emptyList(),
    val isNew: Boolean = true,
    val isPrivate: Boolean = false
)

data class VideoFolder(
    val name: String,
    val path: String,
    val videos: List<VideoItem>,
    val totalDurationMs: Long,
    val newCount: Int
)

enum class SortOption(val label: String) {
    NAME("Name"),
    DATE("Date"),
    SIZE("Size"),
    DURATION("Duration")
}

data class AppSettings(
    val accentIndex: Int = 0,
    val showFolderDuration: Boolean = false,
    val showNewBadge: Boolean = true,
    val showSubtitleBadge: Boolean = true,
    val showVideoSize: Boolean = false,
    val showVideoFps: Boolean = false,
    val isGridView: Boolean = false,
    val seekStepSeconds: Int = 10,
    val swipeSeekSensitivity: Float = 1.0f,
    val enableEdgeSwipes: Boolean = true,
    val resumePlayback: Boolean = true,
    val defaultDecoder: String = "HW",
    val smoothPerformanceMode: Boolean = false,
    val autoHeadphonePlay: Boolean = true,
    val pauseOnUnplug: Boolean = true,
    val defaultAudioLang: String = "Auto",
    val defaultSubLang: String = "Auto",
    val subFontSize: Int = 18,
    val autoLoadSubtitles: Boolean = true,
    val privacyPin: String = "",
    val useBiometric: Boolean = false
)
