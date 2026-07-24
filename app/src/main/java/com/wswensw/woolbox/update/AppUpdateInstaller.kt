package com.wswensw.woolbox.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import java.io.File

internal object AppUpdateInstaller {
    const val ActionInstallStatus = "com.wswensw.woolbox.action.INSTALL_UPDATE_STATUS"
    const val ExtraVersionName = "version_name"

    fun install(context: Context, apkFile: File, manifest: AppUpdateManifest) {
        val packageInstaller = context.packageManager.packageInstaller
        val sessionParams = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED)
        }
        val sessionId = packageInstaller.createSession(sessionParams)
        try {
            packageInstaller.openSession(sessionId).use { session ->
                apkFile.inputStream().use { input ->
                    session.openWrite("woolbox-${manifest.versionCode}.apk", 0, apkFile.length()).use { output ->
                        input.copyTo(output)
                        session.fsync(output)
                    }
                }
                val statusIntent = Intent(context, AppUpdateInstallReceiver::class.java).apply {
                    action = ActionInstallStatus
                    putExtra(ExtraVersionName, manifest.versionName)
                }
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    sessionId,
                    statusIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
                )
                session.commit(pendingIntent.intentSender)
            }
        } catch (error: Throwable) {
            packageInstaller.abandonSession(sessionId)
            throw error
        }
    }
}
