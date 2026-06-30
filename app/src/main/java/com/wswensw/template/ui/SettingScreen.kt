package com.wswensw.template.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wswensw.template.BuildConfig
import com.wswensw.template.R

@Composable
fun SettingScreen(
    themeMode: TemplateThemeMode,
    onThemeModeChange: (TemplateThemeMode) -> Unit,
    templateOption: String,
    onTemplateOptionChange: (String) -> Unit,
    onOpenSettingsGuide: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var sampleToggleEnabled by rememberSaveable { mutableStateOf(false) }
    var denseModeEnabled by rememberSaveable { mutableStateOf(true) }
    var showThemeDialog by rememberSaveable { mutableStateOf(false) }
    var showTemplateOptionsDialog by rememberSaveable { mutableStateOf(false) }
    var showVersionDialog by rememberSaveable { mutableStateOf(false) }

    TemplatePage(
        title = "Setting",
        subtitle = "Central place for app preferences and reusable settings rows.",
        modifier = modifier,
    ) {
        TemplateSection(title = "Appearance") {
            TemplateListItem(
                title = "Theme",
                subtitle = themeMode.label,
                iconRes = R.drawable.ic_color_lens_24,
                onClick = { showThemeDialog = true },
            )
            TemplateDivider()
            TemplateListItem(
                title = "Density",
                subtitle = "Keep operational pages compact for repeated use.",
                iconRes = R.drawable.ic_tune_24,
                trailing = {
                    Switch(
                        checked = denseModeEnabled,
                        onCheckedChange = null,
                    )
                },
                onClick = { denseModeEnabled = !denseModeEnabled },
            )
        }

        Spacer(Modifier.height(12.dp))

        TemplateSection(title = "Preferences") {
            TemplateListItem(
                title = "Sample toggle",
                subtitle = "Neutral switch row for future boolean settings.",
                iconRes = R.drawable.ic_check_24,
                trailing = {
                    Switch(
                        checked = sampleToggleEnabled,
                        onCheckedChange = null,
                    )
                },
                onClick = { sampleToggleEnabled = !sampleToggleEnabled },
            )
            TemplateDivider()
            TemplateListItem(
                title = "Template options",
                subtitle = templateOption,
                iconRes = R.drawable.ic_settings_24,
                onClick = { showTemplateOptionsDialog = true },
            )
            TemplateDivider()
            TemplateListItem(
                title = "Settings pattern",
                subtitle = "Open a child page with settings structure guidance.",
                iconRes = R.drawable.ic_article_24,
                onClick = onOpenSettingsGuide,
            )
        }

        Spacer(Modifier.height(12.dp))

        TemplateSection(title = "About") {
            TemplateListItem(
                title = "Initial version",
                subtitle = "0.1.0",
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

    if (showTemplateOptionsDialog) {
        TemplateTextInputDialog(
            title = "Template options",
            value = templateOption,
            label = "Option label",
            onDismiss = { showTemplateOptionsDialog = false },
            onConfirm = {
                onTemplateOptionChange(it)
                showTemplateOptionsDialog = false
            },
        )
    }

    if (showVersionDialog) {
        TemplateInfoDialog(
            title = "Initial version",
            body = "Version: ${BuildConfig.VERSION_NAME}\nVersion code: ${BuildConfig.VERSION_CODE}\nPackage: ${BuildConfig.APPLICATION_ID}",
            onDismiss = { showVersionDialog = false },
        )
    }
}
