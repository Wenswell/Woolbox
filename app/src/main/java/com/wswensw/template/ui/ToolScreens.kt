package com.wswensw.template.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wswensw.template.R
import com.wswensw.template.tool.model.OledShieldConfig
import com.wswensw.template.tool.service.ToolActivity
import com.wswensw.template.tool.service.ToolServiceState
import kotlin.math.roundToInt

@Composable
fun GlobalCopyRoute(
    state: ToolServiceState,
    onBack: () -> Unit,
    onLaunchTool: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onRequestTile: () -> Unit,
) {
    TemplateRouteScaffold(
        title = "Global Copy",
        onBack = onBack,
    ) {
        TemplatePrimaryAction(
            label = "Start Global Copy",
            iconRes = R.drawable.ic_content_copy_24,
            enabled = state.isServiceConnected,
            onClick = onLaunchTool,
        )

        TemplateSection(title = "Access") {
            TemplateListItem(
                title = "Accessibility service",
                subtitle = state.accessibilityStatus(),
                iconRes = R.drawable.ic_settings_24,
                onClick = onOpenAccessibilitySettings,
            )
        }

        Spacer(Modifier.height(12.dp))

        TemplateSection(title = "Quick settings") {
            TemplateListItem(
                title = "Global Copy tile",
                subtitle = state.globalCopyStatus(),
                iconRes = R.drawable.ic_content_copy_24,
                trailing = {
                    FilledTonalButton(onClick = onRequestTile) {
                        Text("Add")
                    }
                },
                onClick = onRequestTile,
            )
        }
    }
}

@Composable
fun OledShieldRoute(
    state: ToolServiceState,
    defaultConfig: OledShieldConfig,
    onBack: () -> Unit,
    onLaunchTool: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onRequestTile: () -> Unit,
    onConfigChange: ((OledShieldConfig) -> OledShieldConfig) -> Unit,
    onSaveDefaultConfig: () -> Unit,
) {
    TemplateRouteScaffold(
        title = "OLED shield",
        onBack = onBack,
    ) {
        TemplatePrimaryAction(
            label = "Show shield",
            iconRes = R.drawable.ic_oled_shield_24,
            enabled = state.isServiceConnected,
            onClick = onLaunchTool,
        )

        TemplateSection(title = "Access") {
            TemplateListItem(
                title = "Accessibility service",
                subtitle = state.accessibilityStatus(),
                iconRes = R.drawable.ic_settings_24,
                onClick = onOpenAccessibilitySettings,
            )
        }

        Spacer(Modifier.height(12.dp))

        TemplateSection(title = "Quick settings") {
            TemplateListItem(
                title = "OLED shield tile",
                subtitle = state.oledStatus(),
                iconRes = R.drawable.ic_oled_shield_24,
                trailing = {
                    FilledTonalButton(onClick = onRequestTile) {
                        Text("Add")
                    }
                },
                onClick = onRequestTile,
            )
        }

        Spacer(Modifier.height(12.dp))

        TemplateSection(title = "Default shield") {
            Column(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                OledDefaultSlider(
                    label = "Opacity",
                    value = defaultConfig.opacityPercent,
                    onValueChange = { value ->
                        onConfigChange { config -> config.copy(opacityPercent = value) }
                    },
                )
                OledDefaultSlider(
                    label = "Width",
                    value = defaultConfig.widthPercent,
                    onValueChange = { value ->
                        onConfigChange { config -> config.copy(widthPercent = value) }
                    },
                )
                OledDefaultSlider(
                    label = "Height",
                    value = defaultConfig.heightPercent,
                    onValueChange = { value ->
                        onConfigChange { config -> config.copy(heightPercent = value) }
                    },
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    Button(onClick = onSaveDefaultConfig) {
                        Text("Save default")
                    }
                }
            }
        }
    }
}

@Composable
private fun OledDefaultSlider(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, color = MaterialTheme.colorScheme.onSurface)
            Text(
                text = "$value%",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.roundToInt()) },
            valueRange = OledShieldConfig.MIN_PERCENT.toFloat()..OledShieldConfig.MAX_PERCENT.toFloat(),
        )
    }
}

private fun ToolServiceState.accessibilityStatus(): String = when {
    isServiceConnected -> "Connected"
    isAccessibilityEnabled -> "Waiting for service"
    else -> "Not enabled"
}

private fun ToolServiceState.globalCopyStatus(): String = when (activeTool) {
    ToolActivity.COPY_PREPARING -> "Preparing"
    ToolActivity.COPY_ACTIVE -> "Active"
    else -> "Ready"
}

private fun ToolServiceState.oledStatus(): String = when (activeTool) {
    ToolActivity.OLED_PREPARING -> "Preparing"
    ToolActivity.OLED_ACTIVE -> "Active"
    else -> "Ready"
}
