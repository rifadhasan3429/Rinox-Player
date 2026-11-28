package com.rinox.videoplayer.utils

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class GitHubReleaseInfo(
    val tagName: String,
    val versionName: String,
    val releaseNotes: String,
    val apkDownloadUrl: String?,
    val htmlPageUrl: String
)

object GitHubUpdateChecker {

    // Replace REPO_NAME if your repository slug is slightly different
    private const val GITHUB_USER = "rifadhasan3429"
    private const val REPO_NAME = "Rinox-Player"
    private const val LATEST_RELEASE_API = "https://github.com"

    fun getCurrentAppVersion(context: Context): String {
        return try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            pInfo.versionName ?: "1.0.0"
        } catch (_: Exception) {
            "1.0.0"
        }
    }

    suspend fun checkForUpdate(context: Context): GitHubReleaseInfo? = withContext(Dispatchers.IO) {
        try {
            val url = URL(LATEST_RELEASE_API)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("Accept", "application/vnd.github.v3+json")
                setRequestProperty("User-Agent", "RinoxPlayer-Android")
            }

            if (conn.responseCode != 200) {
                conn.disconnect()
                return@withContext null
            }

            val body = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()

            val json = JSONObject(body)
            val tagName = json.optString("tag_name", "").trim()
            val cleanRemoteVersion = tagName.removePrefix("v").removePrefix("V").trim()
            val releaseNotes = json.optString("body", "Bug fixes and performance improvements.")
            val htmlUrl = json.optString("html_url", "https://github.com")

            var apkUrl: String? = null
            val assets = json.optJSONArray("assets")
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val name = asset.optString("name", "")
                    if (name.endsWith(".apk", ignoreCase = true)) {
                        apkUrl = asset.optString("browser_download_url", "")
                        break
                    }
                }
            }

            val currentVersion = getCurrentAppVersion(context)
            if (cleanRemoteVersion.isNotEmpty() && isNewerVersion(cleanRemoteVersion, currentVersion)) {
                GitHubReleaseInfo(
                    tagName = tagName,
                    versionName = cleanRemoteVersion,
                    releaseNotes = releaseNotes,
                    apkDownloadUrl = apkUrl,
                    htmlPageUrl = htmlUrl
                )
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun isNewerVersion(remote: String, local: String): Boolean {
        return try {
            val rParts = remote.split(".").map { it.filter { ch -> ch.isDigit() }.toIntOrNull() ?: 0 }
            val lParts = local.split(".").map { it.filter { ch -> ch.isDigit() }.toIntOrNull() ?: 0 }
            val maxLen = maxOf(rParts.size, lParts.size)
            for (i in 0 until maxLen) {
                val r = rParts.getOrElse(i) { 0 }
                val l = lParts.getOrElse(i) { 0 }
                if (r > l) return true
                if (r < l) return false
            }
            false
        } catch (_: Exception) {
            remote != local
        }
    }

    fun downloadAndInstallApk(context: Context, release: GitHubReleaseInfo) {
        val downloadUrl = release.apkDownloadUrl
        if (downloadUrl.isNullOrEmpty()) {
            openReleasePage(context, release.htmlPageUrl)
            return
        }

        try {
            val fileName = "RinoxPlayer_v${release.versionName}.apk"
            val request = DownloadManager.Request(Uri.parse(downloadUrl)).apply {
                setTitle("Updating Rinox Player")
                setDescription("Downloading version ${release.versionName}...")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                setMimeType("application/vnd.android.package-archive")
            }
            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            dm.enqueue(request)
            Toast.makeText(context, "Downloading update in notification bar...", Toast.LENGTH_LONG).show()
        } catch (_: Exception) {
            openReleasePage(context, release.htmlPageUrl)
        }
    }

    fun openReleasePage(context: Context, url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(context, "Unable to open GitHub Releases", Toast.LENGTH_SHORT).show()
        }
    }
}
