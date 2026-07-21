package com.msda.android

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

data class AppReleaseInfo(
    val versionName: String,
    val tagName: String,
    val apkDownloadUrl: String,
    val releaseNotes: String,
    val htmlUrl: String
)

/**
 * Checks GitHub Releases for a newer MSDA APK and installs it when the user confirms.
 * Used on hub launch; Settings shows an “Update available” hint in the version footer.
 */
object UpdateChecker {
    private const val LATEST_RELEASE_URL =
        "https://api.github.com/repos/bohdanbtw/MSDA/releases/latest"
    private const val USER_AGENT = "MSDA-Android-Updater"

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    @Volatile
    private var checkedThisProcess = false

    fun currentVersionName(activity: Activity): String {
        return try {
            val pm = activity.packageManager
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(activity.packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(activity.packageName, 0)
            }
            info.versionName?.trim().orEmpty().ifBlank { "0" }
        } catch (_: Throwable) {
            "0"
        }
    }

    fun isNewer(remoteVersion: String, localVersion: String): Boolean {
        return compareVersions(normalizeVersion(remoteVersion), normalizeVersion(localVersion)) > 0
    }

    suspend fun fetchLatestRelease(): AppReleaseInfo? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(LATEST_RELEASE_URL)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", USER_AGENT)
            .get()
            .build()
        val response = http.newCall(request).execute()
        val body = response.body?.string().orEmpty()
        if (!response.isSuccessful) {
            throw IllegalStateException("GitHub HTTP ${response.code}")
        }
        parseLatestRelease(body)
    }

    fun parseLatestRelease(jsonBody: String): AppReleaseInfo? {
        val json = JSONObject(jsonBody)
        if (json.optBoolean("draft", false) || json.optBoolean("prerelease", false)) {
            return null
        }
        val tag = json.optString("tag_name", "").trim()
        val version = normalizeVersion(tag)
        if (version.isBlank()) return null

        val assets = json.optJSONArray("assets") ?: return null
        var apkUrl = ""
        for (i in 0 until assets.length()) {
            val asset = assets.optJSONObject(i) ?: continue
            val name = asset.optString("name", "")
            val url = asset.optString("browser_download_url", "")
            if (name.endsWith(".apk", ignoreCase = true) && url.isNotBlank()) {
                apkUrl = url
                if (name.startsWith("MSDA-", ignoreCase = true)) break
            }
        }
        if (apkUrl.isBlank()) return null

        return AppReleaseInfo(
            versionName = version,
            tagName = tag,
            apkDownloadUrl = apkUrl,
            releaseNotes = json.optString("body", "").trim(),
            htmlUrl = json.optString("html_url", "https://github.com/bohdanbtw/MSDA/releases")
        )
    }

    suspend fun downloadApk(activity: Activity, url: String, versionName: String): File =
        withContext(Dispatchers.IO) {
            val dir = File(activity.cacheDir, "updates").apply { mkdirs() }
            val target = File(dir, "MSDA-$versionName.apk")
            if (target.exists()) target.delete()

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .get()
                .build()
            val response = http.newCall(request).execute()
            if (!response.isSuccessful) {
                throw IllegalStateException("Download HTTP ${response.code}")
            }
            response.body?.byteStream()?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            } ?: throw IllegalStateException("Empty download body")
            target
        }

    fun canRequestPackageInstalls(activity: Activity): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            activity.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    fun openInstallPermissionSettings(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${activity.packageName}")
            )
            activity.startActivity(intent)
        }
    }

    fun installApk(activity: Activity, apkFile: File) {
        val uri = FileProvider.getUriForFile(
            activity,
            "${activity.packageName}.fileprovider",
            apkFile
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        activity.startActivity(intent)
    }

    /** Silent check on hub launch — at most once per process. */
    fun checkOnLaunch(activity: Activity) {
        if (checkedThisProcess) return
        checkedThisProcess = true
        runCheck(activity, notifyWhenUpToDate = false)
    }

    fun checkManually(activity: Activity) {
        runCheck(activity, notifyWhenUpToDate = true)
    }

    private fun runCheck(activity: Activity, notifyWhenUpToDate: Boolean) {
        CoroutineScope(Dispatchers.Main).launch {
            if (notifyWhenUpToDate) {
                Toast.makeText(activity, R.string.update_checking, Toast.LENGTH_SHORT).show()
            }
            val local = currentVersionName(activity)
            val latest = try {
                fetchLatestRelease()
            } catch (_: Throwable) {
                if (notifyWhenUpToDate && !activity.isFinishing) {
                    Toast.makeText(activity, R.string.update_check_failed, Toast.LENGTH_SHORT).show()
                }
                return@launch
            }

            if (activity.isFinishing) return@launch

            if (latest == null || !isNewer(latest.versionName, local)) {
                if (notifyWhenUpToDate) {
                    Toast.makeText(
                        activity,
                        activity.getString(R.string.update_up_to_date, local),
                        Toast.LENGTH_SHORT
                    ).show()
                }
                return@launch
            }

            showUpdateDialog(activity, local, latest)
        }
    }

    private fun showUpdateDialog(activity: Activity, localVersion: String, release: AppReleaseInfo) {
        val preview = release.releaseNotes
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .take(8)
            .joinToString("\n")
            .ifBlank { activity.getString(R.string.update_no_notes) }

        AlertDialog.Builder(activity)
            .setTitle(activity.getString(R.string.update_available_title, release.versionName))
            .setMessage(
                activity.getString(
                    R.string.update_available_message,
                    localVersion,
                    release.versionName,
                    preview
                )
            )
            .setNegativeButton(R.string.update_later, null)
            .setNeutralButton(R.string.update_open_github) { _, _ ->
                activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(release.htmlUrl)))
            }
            .setPositiveButton(R.string.update_install) { _, _ ->
                startInstallFlow(activity, release)
            }
            .show()
    }

    private fun startInstallFlow(activity: Activity, release: AppReleaseInfo) {
        if (!canRequestPackageInstalls(activity)) {
            AlertDialog.Builder(activity)
                .setTitle(R.string.update_permission_title)
                .setMessage(R.string.update_permission_message)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.update_permission_open) { _, _ ->
                    openInstallPermissionSettings(activity)
                    Toast.makeText(activity, R.string.update_permission_retry_hint, Toast.LENGTH_LONG).show()
                }
                .show()
            return
        }

        Toast.makeText(activity, R.string.update_downloading, Toast.LENGTH_SHORT).show()
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val file = downloadApk(activity, release.apkDownloadUrl, release.versionName)
                if (!activity.isFinishing) {
                    installApk(activity, file)
                }
            } catch (_: Throwable) {
                if (!activity.isFinishing) {
                    Toast.makeText(activity, R.string.update_download_failed, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    fun normalizeVersion(raw: String): String {
        return raw.trim().removePrefix("v").removePrefix("V").trim()
    }

    /** Returns negative if a < b, 0 if equal, positive if a > b. */
    fun compareVersions(a: String, b: String): Int {
        val pa = a.split('.', '-', '_').mapNotNull { part ->
            part.filter(Char::isDigit).takeIf { it.isNotEmpty() }?.toIntOrNull()
        }
        val pb = b.split('.', '-', '_').mapNotNull { part ->
            part.filter(Char::isDigit).takeIf { it.isNotEmpty() }?.toIntOrNull()
        }
        val size = maxOf(pa.size, pb.size)
        for (i in 0 until size) {
            val left = pa.getOrElse(i) { 0 }
            val right = pb.getOrElse(i) { 0 }
            if (left != right) return left.compareTo(right)
        }
        return 0
    }
}
