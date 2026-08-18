package com.wswensw.woolbox.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.wswensw.woolbox.BuildConfig
import com.wswensw.woolbox.R
import com.wswensw.woolbox.record.ui.MotionMode
import com.wswensw.woolbox.update.AppUpdatePhase
import com.wswensw.woolbox.update.AppUpdateUiState
import com.wswensw.woolbox.ui.UiText
import java.text.NumberFormat
import java.util.Locale

@Composable
fun SettingScreen(
    languageMode: LanguageMode,
    onLanguageModeChange: (LanguageMode) -> Unit,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    motionMode: MotionMode,
    onMotionModeChange: (MotionMode) -> Unit,
    recordsConnection: String,
    onOpenRecordsConnection: () -> Unit,
    updateState: AppUpdateUiState,
    onCheckForUpdate: () -> Unit,
    onDownloadAndInstall: () -> Unit,
    onOpenInstallSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val languageLabels = mapOf(
        LanguageMode.SYSTEM to stringResource(R.string.language_follow_system),
        LanguageMode.ENGLISH to stringResource(R.string.language_english),
        LanguageMode.SIMPLIFIED_CHINESE to stringResource(R.string.language_simplified_chinese),
    )
    val themeLabels = mapOf(
        ThemeMode.SYSTEM to stringResource(R.string.theme_follow_system),
        ThemeMode.LIGHT to stringResource(R.string.theme_light),
        ThemeMode.DARK to stringResource(R.string.theme_dark),
    )
    val motionLabels = mapOf(
        MotionMode.SYSTEM to stringResource(R.string.motion_follow_system),
        MotionMode.ON to stringResource(R.string.motion_on),
        MotionMode.OFF to stringResource(R.string.motion_off),
    )
    val updateStatus = updateState.status.asString()
    var showLanguageDialog by rememberSaveable { mutableStateOf(false) }
    var showThemeDialog by rememberSaveable { mutableStateOf(false) }
    var showMotionDialog by rememberSaveable { mutableStateOf(false) }
    var showVersionDialog by rememberSaveable { mutableStateOf(false) }
    var showDownloadConfirmation by rememberSaveable { mutableStateOf(false) }

    AppPage(
        title = stringResource(R.string.setting_title),
        subtitle = stringResource(R.string.setting_subtitle),
        modifier = modifier,
    ) {
        AppSection(title = stringResource(R.string.setting_appearance)) {
            AppListItem(
                title = stringResource(R.string.setting_language),
                subtitle = languageLabels.getValue(languageMode),
                leadingContent = {
                    AppListLeadingIcon(imageVector = Icons.Outlined.Translate)
                },
                onClick = { showLanguageDialog = true },
            )
            AppListItem(
                title = stringResource(R.string.setting_theme),
                subtitle = themeLabels.getValue(themeMode),
                leadingContent = {
                    AppListLeadingIcon(imageVector = Icons.Outlined.Palette)
                },
                onClick = { showThemeDialog = true },
            )
            AppListItem(
                title = stringResource(R.string.setting_motion),
                subtitle = motionLabels.getValue(motionMode),
                leadingContent = {
                    AppListLeadingIcon(imageVector = Icons.Outlined.Info)
                },
                onClick = { showMotionDialog = true },
            )
        }

        AppSection(title = stringResource(R.string.setting_records)) {
            AppListItem(
                title = stringResource(R.string.setting_connection),
                subtitle = recordsConnection,
                leadingContent = {
                    AppListLeadingIcon(imageVector = Icons.Outlined.Link)
                },
                navigate = true,
                onClick = onOpenRecordsConnection,
            )
        }

        AppSection(title = stringResource(R.string.setting_about)) {
            AppListItem(
                title = stringResource(R.string.setting_version),
                subtitle = BuildConfig.VERSION_NAME,
                leadingContent = {
                    AppListLeadingIcon(imageVector = Icons.Outlined.Info)
                },
                onClick = { showVersionDialog = true },
            )
        }

        AppSection(title = stringResource(R.string.setting_updates)) {
            AppListItem(
                title = stringResource(R.string.setting_current_version),
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
                    AppUpdatePhase.DOWNLOADED -> stringResource(R.string.update_install_version, updateVersion)
                    AppUpdatePhase.WAITING_FOR_INSTALL_PERMISSION -> stringResource(R.string.update_allow_installs)
                    AppUpdatePhase.INSTALLING -> stringResource(R.string.update_installing_version, updateVersion)
                    else -> stringResource(R.string.update_version, updateVersion)
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
                    subtitle = updateStatus,
                    leadingContent = {
                        AppListLeadingIcon(imageVector = Icons.Outlined.Info)
                    },
                    enabled = !updateState.isBusy,
                    onClick = onClick,
                )
            } else if (updateState.status != UiText.Resource(R.string.update_check_prompt)) {
                AppListItem(
                    title = stringResource(R.string.setting_update_status),
                    subtitle = updateStatus,
                    leadingContent = {
                        AppListLeadingIcon(imageVector = Icons.Outlined.Info)
                    },
                )
            }
        }
    }

    if (showLanguageDialog) {
        AppSingleChoiceDialog(
            title = stringResource(R.string.setting_language),
            options = LanguageMode.entries,
            selected = languageMode,
            label = languageLabels::getValue,
            onSelected = onLanguageModeChange,
            onDismiss = { showLanguageDialog = false },
        )
    }

    if (showThemeDialog) {
        AppSingleChoiceDialog(
            title = stringResource(R.string.setting_theme),
            options = ThemeMode.entries,
            selected = themeMode,
            label = themeLabels::getValue,
            onSelected = onThemeModeChange,
            onDismiss = { showThemeDialog = false },
        )
    }

    if (showMotionDialog) {
        AppSingleChoiceDialog(
            title = stringResource(R.string.setting_motion),
            options = MotionMode.entries,
            selected = motionMode,
            label = motionLabels::getValue,
            onSelected = onMotionModeChange,
            onDismiss = { showMotionDialog = false },
        )
    }

    if (showVersionDialog) {
        AppInfoDialog(
            title = stringResource(R.string.setting_version),
            body = stringResource(
                R.string.version_details,
                BuildConfig.VERSION_NAME,
                BuildConfig.VERSION_CODE,
                BuildConfig.APPLICATION_ID,
            ),
            onDismiss = { showVersionDialog = false },
        )
    }

    if (showDownloadConfirmation) {
        val isDownloaded = updateState.phase == AppUpdatePhase.DOWNLOADED
        AlertDialog(
            onDismissRequest = { showDownloadConfirmation = false },
            title = {
                Text(stringResource(if (isDownloaded) R.string.install_update else R.string.download_update))
            },
            text = {
                Text(
                    if (isDownloaded) {
                        stringResource(
                            R.string.install_update_question,
                            BuildConfig.BUILD_TYPE,
                            updateState.availableVersionName,
                        )
                    } else {
                        stringResource(
                            R.string.download_update_question,
                            BuildConfig.BUILD_TYPE,
                            updateState.availableVersionName,
                            updateState.availableSizeBytes.formatUpdateBytes(),
                        )
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
                    Text(stringResource(if (isDownloaded) R.string.action_install else R.string.action_download))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDownloadConfirmation = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

private fun Long.formatUpdateBytes(locale: Locale = Locale.getDefault()): String = when {
    this < 1024L -> "$this B"
    this < 1024L * 1024L -> "${NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = 1 }.format(this / 1024.0)} KiB"
    else -> "${NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = 2 }.format(this / (1024.0 * 1024.0))} MiB"
}
