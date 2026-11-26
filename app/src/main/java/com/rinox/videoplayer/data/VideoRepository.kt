package com.rinox.videoplayer.data

import android.content.ContentUris
import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import java.io.File
import java.util.Locale

class VideoRepository(
    private val context: Context,
    private val settingsManager: SettingsManager
) {
    companion object {
        private var cachedVideos: List<VideoItem>? = null
        private var cachedPrivate: List<VideoItem>? = null
    }

    private val privateVaultDir: File by lazy {
        File(context.filesDir, "rinox_private_vault").apply {
            if (!exists()) mkdirs()
            val noMedia = File(this, ".nomedia")
            if (!noMedia.exists()) noMedia.createNewFile()
        }
    }

    fun getInstantCachedVideos(): List<VideoItem> = cachedVideos ?: emptyList()
    fun getInstantCachedPrivate(): List<VideoItem> = cachedPrivate ?: emptyList()

    fun scanAllVideos(forceRefresh: Boolean = false): List<VideoItem> {
        if (!forceRefresh && cachedVideos != null) {
            return cachedVideos!!
        }
        val videoList = mutableListOf<VideoItem>()
        val excluded = settingsManager.getExcludedFolders()
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }

        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.TITLE,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DATA,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT,
            MediaStore.Video.Media.MIME_TYPE,
            MediaStore.Video.Media.DATE_ADDED
        )
        try {
            context.contentResolver.query(
                collection,
                projection,
                null,
                null,
                "${MediaStore.Video.Media.DATE_ADDED} DESC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.TITLE)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val pathCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
                val durCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                val wCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH)
                val hCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT)
                val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)

                while (cursor.moveToNext()) {
                    val path = cursor.getString(pathCol) ?: continue
                    val file = File(path)
                    if (!file.exists()) continue
                    val folderPath = file.parent ?: "/Internal Storage"
                    if (excluded.contains(folderPath)) continue

                    val id = cursor.getLong(idCol)
                    val displayName = cursor.getString(nameCol) ?: file.name
                    val title = cursor.getString(titleCol) ?: file.nameWithoutExtension
                    val uri = ContentUris.withAppendedId(collection, id)
                    val subs = detectExternalSubtitles(file)

                    videoList.add(
                        VideoItem(
                            id = id,
                            title = title,
                            displayName = displayName,
                            path = path,
                            folderName = file.parentFile?.name ?: "Internal Storage",
                            folderPath = folderPath,
                            durationMs = cursor.getLong(durCol),
                            sizeBytes = cursor.getLong(sizeCol),
                            width = cursor.getInt(wCol),
                            height = cursor.getInt(hCol),
                            mimeType = cursor.getString(mimeCol) ?: "video/mp4",
                            dateAdded = cursor.getLong(dateCol),
                            uri = uri,
                            fps = getFastVideoFps(path),
                            hasSubtitle = subs.isNotEmpty(),
                            subtitleTypes = subs,
                            isNew = !settingsManager.isVideoWatched(path),
                            isPrivate = false
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        cachedVideos = videoList
        return videoList
    }
    fun getPrivateVideos(): List<VideoItem> {
        val list = mutableListOf<VideoItem>()
        val files = privateVaultDir.listFiles() ?: return emptyList()
        files.filter { it.isFile && !it.name.startsWith(".") }.forEachIndexed { index, file ->
            val subs = detectExternalSubtitles(file)
            list.add(
                VideoItem(
                    id = -(index + 1L),
                    title = file.nameWithoutExtension,
                    displayName = file.name,
                    path = file.absolutePath,
                    folderName = "Privacy Vault",
                    folderPath = privateVaultDir.absolutePath,
                    durationMs = 0L,
                    sizeBytes = file.length(),
                    width = 1920,
                    height = 1080,
                    mimeType = "video/mp4",
                    dateAdded = file.lastModified() / 1000L,
                    uri = Uri.fromFile(file),
                    fps = getFastVideoFps(file.absolutePath),
                    hasSubtitle = subs.isNotEmpty(),
                    subtitleTypes = subs,
                    isNew = !settingsManager.isVideoWatched(file.absolutePath),
                    isPrivate = true
                )
            )
        }
        cachedPrivate = list
        return list
    }

    fun moveToPrivacy(video: VideoItem): Boolean {
        return try {
            val source = File(video.path)
            if (!source.exists()) return false
            val dest = File(privateVaultDir, source.name)
            source.copyTo(dest, overwrite = true)
            source.delete()
            context.contentResolver.delete(video.uri, null, null)
            cachedVideos = null
            true
        } catch (e: Exception) {
            false
        }
    }

    fun unhideFromPrivacy(video: VideoItem): Boolean {
        return try {
            val source = File(video.path)
            val moviesDir = File("/storage/emulated/0/Movies/RinoxRestored").apply { mkdirs() }
            val dest = File(moviesDir, source.name)
            source.copyTo(dest, overwrite = true)
            source.delete()
            cachedVideos = null
            true
        } catch (e: Exception) {
            false
        }
    }

    fun deleteVideo(video: VideoItem): Boolean {
        return try {
            val file = File(video.path)
            if (file.exists()) file.delete()
            if (!video.isPrivate) {
                context.contentResolver.delete(video.uri, null, null)
            }
            cachedVideos = null
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun detectExternalSubtitles(videoFile: File): List<String> {
        val parent = videoFile.parentFile ?: return emptyList()
        val baseName = videoFile.nameWithoutExtension
        val exts = listOf("srt", "ass", "ssa", "sub", "vtt")
        return exts.filter { ext ->
            File(parent, "$baseName.$ext").exists()
        }.map { it.uppercase(Locale.US) }
    }

    private fun getFastVideoFps(path: String): Int {
        val saved = settingsManager.getCachedFps(path)
        if (saved > 0) return saved
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(path)
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("video/") && format.containsKey(MediaFormat.KEY_FRAME_RATE)) {
                    val fps = format.getInteger(MediaFormat.KEY_FRAME_RATE)
                    settingsManager.cacheFps(path, fps)
                    return fps
                }
            }
            settingsManager.cacheFps(path, 30)
            30
        } catch (_: Exception) {
            30
        } finally {
            try { extractor.release() } catch (_: Exception) {}
        }
    }
}
