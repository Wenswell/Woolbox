package com.wswensw.woolbox.update

data class AppUpdateManifest(
    val packageName: String,
    val buildType: String,
    val versionCode: Long,
    val versionName: String,
    val apkUrl: String,
    val sha256: String,
    val sizeBytes: Long,
)

data class AppUpdateDownloadProgress(
    val bytesDownloaded: Long,
    val totalBytes: Long,
    val bytesPerSecond: Long,
)
