package com.wswensw.woolbox.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.provider.Settings
import com.wswensw.woolbox.BuildConfig
import com.wswensw.woolbox.R
import com.wswensw.woolbox.ui.UiText
import com.wswensw.woolbox.ui.uiText
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AppUpdatePhase {
    IDLE,
    CHECKING,
    AVAILABLE,
    DOWNLOADING,
    DOWNLOADED,
    WAITING_FOR_INSTALL_PERMISSION,
    INSTALLING,
}

data class AppUpdateUiState(
    val phase: AppUpdatePhase = AppUpdatePhase.IDLE,
    val availableVersionCode: Long? = null,
    val availableVersionName: String = "",
    val availableSizeBytes: Long = 0L,
    val status: UiText = uiText(R.string.update_check_prompt),
    val isInstallPermissionGranted: Boolean = false,
) {
    val isBusy: Boolean
        get() = phase == AppUpdatePhase.CHECKING ||
            phase == AppUpdatePhase.DOWNLOADING ||
            phase == AppUpdatePhase.INSTALLING

    val hasUpdate: Boolean
        get() = availableVersionCode != null
}

object AppUpdateManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val operationInFlight = AtomicBoolean(false)
    private val mutableState = MutableStateFlow(AppUpdateUiState())
    private lateinit var appContext: Context
    private var latestManifest: AppUpdateManifest? = null

    val state: StateFlow<AppUpdateUiState> = mutableState

    fun initialize(context: Context) {
        appContext = context.applicationContext
        updateDirectory().mkdirs()
        syncInstallPermission()
    }

    fun syncInstallPermission() {
        if (!::appContext.isInitialized) return
        val granted = appContext.packageManager.canRequestPackageInstalls()
        mutableState.update { state ->
            state.copy(
                phase = if (state.phase == AppUpdatePhase.WAITING_FOR_INSTALL_PERMISSION && granted) {
                    AppUpdatePhase.DOWNLOADED
                } else {
                    state.phase
                },
                status = if (state.phase == AppUpdatePhase.WAITING_FOR_INSTALL_PERMISSION && granted) {
                    uiText(R.string.update_ready_install)
                } else {
                    state.status
                },
                isInstallPermissionGranted = granted,
            )
        }
    }

    fun checkForUpdate() {
        requireInitialized()
        if (!beginOperation()) return
        scope.launch {
            try {
                checkForUpdateInternal()
            } finally {
                operationInFlight.set(false)
            }
        }
    }

    fun downloadAndInstall() {
        requireInitialized()
        val manifest = latestManifest
        if (manifest == null) {
            mutableState.update { it.copy(status = uiText(R.string.update_check_first)) }
            return
        }
        if (!beginOperation()) return
        scope.launch {
            try {
                downloadAndInstallInternal(manifest)
            } finally {
                operationInFlight.set(false)
            }
        }
    }

    fun openInstallSettings() {
        requireInitialized()
        appContext.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                .setData(Uri.fromParts("package", appContext.packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    fun handleInstallStatus(
        context: Context,
        status: Int,
        statusMessage: String,
        versionName: String,
        confirmationIntent: Intent?,
    ) {
        initialize(context)
        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                if (confirmationIntent == null) {
                    mutableState.update {
                        it.copy(
                            phase = AppUpdatePhase.DOWNLOADED,
                            status = uiText(R.string.update_confirmation_unavailable),
                        )
                    }
                    return
                }
                mutableState.update {
                    it.copy(
                        phase = AppUpdatePhase.INSTALLING,
                        status = uiText(R.string.update_confirm_installer),
                    )
                }
                context.startActivity(confirmationIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }

            PackageInstaller.STATUS_SUCCESS -> {
                latestManifest = null
                mutableState.value = AppUpdateUiState(
                    status = uiText(R.string.update_installed, versionName),
                    isInstallPermissionGranted = appContext.packageManager.canRequestPackageInstalls(),
                )
            }

            else -> {
                mutableState.update {
                    it.copy(
                        phase = AppUpdatePhase.DOWNLOADED,
                        status = installFailureMessage(status, statusMessage),
                    )
                }
            }
        }
    }

    private fun checkForUpdateInternal() {
        mutableState.update {
            it.copy(
                phase = AppUpdatePhase.CHECKING,
                status = uiText(R.string.update_checking),
                isInstallPermissionGranted = appContext.packageManager.canRequestPackageInstalls(),
            )
        }
        val result = runCatching { AppUpdateClient.fetchManifest(BuildConfig.UPDATE_MANIFEST_URL) }
        val manifest = result.getOrNull()
        when {
            manifest == null -> {
                latestManifest = null
                mutableState.value = AppUpdateUiState(
                    status = result.exceptionOrNull()?.message?.let(UiText::Dynamic)
                        ?: uiText(R.string.update_check_failed),
                    isInstallPermissionGranted = appContext.packageManager.canRequestPackageInstalls(),
                )
            }

            manifest.versionCode <= BuildConfig.VERSION_CODE -> {
                latestManifest = null
                mutableState.value = AppUpdateUiState(
                    status = uiText(
                        R.string.update_up_to_date,
                        BuildConfig.VERSION_NAME,
                        BuildConfig.VERSION_CODE,
                    ),
                    isInstallPermissionGranted = appContext.packageManager.canRequestPackageInstalls(),
                )
            }

            else -> {
                latestManifest = manifest
                val cachedApk = verifiedUpdateFile(manifest)
                mutableState.value = AppUpdateUiState(
                    phase = if (cachedApk == null) AppUpdatePhase.AVAILABLE else AppUpdatePhase.DOWNLOADED,
                    availableVersionCode = manifest.versionCode,
                    availableVersionName = manifest.versionName,
                    availableSizeBytes = manifest.sizeBytes,
                    status = if (cachedApk == null) {
                        uiText(R.string.update_available, manifest.versionName)
                    } else {
                        uiText(R.string.update_ready, manifest.versionName)
                    },
                    isInstallPermissionGranted = appContext.packageManager.canRequestPackageInstalls(),
                )
            }
        }
    }

    private fun downloadAndInstallInternal(manifest: AppUpdateManifest) {
        val apkFile = verifiedUpdateFile(manifest) ?: downloadUpdate(manifest) ?: return
        if (!appContext.packageManager.canRequestPackageInstalls()) {
            mutableState.update {
                it.copy(
                    phase = AppUpdatePhase.WAITING_FOR_INSTALL_PERMISSION,
                    status = uiText(R.string.update_allow_system),
                    isInstallPermissionGranted = false,
                )
            }
            openInstallSettings()
            return
        }
        mutableState.update {
            it.copy(
                phase = AppUpdatePhase.INSTALLING,
                status = uiText(R.string.update_preparing, manifest.versionName),
                isInstallPermissionGranted = true,
            )
        }
        runCatching { AppUpdateInstaller.install(appContext, apkFile, manifest) }
            .onFailure { error ->
                mutableState.update {
                    it.copy(
                        phase = AppUpdatePhase.DOWNLOADED,
                        status = error.message?.let(UiText::Dynamic) ?: uiText(R.string.update_install_failed),
                    )
                }
            }
    }

    private fun downloadUpdate(manifest: AppUpdateManifest): File? {
        mutableState.update {
            it.copy(
                phase = AppUpdatePhase.DOWNLOADING,
                status = uiText(R.string.update_downloading, manifest.versionName),
            )
        }
        val destination = updateFile(manifest)
        clearOtherUpdateFiles(destination)
        val result = runCatching {
            AppUpdateClient.downloadApk(manifest, destination) { progress ->
                mutableState.update {
                    it.copy(status = progress.statusFor(manifest.versionName))
                }
            }
            destination
        }
        return result.fold(
            onSuccess = { apkFile ->
                mutableState.update {
                    it.copy(
                        phase = AppUpdatePhase.DOWNLOADED,
                        status = uiText(R.string.update_downloaded, manifest.versionName),
                    )
                }
                apkFile
            },
            onFailure = { error ->
                mutableState.update {
                    it.copy(
                        phase = AppUpdatePhase.AVAILABLE,
                        status = error.message?.let(UiText::Dynamic) ?: uiText(R.string.update_download_failed),
                    )
                }
                null
            },
        )
    }

    private fun beginOperation(): Boolean {
        if (mutableState.value.isBusy || !operationInFlight.compareAndSet(false, true)) {
            mutableState.update { it.copy(status = uiText(R.string.update_operation_busy)) }
            return false
        }
        return true
    }

    private fun verifiedUpdateFile(manifest: AppUpdateManifest): File? {
        val apkFile = updateFile(manifest)
        if (!apkFile.isFile) return null
        if (AppUpdateClient.verifyDownloadedApk(manifest, apkFile)) return apkFile
        apkFile.delete()
        return null
    }

    private fun updateDirectory(): File = File(appContext.filesDir, "updates")

    private fun updateFile(manifest: AppUpdateManifest): File =
        File(updateDirectory(), "woolbox-${manifest.versionCode}.apk")

    private fun clearOtherUpdateFiles(keep: File) {
        updateDirectory().listFiles()?.forEach { file ->
            if (file != keep) file.delete()
        }
    }

    private fun requireInitialized() {
        check(::appContext.isInitialized) { "AppUpdateManager.initialize(context) must run first." }
    }

    private fun AppUpdateDownloadProgress.statusFor(versionName: String): UiText {
        val percent = (bytesDownloaded * 100.0 / totalBytes).coerceIn(0.0, 100.0)
        val locale = appContext.resources.configuration.locales[0]
        return uiText(
            R.string.update_download_progress,
            versionName,
            bytesDownloaded.formatBytes(),
            totalBytes.formatBytes(),
            "%.0f".format(locale, percent),
            bytesPerSecond.formatBytes(),
        )
    }

    private fun Long.formatBytes(): String {
        val locale = appContext.resources.configuration.locales[0]
        return when {
            this < 1024L -> "$this B"
            this < 1024L * 1024L -> "%.1f KiB".format(locale, this / 1024.0)
            else -> "%.2f MiB".format(locale, this / (1024.0 * 1024.0))
        }
    }

    private fun installFailureMessage(status: Int, statusMessage: String): UiText {
        val label = when (status) {
            PackageInstaller.STATUS_FAILURE_ABORTED -> uiText(R.string.update_install_canceled)
            PackageInstaller.STATUS_FAILURE_BLOCKED -> uiText(R.string.update_install_blocked)
            PackageInstaller.STATUS_FAILURE_CONFLICT -> uiText(R.string.update_install_conflict)
            PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> uiText(R.string.update_install_incompatible)
            PackageInstaller.STATUS_FAILURE_INVALID -> uiText(R.string.update_invalid_apk)
            PackageInstaller.STATUS_FAILURE_STORAGE -> uiText(R.string.update_install_storage_error)
            else -> uiText(R.string.update_install_failed_generic)
        }
        return if (statusMessage.isBlank()) label else uiText(R.string.update_failure_detail, label, UiText.Dynamic(statusMessage))
    }
}
