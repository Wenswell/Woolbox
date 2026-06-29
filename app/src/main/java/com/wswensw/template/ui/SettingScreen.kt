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
import com.wswensw.template.R

@Composable
fun SettingScreen(modifier: Modifier = Modifier) {
    var sampleToggleEnabled by rememberSaveable { mutableStateOf(false) }
    var denseModeEnabled by rememberSaveable { mutableStateOf(true) }

    TemplatePage(
        title = "Setting",
        subtitle = "Central place for app preferences and reusable settings rows.",
        modifier = modifier,
    ) {
        TemplateSection(title = "Appearance") {
            TemplateListItem(
                title = "Theme",
                subtitle = "Follow Material 3 dynamic color and system dark mode.",
                iconRes = R.drawable.ic_color_lens_24,
            )
            TemplateDivider()
            TemplateListItem(
                title = "Density",
                subtitle = "Keep operational pages compact for repeated use.",
                iconRes = R.drawable.ic_tune_24,
                trailing = {
                    Switch(
                        checked = denseModeEnabled,
                        onCheckedChange = { denseModeEnabled = it },
                    )
                },
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
                        onCheckedChange = { sampleToggleEnabled = it },
                    )
                },
            )
            TemplateDivider()
            TemplateListItem(
                title = "Template options",
                subtitle = "Add new settings here as full-row entries.",
                iconRes = R.drawable.ic_settings_24,
            )
        }

        Spacer(Modifier.height(12.dp))

        TemplateSection(title = "About") {
            TemplateListItem(
                title = "Initial version",
                subtitle = "0.1.0",
                iconRes = R.drawable.ic_info_24,
            )
        }
    }
}
