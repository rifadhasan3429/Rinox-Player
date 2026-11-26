package com.rinox.videoplayer.data

import android.content.Context
import android.content.SharedPreferences

class SettingsManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("rinox_offline_prefs", Context.MODE_PRIVATE)

    fun loadSettings(): AppSettings {
        return AppSettings(
            accentIndex = prefs.getInt("accentIndex", 0),
            showFolderDuration = prefs.getBoolean("showFolderDuration", false),
            showNewBadge = prefs.getBoolean("showNewBadge", true),
            showSubtitleBadge = prefs.getBoolean("showSubtitleBadge", true),
            showVideoSize = prefs.getBoolean("showVideoSize", false),
            showVideoFps = prefs.getBoolean("showVideoFps", false),
            isGridView = prefs.getBoolean("isGridView", false),
            seekStepSeconds = prefs.getInt("seekStepSeconds", 10),
            swipeSeekSensitivity = prefs.getFloat("swipeSeekSensitivity", 1.0f),
            enableEdgeSwipes = prefs.getBoolean("enableEdgeSwipes", true),
            resumePlayback = prefs.getBoolean("resumePlayback", true),
            defaultDecoder = prefs.getString("defaultDecoder", "HW") ?: "HW",
            smoothPerformanceMode = prefs.getBoolean("smoothPerformanceMode", false),
            autoHeadphonePlay = prefs.getBoolean("autoHeadphonePlay", true),
            pauseOnUnplug = prefs.getBoolean("pauseOnUnplug", true),
            defaultAudioLang = prefs.getString("defaultAudioLang", "Auto") ?: "Auto",
            defaultSubLang = prefs.getString("defaultSubLang", "Auto") ?: "Auto",
            subFontSize = prefs.getInt("subFontSize", 18),
            autoLoadSubtitles = prefs.getBoolean("autoLoadSubtitles", true),
            privacyPin = prefs.getString("privacyPin", "") ?: "",
            useBiometric = prefs.getBoolean("useBiometric", false)
        )
    }

    fun saveSettings(s: AppSettings) {
        prefs.edit().apply {
            putInt("accentIndex", s.accentIndex)
            putBoolean("showFolderDuration", s.showFolderDuration)
            putBoolean("showNewBadge", s.showNewBadge)
            putBoolean("showSubtitleBadge", s.showSubtitleBadge)
            putBoolean("showVideoSize", s.showVideoSize)
            putBoolean("showVideoFps", s.showVideoFps)
            putBoolean("isGridView", s.isGridView)
            putInt("seekStepSeconds", s.seekStepSeconds)
            putFloat("swipeSeekSensitivity", s.swipeSeekSensitivity)
            putBoolean("enableEdgeSwipes", s.enableEdgeSwipes)
            putBoolean("resumePlayback", s.resumePlayback)
            putString("defaultDecoder", s.defaultDecoder)
            putBoolean("smoothPerformanceMode", s.smoothPerformanceMode)
            putBoolean("autoHeadphonePlay", s.autoHeadphonePlay)
            putBoolean("pauseOnUnplug", s.pauseOnUnplug)
            putString("defaultAudioLang", s.defaultAudioLang)
            putString("defaultSubLang", s.defaultSubLang)
            putInt("subFontSize", s.subFontSize)
            putBoolean("autoLoadSubtitles", s.autoLoadSubtitles)
            putString("privacyPin", s.privacyPin)
            putBoolean("useBiometric", s.useBiometric)
            apply()
        }
    }
    fun isVideoWatched(path: String): Boolean {
        return prefs.getBoolean("watched_$path", false)
    }

    fun markVideoWatched(path: String) {
        prefs.edit().putBoolean("watched_$path", true).apply()
    }

    fun saveLastPlayedPath(path: String) {
        prefs.edit().putString("last_played_path", path).apply()
    }

    fun getLastPlayedPath(): String? {
        return prefs.getString("last_played_path", null)
    }

    fun getExcludedFolders(): Set<String> {
        return prefs.getStringSet("excluded_folders", emptySet()) ?: emptySet()
    }

    fun toggleExcludedFolder(folderPath: String) {
        val current = getExcludedFolders().toMutableSet()
        if (current.contains(folderPath)) current.remove(folderPath) else current.add(folderPath)
        prefs.edit().putStringSet("excluded_folders", current).apply()
    }

    fun clearExcludedFolders() {
        prefs.edit().remove("excluded_folders").apply()
    }
}
