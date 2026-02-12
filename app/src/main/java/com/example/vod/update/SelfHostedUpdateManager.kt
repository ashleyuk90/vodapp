package com.example.vod.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.util.Xml
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import com.example.vod.BuildConfig
import com.example.vod.R
import com.example.vod.utils.Constants
import com.example.vod.utils.NetworkUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException

data class AndroidUpdateInfo(
    val channel: String?,
    val versionCode: Int,
    val versionName: String,
    val mandatory: Boolean,
    val minSupportedVersionCode: Int?,
    val apkFileName: String,
    val apkSha256: String,
    val apkSizeBytes: Long?,
    val publishedAt: String?,
    val changelogSummary: String?,
    val changelogItems: List<String>
) {
    fun isMandatoryFor(installedVersionCode: Int): Boolean {
        return mandatory || (
            minSupportedVersionCode != null &&
                installedVersionCode < minSupportedVersionCode
            )
    }
}

class SelfHostedUpdateManager(
    private val activity: AppCompatActivity
) {
    companion object {
        private const val TAG = "SelfHostedUpdateMgr"
    }

    private val prefs = activity.getSharedPreferences(
        Constants.PREFS_UPDATER_NAME,
        Context.MODE_PRIVATE
    )
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    @Volatile
    private var isCheckingForUpdates = false

    @Volatile
    private var isDownloadingUpdate = false

    fun checkForUpdatesIfNeeded(
        force: Boolean = false,
        showErrors: Boolean = false,
        showNoUpdateMessage: Boolean = false,
        showPromptIfAvailable: Boolean = true,
        onAvailabilityEvaluated: ((Boolean) -> Unit)? = null
    ) {
        if (isCheckingForUpdates) return
        if (!force && !shouldRunAutomaticCheck()) return
        if (BuildConfig.UPDATE_FEED_URL.isBlank()) {
            Log.w(TAG, "UPDATE_FEED_URL is blank. Skipping updater.")
            onAvailabilityEvaluated?.invoke(hasCachedAvailableUpdate())
            if (showErrors) {
                showShortToast(R.string.update_check_failed)
            }
            return
        }

        isCheckingForUpdates = true

        activity.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val updateInfo = fetchUpdateInfo()
                markCheckAttemptNow()
                if (updateInfo == null) {
                    val cachedAvailability = hasCachedAvailableUpdate()
                    withContext(Dispatchers.Main) {
                        onAvailabilityEvaluated?.invoke(cachedAvailability)
                    }
                    if (showErrors) {
                        withContext(Dispatchers.Main) {
                            showShortToast(R.string.update_check_failed)
                        }
                    }
                    return@launch
                }
                val installedVersionCode = getInstalledVersionCode()

                clearObsoleteSkip(installedVersionCode)

                if (updateInfo.versionCode <= installedVersionCode) {
                    setAvailableUpdateVersionCode(null)
                    if (showNoUpdateMessage) {
                        withContext(Dispatchers.Main) {
                            showShortToast(R.string.update_up_to_date)
                        }
                    }
                    withContext(Dispatchers.Main) {
                        onAvailabilityEvaluated?.invoke(false)
                    }
                    return@launch
                }

                val isMandatory = updateInfo.isMandatoryFor(installedVersionCode)
                val skippedVersion = prefs.getInt(Constants.KEY_SKIPPED_UPDATE_VERSION_CODE, -1)
                if (!isMandatory && skippedVersion == updateInfo.versionCode) {
                    setAvailableUpdateVersionCode(null)
                    Log.d(TAG, "Skipping prompt for version ${updateInfo.versionCode} due to user choice.")
                    if (showNoUpdateMessage) {
                        withContext(Dispatchers.Main) {
                            showShortToast(R.string.update_up_to_date)
                        }
                    }
                    withContext(Dispatchers.Main) {
                        onAvailabilityEvaluated?.invoke(false)
                    }
                    return@launch
                }

                setAvailableUpdateVersionCode(updateInfo.versionCode)
                withContext(Dispatchers.Main) {
                    onAvailabilityEvaluated?.invoke(true)
                    if (showPromptIfAvailable) {
                        showUpdatePrompt(
                            updateInfo = updateInfo,
                            installedVersionCode = installedVersionCode,
                            isMandatory = isMandatory,
                            onAvailabilityEvaluated = onAvailabilityEvaluated
                        )
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Update check failed", e)
                val cachedAvailability = hasCachedAvailableUpdate()
                withContext(Dispatchers.Main) {
                    onAvailabilityEvaluated?.invoke(cachedAvailability)
                }
                if (showErrors) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            activity,
                            activity.getString(R.string.update_check_failed),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } finally {
                isCheckingForUpdates = false
            }
        }
    }

    fun resumePendingInstallIfPermitted() {
        val pendingPath = prefs.getString(Constants.KEY_PENDING_UPDATE_APK_PATH, null)
        if (pendingPath.isNullOrBlank()) return

        val file = File(pendingPath)
        if (!file.exists()) {
            clearPendingInstall()
            return
        }

        if (!canRequestPackageInstalls()) {
            return
        }

        clearPendingInstall()
        launchInstaller(file)
    }

    fun hasCachedAvailableUpdate(): Boolean {
        val availableVersionCode = prefs.getInt(Constants.KEY_AVAILABLE_UPDATE_VERSION_CODE, -1)
        if (availableVersionCode <= 0) return false
        val installedVersionCode = runCatching { getInstalledVersionCode() }.getOrNull() ?: return true
        return if (availableVersionCode > installedVersionCode) {
            true
        } else {
            setAvailableUpdateVersionCode(null)
            false
        }
    }

    private fun shouldRunAutomaticCheck(): Boolean {
        val lastCheck = prefs.getLong(Constants.KEY_LAST_UPDATE_CHECK_MS, 0L)
        val intervalHours = BuildConfig.UPDATE_CHECK_INTERVAL_HOURS.coerceAtLeast(1)
        val intervalMs = intervalHours * 60L * 60L * 1000L
        val now = System.currentTimeMillis()
        return now - lastCheck >= intervalMs
    }

    private fun markCheckAttemptNow() {
        prefs.edit {
            putLong(Constants.KEY_LAST_UPDATE_CHECK_MS, System.currentTimeMillis())
        }
    }

    private fun clearObsoleteSkip(installedVersionCode: Int) {
        val skipped = prefs.getInt(Constants.KEY_SKIPPED_UPDATE_VERSION_CODE, -1)
        if (skipped != -1 && skipped <= installedVersionCode) {
            prefs.edit { remove(Constants.KEY_SKIPPED_UPDATE_VERSION_CODE) }
        }
    }

    private fun getInstalledVersionCode(): Int {
        val packageInfo = activity.packageManager.getPackageInfo(activity.packageName, 0)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode.toInt()
        } else {
            @Suppress("DEPRECATION")
            packageInfo.versionCode
        }
    }

    private fun fetchUpdateInfo(): AndroidUpdateInfo? {
        val request = Request.Builder()
            .url(BuildConfig.UPDATE_FEED_URL)
            .header("Cache-Control", "no-cache")
            .build()

        return httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Update feed request failed with HTTP ${response.code}")
            }
            val body = response.body ?: return null
            parseUpdateXml(body.byteStream())
        }
    }

    private fun parseUpdateXml(inputStream: InputStream): AndroidUpdateInfo? {
        inputStream.use { stream ->
            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(stream, "UTF-8")

            var channel: String? = null
            var versionCode: Int? = null
            var versionName: String? = null
            var mandatory: Boolean? = null
            var minSupportedVersionCode: Int? = null
            var apkFileName: String? = null
            var apkSha256: String? = null
            var apkSizeBytes: Long? = null
            var publishedAt: String? = null
            var changelogSummary: String? = null
            val changelogItems = mutableListOf<String>()

            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG) {
                    when (parser.name) {
                        "channel" -> channel = parser.nextText().trim().ifEmpty { null }
                        "versionCode" -> versionCode = parser.nextText().trim().toIntOrNull()
                        "versionName" -> versionName = parser.nextText().trim().ifEmpty { null }
                        "mandatory" -> mandatory = parser.nextText().trim().equals("true", ignoreCase = true)
                        "minSupportedVersionCode" ->
                            minSupportedVersionCode = parser.nextText().trim().toIntOrNull()
                        "apkFileName" -> apkFileName = parser.nextText().trim().ifEmpty { null }
                        "apkSha256" -> apkSha256 = parser.nextText().trim().ifEmpty { null }
                        "apkSizeBytes" -> apkSizeBytes = parser.nextText().trim().toLongOrNull()
                        "publishedAt" -> publishedAt = parser.nextText().trim().ifEmpty { null }
                        "summary" -> changelogSummary = parser.nextText().trim().ifEmpty { null }
                        "item" -> {
                            val item = parser.nextText().trim()
                            if (item.isNotEmpty()) {
                                changelogItems.add(item)
                            }
                        }
                    }
                }
                eventType = parser.next()
            }

            if (versionCode == null || versionCode <= 0) return null
            if (versionName.isNullOrBlank()) return null
            if (mandatory == null) return null
            if (apkFileName.isNullOrBlank()) return null
            if (apkSha256.isNullOrBlank()) return null

            val configuredChannel = BuildConfig.UPDATE_CHANNEL.trim()
            if (channel != null &&
                configuredChannel.isNotBlank() &&
                !channel.equals(configuredChannel, ignoreCase = true)
            ) {
                Log.w(
                    TAG,
                    "Feed channel mismatch. expected=$configuredChannel actual=$channel"
                )
                return null
            }

            return AndroidUpdateInfo(
                channel = channel,
                versionCode = versionCode,
                versionName = versionName,
                mandatory = mandatory,
                minSupportedVersionCode = minSupportedVersionCode,
                apkFileName = apkFileName,
                apkSha256 = apkSha256,
                apkSizeBytes = apkSizeBytes,
                publishedAt = publishedAt,
                changelogSummary = changelogSummary,
                changelogItems = changelogItems
            )
        }
    }

    private fun showUpdatePrompt(
        updateInfo: AndroidUpdateInfo,
        installedVersionCode: Int,
        isMandatory: Boolean,
        onAvailabilityEvaluated: ((Boolean) -> Unit)?
    ) {
        if (activity.isFinishing || activity.isDestroyed) return

        val installedVersionLabel = BuildConfig.VERSION_NAME.ifBlank {
            installedVersionCode.toString()
        }
        val message = buildUpdatePromptMessage(
            installedVersionLabel = installedVersionLabel,
            updateInfo = updateInfo,
            isMandatory = isMandatory
        )

        val dialogBuilder = AlertDialog.Builder(activity)
            .setTitle(
                if (isMandatory) {
                    R.string.update_required_title
                } else {
                    R.string.update_available_title
                }
            )
            .setMessage(message)
            .setPositiveButton(R.string.update_action_now) { _, _ ->
                prefs.edit {
                    remove(Constants.KEY_SKIPPED_UPDATE_VERSION_CODE)
                }
                downloadAndInstall(updateInfo)
            }
            .setCancelable(!isMandatory)

        if (!isMandatory) {
            dialogBuilder.setNegativeButton(R.string.update_action_later, null)
            dialogBuilder.setNeutralButton(R.string.update_action_skip) { _, _ ->
                prefs.edit {
                    putInt(Constants.KEY_SKIPPED_UPDATE_VERSION_CODE, updateInfo.versionCode)
                }
                setAvailableUpdateVersionCode(null)
                onAvailabilityEvaluated?.invoke(false)
            }
        }

        val dialog = dialogBuilder.create()
        dialog.setCanceledOnTouchOutside(!isMandatory)
        dialog.show()
    }

    private fun buildUpdatePromptMessage(
        installedVersionLabel: String,
        updateInfo: AndroidUpdateInfo,
        isMandatory: Boolean
    ): String {
        val lines = mutableListOf<String>()
        lines.add(activity.getString(R.string.update_version_current, installedVersionLabel))
        lines.add(activity.getString(R.string.update_version_latest, updateInfo.versionName))

        if (isMandatory) {
            lines.add("")
            lines.add(activity.getString(R.string.update_required_message))
        }

        val hasChangelog = !updateInfo.changelogSummary.isNullOrBlank() || updateInfo.changelogItems.isNotEmpty()
        if (hasChangelog) {
            lines.add("")
            lines.add(activity.getString(R.string.update_changelog_heading))
            updateInfo.changelogSummary?.takeIf { it.isNotBlank() }?.let { lines.add(it) }
            updateInfo.changelogItems.forEach { lines.add("• $it") }
        }

        return lines.joinToString(separator = "\n")
    }

    private fun downloadAndInstall(updateInfo: AndroidUpdateInfo) {
        if (isDownloadingUpdate) return
        if (!NetworkUtils.isNetworkAvailable(activity)) {
            Toast.makeText(activity, activity.getString(R.string.error_network), Toast.LENGTH_LONG).show()
            return
        }
        isDownloadingUpdate = true
        Toast.makeText(activity, activity.getString(R.string.update_download_starting), Toast.LENGTH_SHORT).show()

        activity.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val apkUrl = resolveApkUrl(updateInfo.apkFileName)
                val updateDir = resolveWritableUpdateDirectory(updateInfo.apkSizeBytes)

                val apkFile = File(updateDir, "vod-update-${updateInfo.versionCode}.apk")
                downloadApkAndVerify(
                    apkUrl = apkUrl,
                    targetFile = apkFile,
                    expectedSha256 = updateInfo.apkSha256,
                    expectedSizeBytes = updateInfo.apkSizeBytes
                )

                withContext(Dispatchers.Main) {
                    maybeInstallOrRequestPermission(apkFile, updateInfo.versionCode)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Update download failed", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        activity,
                        resolveDownloadErrorMessage(e),
                        Toast.LENGTH_LONG
                    ).show()
                }
            } finally {
                isDownloadingUpdate = false
            }
        }
    }

    private fun resolveApkUrl(apkFileName: String): String {
        val trimmedFileName = apkFileName.trim()
        if (trimmedFileName.startsWith("https://", ignoreCase = true) ||
            trimmedFileName.startsWith("http://", ignoreCase = true)
        ) {
            trimmedFileName.toHttpUrlOrNull()
                ?: throw UpdateConfigException("Invalid absolute APK URL: $trimmedFileName")
            return trimmedFileName
        }

        val base = BuildConfig.UPDATE_APK_BASE_URL.trim()
        if (base.isBlank()) {
            throw UpdateConfigException("UPDATE_APK_BASE_URL is blank")
        }
        val baseUrl = base.toHttpUrlOrNull()
            ?: throw UpdateConfigException("Invalid UPDATE_APK_BASE_URL: $base")
        val normalizedBase = if (baseUrl.toString().endsWith("/")) {
            baseUrl.toString()
        } else {
            "${baseUrl}/"
        }

        val encodedPath = trimmedFileName
            .removePrefix("/")
            .split("/")
            .filter { it.isNotBlank() }
            .joinToString("/") { Uri.encode(it) }
            .ifBlank { throw UpdateConfigException("APK file name is blank") }

        val resolved = "$normalizedBase$encodedPath"
        resolved.toHttpUrlOrNull()
            ?: throw UpdateConfigException("Resolved APK URL is invalid: $resolved")
        return resolved
    }

    private fun downloadApkAndVerify(
        apkUrl: String,
        targetFile: File,
        expectedSha256: String,
        expectedSizeBytes: Long?
    ) {
        val request = Request.Builder()
            .url(apkUrl)
            .header("Cache-Control", "no-cache")
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw UpdateHttpException(response.code)
            }
            val body = response.body ?: throw IOException("APK response body is empty")
            val normalizedExpectedSha = normalizeSha256(expectedSha256)
            if (normalizedExpectedSha.length != 64) {
                throw UpdateVerificationException(activity.getString(R.string.update_checksum_failed))
            }

            val digest = MessageDigest.getInstance("SHA-256")
            if (targetFile.exists()) {
                targetFile.delete()
            }
            FileOutputStream(targetFile).use { output ->
                body.byteStream().use { input ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val bytesRead = input.read(buffer)
                        if (bytesRead < 0) break
                        if (bytesRead == 0) continue
                        output.write(buffer, 0, bytesRead)
                        digest.update(buffer, 0, bytesRead)
                    }
                    output.flush()
                }
            }

            if (!targetFile.exists() || targetFile.length() <= 0L) {
                targetFile.delete()
                throw UpdateVerificationException(activity.getString(R.string.update_invalid_file))
            }

            if (expectedSizeBytes != null && expectedSizeBytes > 0L && targetFile.length() != expectedSizeBytes) {
                targetFile.delete()
                throw UpdateVerificationException(activity.getString(R.string.update_invalid_file))
            }

            val actualSha = digest.digest().toHexString()
            if (!actualSha.equals(normalizedExpectedSha, ignoreCase = true)) {
                targetFile.delete()
                throw UpdateVerificationException(activity.getString(R.string.update_checksum_failed))
            }
        }
    }

    private fun resolveWritableUpdateDirectory(expectedSizeBytes: Long?): File {
        val candidates = mutableListOf(
            File(activity.cacheDir, "updates"),
            File(activity.filesDir, "updates")
        )
        activity.externalCacheDir?.let { candidates.add(File(it, "updates")) }

        val requiredBytes = expectedSizeBytes
            ?.takeIf { it > 0L }
            ?.plus(2L * 1024L * 1024L)

        for (directory in candidates) {
            try {
                if (!directory.exists() && !directory.mkdirs()) {
                    continue
                }
                if (!directory.isDirectory || !directory.canWrite()) {
                    continue
                }
                if (requiredBytes != null && directory.usableSpace < requiredBytes) {
                    continue
                }
                return directory
            } catch (_: Exception) {
                // Try next directory.
            }
        }

        throw UpdateStorageException("No writable update directory available")
    }

    private fun normalizeSha256(value: String): String {
        return value
            .trim()
            .lowercase()
            .filter { it in '0'..'9' || it in 'a'..'f' }
    }

    private fun resolveDownloadErrorMessage(error: Throwable): String {
        val root = rootCause(error)
        return when (root) {
            is UpdateHttpException -> activity.getString(
                R.string.update_download_failed_http,
                root.statusCode
            )
            is UpdateConfigException -> activity.getString(R.string.update_download_failed_invalid_url)
            is UpdateStorageException -> activity.getString(R.string.update_download_failed_storage)
            is UpdateVerificationException ->
                root.message ?: activity.getString(R.string.update_checksum_failed)
            is UnknownHostException -> activity.getString(R.string.update_download_failed_network)
            is SocketTimeoutException -> activity.getString(R.string.update_download_failed_timeout)
            is SSLException -> activity.getString(R.string.update_download_failed_ssl)
            is IOException -> {
                if (root.message?.contains("cleartext", ignoreCase = true) == true) {
                    activity.getString(R.string.update_download_failed_ssl)
                } else {
                    activity.getString(R.string.update_download_failed)
                }
            }
            else -> activity.getString(R.string.update_download_failed)
        }
    }

    private fun rootCause(error: Throwable): Throwable {
        var current = error
        while (true) {
            val next = current.cause ?: break
            if (next === current) break
            current = next
        }
        return current
    }

    private fun maybeInstallOrRequestPermission(apkFile: File, versionCode: Int) {
        if (!canRequestPackageInstalls()) {
            Log.w(
                TAG,
                "Install permission not granted. Deferring install for versionCode=$versionCode, file=${apkFile.absolutePath}"
            )
            prefs.edit {
                putString(Constants.KEY_PENDING_UPDATE_APK_PATH, apkFile.absolutePath)
                putInt(Constants.KEY_PENDING_UPDATE_VERSION_CODE, versionCode)
            }
            Toast.makeText(
                activity,
                activity.getString(R.string.update_install_permission_required),
                Toast.LENGTH_LONG
            ).show()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    "package:${activity.packageName}".toUri()
                )
                try {
                    activity.startActivity(intent)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to open unknown-app-sources settings", e)
                }
            }
            return
        }

        clearPendingInstall()
        launchInstaller(apkFile)
    }

    private fun canRequestPackageInstalls(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            activity.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    private fun launchInstaller(apkFile: File) {
        if (!apkFile.exists()) {
            Toast.makeText(
                activity,
                activity.getString(R.string.update_invalid_file),
                Toast.LENGTH_LONG
            ).show()
            return
        }

        Log.d(TAG, "Launching installer for APK file: ${apkFile.absolutePath}")
        val apkUri = runCatching {
            FileProvider.getUriForFile(
                activity,
                "${activity.packageName}.fileprovider",
                apkFile
            )
        }.getOrElse { error ->
            Log.e(TAG, "Failed to create install URI for APK: ${apkFile.absolutePath}", error)
            Toast.makeText(
                activity,
                activity.getString(R.string.update_invalid_file),
                Toast.LENGTH_LONG
            ).show()
            return
        }
        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        val resolveInfos = activity.packageManager.queryIntentActivities(
            installIntent,
            PackageManager.MATCH_DEFAULT_ONLY
        )
        resolveInfos.forEach { resolveInfo ->
            activity.grantUriPermission(
                resolveInfo.activityInfo.packageName,
                apkUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }

        try {
            Toast.makeText(
                activity,
                activity.getString(R.string.update_install_opening),
                Toast.LENGTH_SHORT
            ).show()
            activity.startActivity(installIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch installer", e)
            Toast.makeText(
                activity,
                activity.getString(R.string.update_invalid_file),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun clearPendingInstall() {
        prefs.edit {
            remove(Constants.KEY_PENDING_UPDATE_APK_PATH)
            remove(Constants.KEY_PENDING_UPDATE_VERSION_CODE)
        }
    }

    private fun setAvailableUpdateVersionCode(versionCode: Int?) {
        prefs.edit {
            if (versionCode == null || versionCode <= 0) {
                remove(Constants.KEY_AVAILABLE_UPDATE_VERSION_CODE)
            } else {
                putInt(Constants.KEY_AVAILABLE_UPDATE_VERSION_CODE, versionCode)
            }
        }
    }

    private fun showShortToast(resId: Int) {
        Toast.makeText(activity, activity.getString(resId), Toast.LENGTH_SHORT).show()
    }
}

private class UpdateHttpException(val statusCode: Int) :
    IOException("HTTP $statusCode while downloading update")

private class UpdateStorageException(message: String) : IOException(message)

private class UpdateVerificationException(message: String) : IOException(message)

private class UpdateConfigException(message: String) : IOException(message)

private fun ByteArray.toHexString(): String {
    val sb = StringBuilder(size * 2)
    for (byte in this) {
        sb.append(String.format("%02x", byte))
    }
    return sb.toString()
}
