package com.wswensw.woolbox.update

import com.wswensw.woolbox.BuildConfig
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateClientTest {
    @Test
    fun verifyDownloadedApkAcceptsMatchingFile() {
        val bytes = "woolbox-update".toByteArray()
        val manifest = manifestFor(bytes)

        assertTrue(AppUpdateClient.verifyDownloadedApk(manifest, temporaryApk(bytes)))
    }

    @Test
    fun verifyDownloadedApkRejectsWrongSizeOrChecksum() {
        val bytes = "woolbox-update".toByteArray()
        val manifest = manifestFor(bytes)

        assertFalse(AppUpdateClient.verifyDownloadedApk(manifest, temporaryApk(bytes.dropLast(1).toByteArray())))
        assertFalse(AppUpdateClient.verifyDownloadedApk(manifest, temporaryApk("other-apk".toByteArray())))
    }

    @Test
    fun validateManifestRejectsOtherPackageBuildTypeAndHost() {
        val manifest = manifestFor("woolbox-update".toByteArray())

        assertTrue(runCatching {
            AppUpdateClient.validateManifest(manifest.copy(packageName = "example.other"))
        }.isFailure)
        assertTrue(runCatching {
            AppUpdateClient.validateManifest(manifest.copy(buildType = "release"))
        }.isFailure)
        assertTrue(runCatching {
            AppUpdateClient.validateManifest(manifest.copy(apkUrl = "https://example.com/woolbox.apk"))
        }.isFailure)
    }

    private fun manifestFor(bytes: ByteArray) = AppUpdateManifest(
        packageName = BuildConfig.APPLICATION_ID,
        buildType = BuildConfig.BUILD_TYPE,
        versionCode = 3,
        versionName = "0.1.2",
        apkUrl = expectedApkUrl("0.1.2"),
        sha256 = sha256(bytes),
        sizeBytes = bytes.size.toLong(),
    )

    private fun expectedApkUrl(versionName: String): String {
        val suffix = if (BuildConfig.BUILD_TYPE == "debug") "-debug" else ""
        return "https://dl.1662803.xyz/d/woolbox/releases/${BuildConfig.BUILD_TYPE}/woolbox-$versionName$suffix.apk"
    }

    private fun temporaryApk(bytes: ByteArray): File =
        Files.createTempFile("woolbox-update", ".apk").toFile().apply {
            writeBytes(bytes)
            deleteOnExit()
        }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(it) }
}
