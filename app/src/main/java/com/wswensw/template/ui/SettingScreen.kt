package com.wswensw.template.ui

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
import com.wswensw.template.BuildConfig

@Composable
fun SettingScreen(
    themeMode: TemplateThemeMode,
    onThemeModeChange: (TemplateThemeMode) -> Unit,
    recordsConnection: String,
    onOpenRecordsConnection: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showThemeDialog by rememberSaveable { mutableStateOf(false) }
    var showVersionDialog by rememberSaveable { mutableStateOf(false) }

    TemplatePage(
        title = "Setting",
        subtitle = "Appearance and app information.",
        modifier = modifier,
    ) {
        TemplateSection(title = "Appearance") {
            TemplateListItem(
                title = "Theme",
                subtitle = themeMode.label,
                leadingContent = {
                    TemplateListLeadingIcon(imageVector = Icons.Outlined.Palette)
                },
                onClick = { showThemeDialog = true },
            )
        }

        TemplateSection(title = "Records") {
            TemplateListItem(
                title = "Connection",
                subtitle = recordsConnection,
                leadingContent = {
                    TemplateListLeadingIcon(imageVector = Icons.Outlined.Link)
                },
                navigate = true,
                onClick = onOpenRecordsConnection,
            )
        }

        TemplateSection(title = "About") {
            TemplateListItem(
                title = "Version",
                subtitle = BuildConfig.VERSION_NAME,
                leadingContent = {
                    TemplateListLeadingIcon(imageVector = Icons.Outlined.Info)
                },
                onClick = { showVersionDialog = true },
            )
        }
    }

    if (showThemeDialog) {
        TemplateSingleChoiceDialog(
            title = "Theme",
            options = TemplateThemeMode.entries,
            selected = themeMode,
            label = { it.label },
            onSelected = onThemeModeChange,
            onDismiss = { showThemeDialog = false },
        )
    }

    if (showVersionDialog) {
        TemplateInfoDialog(
            title = "Version",
            body = "Version: ${BuildConfig.VERSION_NAME}\nVersion code: ${BuildConfig.VERSION_CODE}\nPackage: ${BuildConfig.APPLICATION_ID}",
            onDismiss = { showVersionDialog = false },
        )
    }
}
