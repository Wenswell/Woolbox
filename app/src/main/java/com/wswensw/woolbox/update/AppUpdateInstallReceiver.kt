package com.wswensw.woolbox.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build

class AppUpdateInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AppUpdateInstaller.ActionInstallStatus) return
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE).orEmpty()
        val versionName = intent.getStringExtra(AppUpdateInstaller.ExtraVersionName).orEmpty()
        val confirmationIntent = if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            intent.compatIntentExtra(Intent.EXTRA_INTENT)
        } else {
            null
        }
        AppUpdateManager.handleInstallStatus(context, status, message, versionName, confirmationIntent)
    }

    private fun Intent.compatIntentExtra(name: String): Intent? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(name, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            getParcelableExtra(name) as? Intent
        }
}
