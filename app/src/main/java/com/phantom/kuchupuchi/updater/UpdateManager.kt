package com.phantom.kuchupuchi.updater

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import com.phantom.kuchupuchi.BuildConfig
import com.phantom.kuchupuchi.config.FlavorConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

class UpdateManager(private val context: Context) {

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    suspend fun checkForUpdates() = withContext(Dispatchers.IO) {
        try {
            val githubOwner = try {
                val owner = BuildConfig.GITHUB_OWNER
                if (owner.isNotBlank()) owner else "phantom"
            } catch (e: Throwable) {
                "phantom"
            }

            val url = "https://api.github.com/repos/$githubOwner/kuchupuchi/releases/latest"
            val request = Request.Builder()
                .url(url)
                .addHeader("Accept", "application/vnd.github.v3+json")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext
                val responseBody = response.body?.string() ?: return@withContext
                val json = JSONObject(responseBody)
                val tagName = json.optString("tag_name", "")
                val latestVersionCode = parseVersionCode(tagName)

                if (latestVersionCode > BuildConfig.VERSION_CODE) {
                    val assetsArray = json.optJSONArray("assets") ?: return@withContext
                    val currentFlavor = FlavorConfig.flavorName
                    val expectedFileName = "${currentFlavor}-release.apk"
                    var downloadUrl: String? = null

                    for (i in 0 until assetsArray.length()) {
                        val assetObj = assetsArray.optJSONObject(i) ?: continue
                        val assetName = assetObj.optString("name", "")
                        if (assetName.equals(expectedFileName, ignoreCase = true) ||
                            (assetName.contains(currentFlavor, ignoreCase = true) && assetName.endsWith(".apk", ignoreCase = true))
                        ) {
                            downloadUrl = assetObj.optString("browser_download_url", null)
                            if (!downloadUrl.isNullOrEmpty()) break
                        }
                    }

                    if (!downloadUrl.isNullOrEmpty()) {
                        val apkFile = File(context.cacheDir, "update.apk")
                        if (downloadApk(downloadUrl, apkFile)) {
                            installApk(apkFile)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    internal fun parseVersionCode(tagName: String): Int {
        val digits = tagName.filter { it.isDigit() }
        return digits.toIntOrNull() ?: 0
    }

    private fun downloadApk(url: String, outputFile: File): Boolean {
        return try {
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful && response.body != null) {
                    response.body!!.byteStream().use { inputStream ->
                        outputFile.outputStream().use { outputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    }
                    true
                } else {
                    false
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun installApk(apkFile: File) {
        if (!apkFile.exists() || apkFile.length() <= 0) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val installed = trySilentInstall(apkFile)
            if (!installed) {
                installLegacy(apkFile)
            }
        } else {
            installLegacy(apkFile)
        }
    }

    private fun trySilentInstall(apkFile: File): Boolean {
        return try {
            val packageInstaller = context.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
                }
            }
            val sessionId = packageInstaller.createSession(params)
            val session = packageInstaller.openSession(sessionId)

            val name: String = "package.apk"
            session.openWrite(name, 0, apkFile.length()).use { outputStream ->
                apkFile.inputStream().use { inputStream ->
                    inputStream.copyTo(outputStream)
                }
                session.fsync(outputStream)
            }

            val intent = Intent(context, UpdateInstallReceiver::class.java).apply {
                action = ACTION_INSTALL_COMPLETE
            }
            val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                sessionId,
                intent,
                pendingIntentFlags
            )

            session.commit(pendingIntent.intentSender)
            session.close()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun installLegacy(apkFile: File) {
        try {
            val contentUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    companion object {
        const val ACTION_INSTALL_COMPLETE = "com.phantom.kuchupuchi.ACTION_INSTALL_COMPLETE"
    }
}
