package com.msda.android

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageInfo
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
import okhttp3.Response
import org.json.JSONObject
import java.io.File
import java.net.URI
import java.security.MessageDigest
import java.util.Locale
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
    private const val DEFAULT_RELEASE_PAGE = "https://github.com/bohdanbtw/MSDA/releases"

    /** Hard ceiling for a downloaded APK, to bound cache usage. */
    private const val MAX_APK_SIZE_BYTES = 40L * 1024 * 1024
    private const val MAX_DOWNLOAD_REDIRECTS = 5

    /** Only these HTTPS hosts may supply APK bytes. */
    private val TRUSTED_DOWNLOAD_HOSTS = setOf(
        "github.com",
        "objects.githubusercontent.com",
        "release-assets.githubusercontent.com"
    )

    private val ALLOWED_APK_CONTENT_TYPES = setOf(
        "application/vnd.android.package-archive",
        // GitHub's release-asset CDN serves APKs with this generic content type.
        "application/octet-stream"
    )

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * Redirects are followed manually so every hop is validated before it is used.
     * A redirect to an untrusted host must never turn into a download target.
     */
    private val downloadHttp = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
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
        http.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IllegalStateException("GitHub HTTP ${response.code}")
            }
            parseLatestRelease(body)
        }
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
            if (name.endsWith(".apk", ignoreCase = true) &&
                url.isNotBlank() &&
                isTrustedDownloadUrl(url)
            ) {
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
            htmlUrl = json.optString("html_url", DEFAULT_RELEASE_PAGE)
        )
    }

    /** HTTPS plus an allow-listed GitHub host; used for assets and for every redirect hop. */
    fun isTrustedDownloadUrl(url: String): Boolean {
        return try {
            val uri = URI(url)
            val scheme = uri.scheme?.lowercase(Locale.ROOT)
            val host = uri.host?.lowercase(Locale.ROOT) ?: return false
            scheme == "https" &&
                (host in TRUSTED_DOWNLOAD_HOSTS || host.endsWith(".githubusercontent.com"))
        } catch (_: Throwable) {
            false
        }
    }

    private fun isTrustedReleasePage(url: String): Boolean {
        return try {
            val uri = URI(url)
            uri.scheme?.lowercase(Locale.ROOT) == "https" &&
                uri.host?.lowercase(Locale.ROOT) == "github.com"
        } catch (_: Throwable) {
            false
        }
    }

    /** Execute a GET, following only HTTPS redirects to trusted hosts. Caller closes the result. */
    private fun openTrustedResponse(initialUrl: String): Response {
        var url = initialUrl
        var redirects = 0
        while (true) {
            if (!isTrustedDownloadUrl(url)) {
                throw SecurityException("Untrusted update download URL: $url")
            }
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .get()
                .build()
            val response = downloadHttp.newCall(request).execute()
            if (response.code in 300..399) {
                val location = response.header("Location")
                response.close()
                if (location.isNullOrBlank()) {
                    throw IllegalStateException("Update redirect without a location")
                }
                if (++redirects > MAX_DOWNLOAD_REDIRECTS) {
                    throw IllegalStateException("Too many update redirects")
                }
                url = URI(url).resolve(location).toString()
                continue
            }
            return response
        }
    }

    suspend fun downloadApk(activity: Activity, url: String, versionName: String): File =
        withContext(Dispatchers.IO) {
            val dir = File(activity.cacheDir, "updates").apply { mkdirs() }
            val target = File(dir, "MSDA-$versionName.apk")
            if (target.exists()) target.delete()

            var response: Response? = null
            try {
                response = openTrustedResponse(url)
                val contentType = response.header("Content-Type")
                    ?.substringBefore(';')
                    ?.trim()
                    ?.lowercase(Locale.ROOT)
                if (contentType == null || contentType !in ALLOWED_APK_CONTENT_TYPES) {
                    throw SecurityException("Unexpected update content type: $contentType")
                }
                val body = response.body ?: throw IllegalStateException("Empty download body")
                val declaredLength = body.contentLength()
                if (declaredLength > MAX_APK_SIZE_BYTES) {
                    throw SecurityException("Update APK is too large: $declaredLength bytes")
                }

                body.byteStream().use { input ->
                    target.outputStream().use { output ->
                        val buffer = ByteArray(16 * 1024)
                        var total = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            total += read
                            if (total > MAX_APK_SIZE_BYTES) {
                                throw SecurityException("Update APK exceeds the size limit")
                            }
                            output.write(buffer, 0, read)
                        }
                        if (total == 0L) {
                            throw IllegalStateException("Empty download body")
                        }
                    }
                }
                target
            } catch (t: Throwable) {
                // Never leave an unverified or partial file behind for a later install.
                if (target.exists()) target.delete()
                throw t
            } finally {
                response?.close()
            }
        }

    private fun signatureFlags(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            @Suppress("DEPRECATION")
            PackageManager.GET_SIGNATURES
        }
    }

    @Suppress("DEPRECATION")
    private fun certificateDigests(info: PackageInfo?): Set<String> {
        if (info == null) return emptySet()
        val signers = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val apkSigners = info.signingInfo?.apkContentsSigners
            if (!apkSigners.isNullOrEmpty()) {
                apkSigners.map { it.toByteArray() }
            } else {
                info.signatures?.map { it.toByteArray() }.orEmpty()
            }
        } else {
            info.signatures?.map { it.toByteArray() }.orEmpty()
        }
        if (signers.isEmpty()) return emptySet()

        val digest = MessageDigest.getInstance("SHA-256")
        return signers.map { bytes ->
            digest.digest(bytes).joinToString("") { "%02X".format(it.toInt() and 0xFF) }
        }.toSet()
    }

    private fun installedSignerDigests(activity: Activity): Set<String> {
        val pm = activity.packageManager
        val flags = signatureFlags()
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageInfo(activity.packageName, PackageManager.PackageInfoFlags.of(flags.toLong()))
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(activity.packageName, flags)
        }
        return certificateDigests(info)
    }

    private fun apkSignerDigests(activity: Activity, apkFile: File): Set<String> {
        val info = activity.packageManager
            .getPackageArchiveInfo(apkFile.absolutePath, signatureFlags())
        return certificateDigests(info)
    }

    /** True only when the platform can read our own signing certificate to compare against. */
    fun canVerifySignatures(activity: Activity): Boolean {
        return installedSignerDigests(activity).isNotEmpty()
    }

    /**
     * A self-update must be signed by the same certificate already installed on the device.
     * An optional BuildConfig pin is also enforced when configured for the build.
     */
    fun verifyApkSignature(activity: Activity, apkFile: File): Boolean {
        val installed = installedSignerDigests(activity)
        if (installed.isEmpty()) return false
        val downloaded = apkSignerDigests(activity, apkFile)
        if (downloaded.isEmpty()) return false
        val pin = normalizeFingerprint(BuildConfig.UPDATE_SIGNING_SHA256)
        if (pin.isNotBlank() && pin !in downloaded) return false
        return installed == downloaded
    }

    fun normalizeFingerprint(raw: String): String {
        return raw.trim().uppercase(Locale.ROOT)
            .replace(":", "")
            .replace(" ", "")
            .replace("\n", "")
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

    /** Only reachable from [startInstallFlow] after [verifyApkSignature] succeeds. */
    private fun installApk(activity: Activity, apkFile: File) {
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
                openReleasePage(activity, release)
            }
            .setPositiveButton(R.string.update_install) { _, _ ->
                startInstallFlow(activity, release)
            }
            .show()
    }

    private fun openReleasePage(activity: Activity, release: AppReleaseInfo) {
        val url = if (isTrustedReleasePage(release.htmlUrl)) release.htmlUrl else DEFAULT_RELEASE_PAGE
        activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    private fun startInstallFlow(activity: Activity, release: AppReleaseInfo) {
        // Without a readable trusted signing certificate there is no safe way to verify
        // a download. Fall back to the HTTPS release page instead of direct install.
        if (!canVerifySignatures(activity)) {
            openReleasePage(activity, release)
            Toast.makeText(activity, R.string.update_verify_unavailable, Toast.LENGTH_LONG).show()
            return
        }

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
            var downloaded: File? = null
            try {
                val file = downloadApk(activity, release.apkDownloadUrl, release.versionName)
                downloaded = file
                if (activity.isFinishing) {
                    file.delete()
                    return@launch
                }
                if (!verifyApkSignature(activity, file)) {
                    file.delete()
                    if (!activity.isFinishing) {
                        Toast.makeText(activity, R.string.update_signature_mismatch, Toast.LENGTH_LONG).show()
                        openReleasePage(activity, release)
                    }
                    return@launch
                }
                installApk(activity, file)
            } catch (_: Throwable) {
                downloaded?.delete()
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
