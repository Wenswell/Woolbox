package com.wswensw.template.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.wswensw.template.BuildConfig
import com.wswensw.template.R

@Composable
fun SettingScreen(
    themeMode: TemplateThemeMode,
    onThemeModeChange: (TemplateThemeMode) -> Unit,
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
                iconRes = R.drawable.ic_color_lens_24,
                onClick = { showThemeDialog = true },
            )
        }

        TemplateSection(title = "About") {
            TemplateListItem(
                title = "Version",
                subtitle = BuildConfig.VERSION_NAME,
                iconRes = R.drawable.ic_info_24,
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
