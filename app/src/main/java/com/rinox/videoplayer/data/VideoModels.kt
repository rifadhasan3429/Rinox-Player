package com.rinox.videoplayer.data

import android.net.Uri

data class AppSettings(
    val isDarkTheme: Boolean = true,
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

data class VideoItem(
    val id: Long,
    val title: String,
    val displayName: String,
    val path: String,
    val uri: Uri,
    val folderName: String,
    val folderPath: String,
    val durationMs: Long,
    val sizeBytes: Long,
    val width: Int,
    val height: Int,
    val dateAddedSec: Long,
    val mimeType: String,
    val fps: Int = 30,
    val isWatched: Boolean = false,
    val isPrivate: Boolean = false,
    val lastPositionMs: Long = 0L
) {
    val resolutionLabel: String
        get() = when {
            width >= 3840 || height >= 2160 -> "4K"
            width >= 2560 || height >= 1440 -> "2K"
            width >= 1920 || height >= 1080 -> "1080p"
            width >= 1280 || height >= 720 -> "720p"
            width >= 854 || height >= 480 -> "480p"
            width > 0 -> "${height}p"
            else -> "HD"
        }

    val watchProgressFraction: Float
        get() = if (durationMs > 0L && lastPositionMs > 0L) {
            (lastPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
        } else 0f
}

data class VideoFolder(
    val name: String,
    val path: String,
    val videos: List<VideoItem>,
    val totalSizeBytes: Long,
    val hasUnwatched: Boolean
) {
    val videoCount: Int get() = videos.size
    val totalDurationMs: Long get() = videos.sumOf { it.durationMs }
}

enum class BrowserViewMode(val label: String) {
    ALL_FOLDERS("All folders"),
    FILES("Files"),
    FOLDERS("Folders")
}

enum class BrowserLayoutMode(val label: String) {
    LIST("List"),
    GRID("Grid")
}

enum class SortOption(val label: String) {
    TITLE("Title"),
    DATE("Date"),
    PLAYED_TIME("Played time"),
    STATUS("Status"),
    LENGTH("Length"),
    SIZE("Size"),
    RESOLUTION("Resolution"),
    PATH("Path"),
    FRAME_RATE("Frame rate"),
    TYPE("Type")
}

enum class SortDirection(val label: String) {
    ASCENDING("Ascending"),
    DESCENDING("Descending")
}

data class BrowserCardFields(
    val showSize: Boolean = true,
    val showFps: Boolean = true,
    val showDuration: Boolean = true,
    val showResolution: Boolean = true,
    val showPath: Boolean = false,
    val showExtension: Boolean = true,
    val showNewTag: Boolean = true
)
