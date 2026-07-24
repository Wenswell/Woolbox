package com.wswensw.woolbox.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.wswensw.woolbox.BuildConfig
import com.wswensw.woolbox.record.data.RootTaskPreviewLimit

@Composable
fun SettingScreen(
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    recordsConnection: String,
    onOpenRecordsConnection: () -> Unit,
    rootTaskPreviewLimit: RootTaskPreviewLimit,
    onRootTaskPreviewLimitChange: (RootTaskPreviewLimit) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showThemeDialog by rememberSaveable { mutableStateOf(false) }
    var showPreviewLimitDialog by rememberSaveable { mutableStateOf(false) }
    var showVersionDialog by rememberSaveable { mutableStateOf(false) }

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
}
