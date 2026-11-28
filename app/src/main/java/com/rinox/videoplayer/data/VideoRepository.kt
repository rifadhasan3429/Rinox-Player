package com.rinox.videoplayer.data

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

class VideoRepository(private val context: Context) {

    private val privateDir: File by lazy {
        File(context.filesDir, "rinox_private_vault").apply {
            if (!exists()) mkdirs()
        }
    }

    suspend fun loadAllMedia(
        settings: AppSettings,
        viewMode: BrowserViewMode = BrowserViewMode.ALL_FOLDERS,
        sortOption: SortOption = SortOption.TITLE,
        sortDirection: SortDirection = SortDirection.ASCENDING
    ): Pair<List<VideoFolder>, List<VideoItem>> = withContext(Dispatchers.IO) {
        val allVideos = mutableListOf<VideoItem>()
        val positionPrefs = context.getSharedPreferences("rinox_positions", Context.MODE_PRIVATE)
        val allPositions = positionPrefs.all

        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }

        val projection = mutableListOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.TITLE,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DATA,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.MIME_TYPE
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            projection.add(MediaStore.Video.Media.CAPTURE_FRAMERATE)
        }

        val sortOrder = "${MediaStore.Video.Media.DATE_ADDED} DESC"

        try {
            context.contentResolver.query(
                collection,
                projection.toTypedArray(),
                null,
                null,
                sortOrder
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.TITLE)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
                val durCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                val wCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH)
                val hCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
                val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE)
                val fpsCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    cursor.getColumnIndex(MediaStore.Video.Media.CAPTURE_FRAMERATE)
                } else -1

                while (cursor.moveToNext()) {
                    val path = cursor.getString(dataCol) ?: continue
                    if (path.isEmpty()) continue

                    val file = File(path)
                    val parentFile = file.parentFile ?: continue
                    val folderPath = parentFile.absolutePath
                    val folderName = parentFile.name.ifEmpty { "Internal Storage" }

                    if (!settings.showHiddenFiles && (file.name.startsWith(".") || folderName.startsWith("."))) {
                        continue
                    }
                    if (settings.excludedFolders.any { ex -> folderPath.equals(ex, ignoreCase = true) }) {
                        continue
                    }

                    val id = cursor.getLong(idCol)
                    val title = cursor.getString(titleCol) ?: file.nameWithoutExtension
                    val displayName = cursor.getString(nameCol) ?: file.name
                    val duration = cursor.getLong(durCol).coerceAtLeast(0L)
                    val size = cursor.getLong(sizeCol).coerceAtLeast(0L)
                    val width = cursor.getInt(wCol).coerceAtLeast(0)
                    val height = cursor.getInt(hCol).coerceAtLeast(0)
                    val dateAdded = cursor.getLong(dateCol).coerceAtLeast(0L)
                    val mime = cursor.getString(mimeCol) ?: "video/mp4"
                    val rawFps = if (fpsCol >= 0) cursor.getFloat(fpsCol).toInt() else 30
                    val fps = if (rawFps in 1..240) rawFps else 30
                    val contentUri = ContentUris.withAppendedId(collection, id)
                    val savedPos = (allPositions[path] as? Long) ?: 0L

                    allVideos.add(
                        VideoItem(
                            id = id,
                            title = title,
                            displayName = displayName,
                            path = path,
                            uri = contentUri,
                            folderName = folderName,
                            folderPath = folderPath,
                            durationMs = duration,
                            sizeBytes = size,
                            width = width,
                            height = height,
                            dateAddedSec = dateAdded,
                            mimeType = mime,
                            fps = fps,
                            isWatched = settings.watchedVideoPaths.contains(path),
                            isPrivate = false,
                            lastPositionMs = savedPos
                        )
                    )
                }
            }
        } catch (_: Exception) {}

        val sortedVideos = sortVideos(allVideos, sortOption, sortDirection)

        val folderMap = sortedVideos.groupBy { it.folderPath }
        val rawFolders = folderMap.map { (fPath, vids) ->
            val fName = vids.firstOrNull()?.folderName ?: File(fPath).name
            val sortedFolderVids = sortVideos(vids, sortOption, sortDirection)
            VideoFolder(
                name = fName,
                path = fPath,
                videos = sortedFolderVids,
                totalSizeBytes = sortedFolderVids.sumOf { it.sizeBytes },
                hasUnwatched = sortedFolderVids.any { !it.isWatched }
            )
        }

        val sortedFolders = sortFolders(rawFolders, sortOption, sortDirection)
        Pair(sortedFolders, sortedVideos)
    }
    fun sortVideos(
        videos: List<VideoItem>,
        option: SortOption,
        direction: SortDirection
    ): List<VideoItem> {
        val comparator = Comparator<VideoItem> { a, b ->
            val baseResult = when (option) {
                SortOption.TITLE -> naturalCompare(a.displayName, b.displayName)
                SortOption.DATE -> a.dateAddedSec.compareTo(b.dateAddedSec)
                SortOption.PLAYED_TIME -> a.lastPositionMs.compareTo(b.lastPositionMs)
                SortOption.STATUS -> a.isWatched.compareTo(b.isWatched)
                SortOption.LENGTH -> a.durationMs.compareTo(b.durationMs)
                SortOption.SIZE -> a.sizeBytes.compareTo(b.sizeBytes)
                SortOption.RESOLUTION -> (a.width.toLong() * a.height).compareTo(b.width.toLong() * b.height)
                SortOption.PATH -> naturalCompare(a.path, b.path)
                SortOption.FRAME_RATE -> a.fps.compareTo(b.fps)
                SortOption.TYPE -> a.displayName.substringAfterLast('.', "").lowercase()
                    .compareTo(b.displayName.substringAfterLast('.', "").lowercase())
            }
            if (direction == SortDirection.ASCENDING) baseResult else -baseResult
        }
        return videos.sortedWith(comparator)
    }

    fun sortFolders(
        folders: List<VideoFolder>,
        option: SortOption,
        direction: SortDirection
    ): List<VideoFolder> {
        val comparator = Comparator<VideoFolder> { a, b ->
            val baseResult = when (option) {
                SortOption.TITLE -> naturalCompare(a.name, b.name)
                SortOption.DATE -> (a.videos.maxOfOrNull { it.dateAddedSec } ?: 0L)
                    .compareTo(b.videos.maxOfOrNull { it.dateAddedSec } ?: 0L)
                SortOption.LENGTH -> a.totalDurationMs.compareTo(b.totalDurationMs)
                SortOption.SIZE -> a.totalSizeBytes.compareTo(b.totalSizeBytes)
                SortOption.PATH -> naturalCompare(a.path, b.path)
                else -> naturalCompare(a.name, b.name)
            }
            if (direction == SortDirection.ASCENDING) baseResult else -baseResult
        }
        return folders.sortedWith(comparator)
    }

    // Natural Episode Serial Comparator: Episode 1, Episode 2 ... Episode 10
    fun naturalCompare(s1: String, s2: String): Int {
        var i1 = 0
        var i2 = 0
        val len1 = s1.length
        val len2 = s2.length

        while (i1 < len1 && i2 < len2) {
            val c1 = s1[i1]
            val c2 = s2[i2]

            if (c1.isDigit() && c2.isDigit()) {
                var num1 = 0L
                while (i1 < len1 && s1[i1].isDigit()) {
                    num1 = num1 * 10 + (s1[i1] - '0')
                    i1++
                }
                var num2 = 0L
                while (i2 < len2 && s2[i2].isDigit()) {
                    num2 = num2 * 10 + (s2[i2] - '0')
                    i2++
                }
                if (num1 != num2) return num1.compareTo(num2)
            } else {
                val lower1 = c1.lowercaseChar()
                val lower2 = c2.lowercaseChar()
                if (lower1 != lower2) return lower1.compareTo(lower2)
                i1++
                i2++
            }
        }
        return len1.compareTo(len2)
    }
    suspend fun getAvailableTargetFolders(existingFolders: List<VideoFolder>): List<File> = withContext(Dispatchers.IO) {
        val folderSet = linkedSetOf<String>()
        val rootDir = Environment.getExternalStorageDirectory()
        listOf("Movies", "Download", "DCIM", "Video", "Videos").forEach { sub ->
            val dir = File(rootDir, sub)
            if (dir.exists() || dir.mkdirs()) {
                folderSet.add(dir.absolutePath)
            }
        }
        existingFolders.forEach { folderSet.add(it.path) }
        folderSet.map { File(it) }.filter { it.exists() && it.isDirectory }
    }

    suspend fun renameVideo(video: VideoItem, newBaseName: String): Boolean = withContext(Dispatchers.IO) {
        val cleanName = newBaseName.trim().replace(Regex("[\\\\/:*?\"<>|]"), "_")
        if (cleanName.isEmpty()) return@withContext false

        val oldFile = File(video.path)
        if (!oldFile.exists()) return@withContext false

        val ext = oldFile.extension
        val finalFileName = if (ext.isNotEmpty() && !cleanName.endsWith(".$ext", ignoreCase = true)) {
            "$cleanName.$ext"
        } else cleanName

        val newFile = File(oldFile.parentFile, finalFileName)
        if (newFile.exists()) return@withContext false

        try {
            if (oldFile.renameTo(newFile)) {
                val values = ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, finalFileName)
                    put(MediaStore.Video.Media.TITLE, newFile.nameWithoutExtension)
                    put(MediaStore.Video.Media.DATA, newFile.absolutePath)
                }
                context.contentResolver.update(video.uri, values, null, null)
                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(oldFile.absolutePath, newFile.absolutePath),
                    null,
                    null
                )
                return@withContext true
            }
        } catch (_: Exception) {}
        false
    }
    suspend fun copyVideoToFolder(video: VideoItem, targetFolder: File): Boolean = withContext(Dispatchers.IO) {
        try {
            val srcFile = File(video.path)
            if (!srcFile.exists()) return@withContext false
            if (!targetFolder.exists()) targetFolder.mkdirs()

            var destFile = File(targetFolder, srcFile.name)
            var counter = 1
            while (destFile.exists()) {
                val base = srcFile.nameWithoutExtension
                val ext = srcFile.extension
                destFile = File(targetFolder, "${base}_($counter).$ext")
                counter++
            }

            FileInputStream(srcFile).use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output, bufferSize = 65536)
                }
            }
            MediaScannerConnection.scanFile(context, arrayOf(destFile.absolutePath), null, null)
            true
        } catch (_: Exception) {
            false
        }
    }

    suspend fun moveVideoToFolder(video: VideoItem, targetFolder: File): Boolean = withContext(Dispatchers.IO) {
        try {
            val srcFile = File(video.path)
            if (!srcFile.exists()) return@withContext false
            if (srcFile.parentFile?.absolutePath == targetFolder.absolutePath) return@withContext false
            if (!targetFolder.exists()) targetFolder.mkdirs()

            var destFile = File(targetFolder, srcFile.name)
            var counter = 1
            while (destFile.exists()) {
                val base = srcFile.nameWithoutExtension
                val ext = srcFile.extension
                destFile = File(targetFolder, "${base}_($counter).$ext")
                counter++
            }

            val movedDirect = srcFile.renameTo(destFile)
            if (!movedDirect) {
                FileInputStream(srcFile).use { input ->
                    FileOutputStream(destFile).use { output ->
                        input.copyTo(output, bufferSize = 65536)
                    }
                }
                if (destFile.exists() && destFile.length() == srcFile.length()) {
                    srcFile.delete()
                } else {
                    return@withContext false
                }
            }

            try {
                context.contentResolver.delete(video.uri, null, null)
            } catch (_: Exception) {}

            MediaScannerConnection.scanFile(
                context,
                arrayOf(srcFile.absolutePath, destFile.absolutePath),
                null,
                null
            )
            true
        } catch (_: Exception) {
            false
        }
    }
    suspend fun deleteVideo(video: VideoItem): Boolean = withContext(Dispatchers.IO) {
        try {
            val file = File(video.path)
            val fileDeleted = if (file.exists()) file.delete() else true
            val rows = try {
                context.contentResolver.delete(video.uri, null, null)
            } catch (_: Exception) { 0 }
            MediaScannerConnection.scanFile(context, arrayOf(video.path), null, null)
            fileDeleted || rows > 0
        } catch (_: Exception) {
            false
        }
    }

    suspend fun renameFolder(folder: VideoFolder, newFolderName: String): Boolean = withContext(Dispatchers.IO) {
        val cleanName = newFolderName.trim().replace(Regex("[\\\\/:*?\"<>|]"), "_")
        if (cleanName.isEmpty()) return@withContext false
        val oldDir = File(folder.path)
        if (!oldDir.exists() || !oldDir.isDirectory) return@withContext false
        val newDir = File(oldDir.parentFile, cleanName)
        if (newDir.exists()) return@withContext false

        try {
            if (oldDir.renameTo(newDir)) {
                val pathsToScan = mutableListOf(oldDir.absolutePath, newDir.absolutePath)
                folder.videos.forEach { vid ->
                    val oldVidFile = File(vid.path)
                    val newVidFile = File(newDir, oldVidFile.name)
                    pathsToScan.add(vid.path)
                    pathsToScan.add(newVidFile.absolutePath)
                    try {
                        val values = ContentValues().apply {
                            put(MediaStore.Video.Media.DATA, newVidFile.absolutePath)
                        }
                        context.contentResolver.update(vid.uri, values, null, null)
                    } catch (_: Exception) {}
                }
                MediaScannerConnection.scanFile(context, pathsToScan.toTypedArray(), null, null)
                return@withContext true
            }
        } catch (_: Exception) {}
        false
    }
    suspend fun copyFolderTo(folder: VideoFolder, targetParentDir: File): Boolean = withContext(Dispatchers.IO) {
        try {
            val destFolder = File(targetParentDir, folder.name)
            if (!destFolder.exists()) destFolder.mkdirs()
            var allOk = true
            folder.videos.forEach { vid ->
                if (!copyVideoToFolder(vid, destFolder)) allOk = false
            }
            allOk
        } catch (_: Exception) {
            false
        }
    }

    suspend fun moveFolderTo(folder: VideoFolder, targetParentDir: File): Boolean = withContext(Dispatchers.IO) {
        try {
            val srcDir = File(folder.path)
            if (srcDir.parentFile?.absolutePath == targetParentDir.absolutePath) return@withContext false
            val destFolder = File(targetParentDir, folder.name)
            if (!destFolder.exists()) destFolder.mkdirs()
            var allOk = true
            folder.videos.forEach { vid ->
                if (!moveVideoToFolder(vid, destFolder)) allOk = false
            }
            if (srcDir.listFiles()?.isEmpty() == true) {
                srcDir.delete()
            }
            allOk
        } catch (_: Exception) {
            false
        }
    }

    suspend fun deleteFolder(folder: VideoFolder): Boolean = withContext(Dispatchers.IO) {
        try {
            var allOk = true
            folder.videos.forEach { vid ->
                if (!deleteVideo(vid)) allOk = false
            }
            val dir = File(folder.path)
            if (dir.exists() && (dir.listFiles()?.isEmpty() == true)) {
                dir.delete()
            }
            allOk
        } catch (_: Exception) {
            false
        }
    }
    suspend fun moveToPrivateVault(video: VideoItem): Boolean = withContext(Dispatchers.IO) {
        try {
            val srcFile = File(video.path)
            if (!srcFile.exists()) return@withContext false

            val safeName = "${System.currentTimeMillis()}_${srcFile.name}"
            val destFile = File(privateDir, safeName)

            FileInputStream(srcFile).use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output, bufferSize = 65536)
                }
            }

            if (destFile.exists() && destFile.length() == srcFile.length()) {
                val metaFile = File(privateDir, "$safeName.meta")
                metaFile.writeText("${video.path}\n${video.displayName}\n${video.durationMs}\n${video.width}\n${video.height}")

                srcFile.delete()
                try {
                    context.contentResolver.delete(video.uri, null, null)
                } catch (_: Exception) {}
                MediaScannerConnection.scanFile(context, arrayOf(video.path), null, null)
                return@withContext true
            }
        } catch (_: Exception) {}
        false
    }

    suspend fun loadPrivateVideos(): List<VideoItem> = withContext(Dispatchers.IO) {
        val list = mutableListOf<VideoItem>()
        val files = privateDir.listFiles() ?: return@withContext emptyList()
        files.filter { !it.name.endsWith(".meta") && it.isFile }.forEachIndexed { idx, file ->
            val metaFile = File(privateDir, "${file.name}.meta")
            val metaLines = if (metaFile.exists()) metaFile.readLines() else emptyList()
            val origName = metaLines.getOrNull(1) ?: file.name.substringAfter("_")
            val dur = metaLines.getOrNull(2)?.toLongOrNull() ?: 0L
            val w = metaLines.getOrNull(3)?.toIntOrNull() ?: 1920
            val h = metaLines.getOrNull(4)?.toIntOrNull() ?: 1080

            list.add(
                VideoItem(
                    id = -1000L - idx,
                    title = origName.substringBeforeLast('.'),
                    displayName = origName,
                    path = file.absolutePath,
                    uri = Uri.fromFile(file),
                    folderName = "Privacy Vault",
                    folderPath = privateDir.absolutePath,
                    durationMs = dur,
                    sizeBytes = file.length(),
                    width = w,
                    height = h,
                    dateAddedSec = file.lastModified() / 1000L,
                    mimeType = "video/mp4",
                    isWatched = false,
                    isPrivate = true
                )
            )
        }
        list.sortedByDescending { it.dateAddedSec }
    }
    suspend fun unhideFromPrivateVault(video: VideoItem): Boolean = withContext(Dispatchers.IO) {
        try {
            val vaultFile = File(video.path)
            if (!vaultFile.exists()) return@withContext false

            val metaFile = File(privateDir, "${vaultFile.name}.meta")
            val metaLines = if (metaFile.exists()) metaFile.readLines() else emptyList()
            val origPath = metaLines.getOrNull(0)

            val destFile = if (!origPath.isNullOrEmpty()) {
                val target = File(origPath)
                target.parentFile?.mkdirs()
                if (target.exists()) File(target.parentFile, "Restored_${target.name}") else target
            } else {
                val moviesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
                moviesDir.mkdirs()
                File(moviesDir, video.displayName)
            }

            FileInputStream(vaultFile).use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output, bufferSize = 65536)
                }
            }

            if (destFile.exists()) {
                vaultFile.delete()
                if (metaFile.exists()) metaFile.delete()
                MediaScannerConnection.scanFile(context, arrayOf(destFile.absolutePath), null, null)
                return@withContext true
            }
        } catch (_: Exception) {}
        false
    }
}
