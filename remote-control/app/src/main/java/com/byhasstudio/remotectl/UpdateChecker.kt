package com.byhasstudio.remotectl

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Mengecek rilis terbaru di GitHub Releases (repo publik, tidak perlu
 * token) dan, kalau ada versi lebih baru, mengunduh APK-nya lalu
 * membuka dialog pemasangan Android. Android tidak pernah mengizinkan
 * aplikasi sideload meng-install dirinya sendiri tanpa persetujuan
 * pengguna sama sekali -- ini batasan sistem, bukan batasan kode --
 * jadi langkah terakhir (ketuk "Install") tetap perlu sekali sentuhan.
 */
object UpdateChecker {

    private const val RELEASES_URL =
        "https://api.github.com/repos/byhasstudio/apk-maker/releases"
    private const val TAG_PREFIX = "android-v"

    data class UpdateInfo(val version: String, val changelog: String, val apkUrl: String)

    suspend fun checkForUpdate(currentVersion: String): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val conn = URL(RELEASES_URL).openConnection() as HttpURLConnection
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val releases = JSONArray(body)

            for (i in 0 until releases.length()) {
                val release = releases.getJSONObject(i)
                val tag = release.optString("tag_name")
                if (!tag.startsWith(TAG_PREFIX)) continue

                val remoteVersion = tag.removePrefix(TAG_PREFIX)
                if (!isNewer(remoteVersion, currentVersion)) return@withContext null

                val assets = release.optJSONArray("assets") ?: return@withContext null
                var apkUrl: String? = null
                for (j in 0 until assets.length()) {
                    val asset = assets.getJSONObject(j)
                    if (asset.optString("name").endsWith(".apk")) {
                        apkUrl = asset.optString("browser_download_url")
                        break
                    }
                }
                if (apkUrl == null) return@withContext null

                return@withContext UpdateInfo(
                    version = remoteVersion,
                    changelog = release.optString("body").ifBlank { "Pembaruan tersedia." },
                    apkUrl = apkUrl
                )
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    private fun isNewer(remote: String, current: String): Boolean {
        fun parts(v: String) = v.split(".").map { it.toIntOrNull() ?: 0 }
        val r = parts(remote)
        val c = parts(current)
        for (i in 0 until maxOf(r.size, c.size)) {
            val rv = r.getOrElse(i) { 0 }
            val cv = c.getOrElse(i) { 0 }
            if (rv != cv) return rv > cv
        }
        return false
    }

    fun downloadAndInstall(context: Context, info: UpdateInfo) {
        val destFile = File(
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
            "remotectl-update.apk"
        )
        if (destFile.exists()) destFile.delete()

        val request = DownloadManager.Request(Uri.parse(info.apkUrl))
            .setTitle("Memperbarui Remote Ctl")
            .setDestinationUri(Uri.fromFile(destFile))
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)

        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val downloadId = downloadManager.enqueue(request)

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (id != downloadId) return
                context.unregisterReceiver(this)

                val apkUri = FileProvider.getUriForFile(
                    context, "${context.packageName}.fileprovider", destFile
                )
                val installIntent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(apkUri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(installIntent)
            }
        }
        context.registerReceiver(
            receiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            Context.RECEIVER_EXPORTED
        )
    }
}
