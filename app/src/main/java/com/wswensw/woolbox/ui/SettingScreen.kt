package com.wswensw.woolbox.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.wswensw.woolbox.BuildConfig
import com.wswensw.woolbox.record.data.RootTaskPreviewLimit
import com.wswensw.woolbox.update.AppUpdatePhase
import com.wswensw.woolbox.update.AppUpdateUiState

@Composable
fun SettingScreen(
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    recordsConnection: String,
    onOpenRecordsConnection: () -> Unit,
    rootTaskPreviewLimit: RootTaskPreviewLimit,
    onRootTaskPreviewLimitChange: (RootTaskPreviewLimit) -> Unit,
    updateState: AppUpdateUiState,
    onCheckForUpdate: () -> Unit,
    onDownloadAndInstall: () -> Unit,
    onOpenInstallSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showThemeDialog by rememberSaveable { mutableStateOf(false) }
    var showPreviewLimitDialog by rememberSaveable { mutableStateOf(false) }
    var showVersionDialog by rememberSaveable { mutableStateOf(false) }
    var showDownloadConfirmation by rememberSaveable { mutableStateOf(false) }

    AppPage(
        title = "Setting",
        subtitle = "Appearance and app information.",
        modifier = modifier,
    ) {
        AppSection(title = "Appearance") {
            AppListItem(
                title = "Theme",
                subtitle = themeMode.label,
                leadingContent = {
                    AppListLeadingIcon(imageVector = Icons.Outlined.Palette)
                },
                onClick = { showThemeDialog = true },
            )
        }

        AppSection(title = "Records") {
            AppListItem(
                title = "Connection",
                subtitle = recordsConnection,
                leadingContent = {
                    AppListLeadingIcon(imageVector = Icons.Outlined.Link)
                },
                navigate = true,
                onClick = onOpenRecordsConnection,
            )
            AppListItem(
                title = "Root task preview",
                subtitle = rootTaskPreviewLimit.label,
                leadingContent = {
                    AppListLeadingIcon(imageVector = Icons.Outlined.Info)
                },
                onClick = { showPreviewLimitDialog = true },
            )
        }

        AppSection(title = "About") {
            AppListItem(
                title = "Version",
                subtitle = BuildConfig.VERSION_NAME,
                leadingContent = {
                    AppListLeadingIcon(imageVector = Icons.Outlined.Info)
                },
                onClick = { showVersionDialog = true },
            )
        }

        AppSection(title = "Updates") {
            AppListItem(
                title = "Current version",
                subtitle = "${BuildConfig.BUILD_TYPE} ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                leadingContent = {
                    AppListLeadingIcon(imageVector = Icons.Outlined.Info)
                },
                enabled = !updateState.isBusy,
                onClick = onCheckForUpdate,
            )
            if (updateState.hasUpdate) {
                val updateVersion = "${BuildConfig.BUILD_TYPE} ${updateState.availableVersionName}"
                val title = when (updateState.phase) {
                    AppUpdatePhase.DOWNLOADED -> "Install $updateVersion"
                    AppUpdatePhase.WAITING_FOR_INSTALL_PERMISSION -> "Allow installs"
                    AppUpdatePhase.INSTALLING -> "Installing $updateVersion"
                    else -> "Update $updateVersion"
                }
                val onClick = when (updateState.phase) {
                    AppUpdatePhase.AVAILABLE,
                    AppUpdatePhase.DOWNLOADED,
                    -> { { showDownloadConfirmation = true } }

                    AppUpdatePhase.WAITING_FOR_INSTALL_PERMISSION -> onOpenInstallSettings
                    else -> null
                }
                AppListItem(
                    title = title,
                    subtitle = updateState.status,
                    leadingContent = {
                        AppListLeadingIcon(imageVector = Icons.Outlined.Info)
                    },
                    enabled = !updateState.isBusy,
                    onClick = onClick,
                )
            } else if (updateState.status != "Check for updates.") {
                AppListItem(
                    title = "Update status",
                    subtitle = updateState.status,
                    leadingContent = {
                        AppListLeadingIcon(imageVector = Icons.Outlined.Info)
                    },
                )
            }
        }
    }

    if (showThemeDialog) {
        AppSingleChoiceDialog(
            title = "Theme",
            options = ThemeMode.entries,
            selected = themeMode,
            label = { it.label },
            onSelected = onThemeModeChange,
            onDismiss = { showThemeDialog = false },
        )
    }

    if (showPreviewLimitDialog) {
        AppSingleChoiceDialog(
            title = "Root task preview",
            options = RootTaskPreviewLimit.entries,
            selected = rootTaskPreviewLimit,
            label = { it.label },
            onSelected = onRootTaskPreviewLimitChange,
            onDismiss = { showPreviewLimitDialog = false },
        )
    }

    if (showVersionDialog) {
        AppInfoDialog(
            title = "Version",
            body = "Version: ${BuildConfig.VERSION_NAME}\nVersion code: ${BuildConfig.VERSION_CODE}\nPackage: ${BuildConfig.APPLICATION_ID}",
            onDismiss = { showVersionDialog = false },
        )
    }

    if (showDownloadConfirmation) {
        val isDownloaded = updateState.phase == AppUpdatePhase.DOWNLOADED
        AlertDialog(
            onDismissRequest = { showDownloadConfirmation = false },
            title = { Text(if (isDownloaded) "Install update" else "Download update") },
            text = {
                Text(
                    if (isDownloaded) {
                        "Open Android installer for ${BuildConfig.BUILD_TYPE} ${updateState.availableVersionName}?"
                    } else {
                        "Download ${BuildConfig.BUILD_TYPE} ${updateState.availableVersionName} " +
                            "(${updateState.availableSizeBytes.formatUpdateBytes()}) and open Android installer?"
                    },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDownloadConfirmation = false
                        onDownloadAndInstall()
                    },
                ) {
                    Text(if (isDownloaded) "Install" else "Download")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDownloadConfirmation = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

private fun Long.formatUpdateBytes(): String = when {
    this < 1024L -> "$this B"
    this < 1024L * 1024L -> "%.1f KiB".format(java.util.Locale.US, this / 1024.0)
    else -> "%.2f MiB".format(java.util.Locale.US, this / (1024.0 * 1024.0))
}
