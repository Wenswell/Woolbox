package com.wswensw.woolbox.update

import com.wswensw.woolbox.BuildConfig
import java.io.File
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URI
import java.security.MessageDigest
import org.json.JSONObject

internal object AppUpdateClient {
    private const val ConnectTimeoutMillis = 15_000
    private const val ReadTimeoutMillis = 30_000
    private const val ProgressIntervalNanos = 250_000_000L
    private const val DownloadHost = "dl.1662803.xyz"
    private const val MaximumApkBytes = 500L * 1024 * 1024
    private val Sha256Pattern = Regex("^[A-Fa-f0-9]{64}$")

    fun fetchManifest(manifestUrl: String): AppUpdateManifest {
        require(manifestUrl.isTrustedUpdateUrl(expectedManifestPath())) {
            "Update manifest URL is not trusted."
        }
        val manifest = parseManifest(getText(manifestUrl))
        validateManifest(manifest)
        return manifest
    }

    fun downloadApk(
        manifest: AppUpdateManifest,
        outputFile: File,
        onProgress: (AppUpdateDownloadProgress) -> Unit = {},
    ) {
        validateManifest(manifest)
        outputFile.parentFile?.mkdirs()
        outputFile.delete()
        val temporaryFile = File(outputFile.parentFile, "${outputFile.name}.part")
        temporaryFile.delete()

        val digest = MessageDigest.getInstance("SHA-256")
        var bytesWritten = 0L
        val startedAt = System.nanoTime()
        var lastProgressAt = 0L

        fun emitProgress(force: Boolean = false) {
            val now = System.nanoTime()
            if (!force && now - lastProgressAt < ProgressIntervalNanos) return
            lastProgressAt = now
            val elapsedNanos = (now - startedAt).coerceAtLeast(1L)
            onProgress(
                AppUpdateDownloadProgress(
                    bytesDownloaded = bytesWritten,
                    totalBytes = manifest.sizeBytes,
                    bytesPerSecond = (bytesWritten * 1_000_000_000.0 / elapsedNanos).toLong(),
                ),
            )
        }

        try {
            val connection = openConnection(manifest.apkUrl)
            try {
                val responseCode = connection.responseCode
                if (responseCode !in 200..299) error("APK download failed: HTTP $responseCode")
                val contentLength = connection.contentLengthLong
                if (contentLength > 0L && contentLength != manifest.sizeBytes) {
                    error("APK size mismatch before download.")
                }
                emitProgress(force = true)
                temporaryFile.outputStream().use { output ->
                    connection.inputStream.use { input ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            bytesWritten += read
                            if (bytesWritten > manifest.sizeBytes) error("APK exceeds manifest size.")
                            output.write(buffer, 0, read)
                            digest.update(buffer, 0, read)
                            emitProgress()
                        }
                    }
                }
                emitProgress(force = true)
            } finally {
                connection.disconnect()
            }
            if (bytesWritten != manifest.sizeBytes) error("APK size mismatch after download.")
            val actualSha256 = digest.digest().toHexString()
            if (actualSha256 != manifest.sha256) error("APK checksum mismatch.")
            check(temporaryFile.renameTo(outputFile)) { "Unable to save verified APK." }
        } catch (timeout: SocketTimeoutException) {
            error("Update download timed out.")
        } catch (error: Throwable) {
            outputFile.delete()
            throw error
        } finally {
            temporaryFile.delete()
        }
    }

    fun verifyDownloadedApk(manifest: AppUpdateManifest, apkFile: File): Boolean {
        if (!apkFile.isFile || apkFile.length() != manifest.sizeBytes) return false
        val sha256 = runCatching {
            apkFile.inputStream().use { input ->
                MessageDigest.getInstance("SHA-256").digestStream(input)
            }
        }
            .getOrNull()
            ?: return false
        return sha256 == manifest.sha256
    }

    internal fun validateManifest(manifest: AppUpdateManifest) {
        require(manifest.packageName == BuildConfig.APPLICATION_ID) { "Update package mismatch." }
        require(manifest.buildType == BuildConfig.BUILD_TYPE) { "Update build type mismatch." }
        require(manifest.versionCode > 0) { "Update version code is invalid." }
        require(manifest.versionName.matches(VersionNamePattern)) { "Update version name is invalid." }
        require(manifest.sizeBytes in 1..MaximumApkBytes) { "Update APK size is invalid." }
        require(manifest.sha256.matches(Sha256Pattern)) { "Update SHA-256 is invalid." }
        require(manifest.apkUrl.isTrustedUpdateUrl(expectedApkPath(manifest.versionName))) {
            "Update APK URL is not trusted."
        }
    }

    internal fun parseManifest(body: String): AppUpdateManifest {
        val json = JSONObject(body)
        return AppUpdateManifest(
            packageName = json.getString("packageName").trim(),
            buildType = json.getString("buildType").trim(),
            versionCode = json.getLong("versionCode"),
            versionName = json.getString("versionName").trim(),
            apkUrl = json.getString("apkUrl").trim(),
            sha256 = json.getString("sha256").trim().lowercase(),
            sizeBytes = json.getLong("sizeBytes"),
        )
    }

    private fun getText(url: String): String = try {
        val connection = openConnection(url)
        try {
            val responseCode = connection.responseCode
            if (responseCode !in 200..299) error("Update check failed: HTTP $responseCode")
            connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            connection.disconnect()
        }
    } catch (timeout: SocketTimeoutException) {
        error("Update check timed out.")
    }

    private fun openConnection(url: String): HttpURLConnection =
        (URI.create(url).toURL().openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            instanceFollowRedirects = false
            useCaches = false
            connectTimeout = ConnectTimeoutMillis
            readTimeout = ReadTimeoutMillis
            setRequestProperty("Accept", "application/json, application/vnd.android.package-archive")
        }

    private fun expectedManifestPath() = "/d/woolbox/releases/${BuildConfig.BUILD_TYPE}/latest.json"

    private fun expectedApkPath(versionName: String): String {
        val debugSuffix = if (BuildConfig.BUILD_TYPE == "debug") "-debug" else ""
        return "/d/woolbox/releases/${BuildConfig.BUILD_TYPE}/woolbox-$versionName$debugSuffix.apk"
    }

    private fun String.isTrustedUpdateUrl(expectedPath: String): Boolean {
        val uri = runCatching(::URI).getOrNull() ?: return false
        return uri.scheme.equals("https", ignoreCase = true) &&
            uri.host.equals(DownloadHost, ignoreCase = true) &&
            uri.port == -1 &&
            uri.userInfo == null &&
            uri.rawQuery == null &&
            uri.rawFragment == null &&
            uri.rawPath == expectedPath
    }

    private fun MessageDigest.digestStream(input: java.io.InputStream): String {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            update(buffer, 0, read)
        }
        return digest().toHexString()
    }

    private fun ByteArray.toHexString(): String = joinToString("") { "%02x".format(it) }

    private val VersionNamePattern = Regex("^\\d+\\.\\d+\\.\\d+$")
}
