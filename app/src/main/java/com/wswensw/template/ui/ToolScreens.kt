package com.wswensw.template.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.wswensw.template.R
import com.wswensw.template.tool.model.OledShieldConfig
import com.wswensw.template.tool.service.ToolActivity
import com.wswensw.template.tool.service.ToolServiceState
import com.wswensw.template.tool.ui.OledShieldProfileUi
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun GlobalCopyRoute(
    state: ToolServiceState,
    historyCount: Int,
    onBack: () -> Unit,
    onLaunchTool: () -> Unit,
    onOpenHistory: () -> Unit,
    tileRequestAvailable: Boolean,
    onRequestTile: () -> Unit,
) {
    TemplateRouteScaffold(
        title = "Global Copy",
        onBack = onBack,
    ) {
        TemplateSection(title = "Actions") {
            TemplateListItem(
                title = "Start Global Copy",
                subtitle = if (state.isServiceConnected) {
                    state.globalCopyStatus()
                } else {
                    state.accessibilityStatus()
                },
                iconRes = R.drawable.ic_content_copy_24,
                enabled = state.isServiceConnected,
                onClick = onLaunchTool,
            )
        }

        Spacer(Modifier.height(12.dp))

        TemplateSection(title = "Quick settings") {
            TemplateListItem(
                title = "Global Copy tile",
                subtitle = state.globalCopyStatus(),
                iconRes = R.drawable.ic_content_copy_24,
                enabled = tileRequestAvailable,
                onClick = onRequestTile,
            )
        }

        Spacer(Modifier.height(12.dp))

        TemplateSection(title = "History") {
            TemplateListItem(
                title = "Captured text",
                subtitle = when (historyCount) {
                    0 -> "No captures yet"
                    1 -> "1 capture"
                    else -> "$historyCount captures"
                },
                iconRes = R.drawable.ic_content_copy_24,
                onClick = onOpenHistory,
            )
        }
    }
}

@Composable
fun OledShieldRoute(
    state: ToolServiceState,
    defaultConfig: OledShieldConfig,
    profiles: List<OledShieldProfileUi>,
    onBack: () -> Unit,
    onLaunchTool: () -> Unit,
    tileRequestAvailable: Boolean,
    onRequestTile: () -> Unit,
    onConfigChange: ((OledShieldConfig) -> OledShieldConfig) -> Unit,
    onSaveDefaultConfig: () -> Unit,
    onEnableProfile: (OledShieldProfileUi) -> Unit,
    onDeleteProfile: (String) -> Unit,
) {
    var pendingDelete by rememberSaveable { mutableStateOf<String?>(null) }

    TemplateRouteScaffold(
        title = "OLED shield",
        onBack = onBack,
    ) {
        TemplateSection(title = "Actions") {
            TemplateListItem(
                title = "Show shield",
                subtitle = state.oledStatus(),
                iconRes = R.drawable.ic_oled_shield_24,
                onClick = onLaunchTool,
            )
        }

        Spacer(Modifier.height(12.dp))

        TemplateSection(title = "Quick settings") {
            TemplateListItem(
                title = "OLED shield tile",
                subtitle = state.oledStatus(),
                iconRes = R.drawable.ic_oled_shield_24,
                enabled = tileRequestAvailable,
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

        Spacer(Modifier.height(12.dp))

        TemplateSection(title = "Saved apps") {
            if (profiles.isEmpty()) {
                Text(
                    text = "No saved apps yet.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 20.dp),
                )
            } else {
                profiles.forEachIndexed { index, profile ->
                    OledProfileRow(
                        profile = profile,
                        enableAvailable = state.isServiceConnected && profile.isLaunchable,
                        onEnable = { onEnableProfile(profile) },
                        onDelete = { pendingDelete = profile.profile.packageName },
                    )
                    if (index != profiles.lastIndex) TemplateDivider()
                }
            }
        }
    }

    pendingDelete?.let { packageName ->
        val profile = profiles.firstOrNull { it.profile.packageName == packageName }
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete saved app?") },
            text = {
                Text("The saved OLED shield configuration for ${profile?.appName ?: packageName} will be removed.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteProfile(packageName)
                        pendingDelete = null
                    },
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun OledProfileRow(
    profile: OledShieldProfileUi,
    enableAvailable: Boolean,
    onEnable: () -> Unit,
    onDelete: () -> Unit,
) {
    val config = profile.profile.config
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, top = 8.dp, end = 4.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TemplateAppIcon(
                bitmap = profile.appIcon,
                fallbackIconRes = R.drawable.ic_oled_shield_24,
                contentDescription = null,
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
            ) {
                Text(
                    text = profile.appName,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodyLarge,
                )
                if (!profile.isLaunchable) {
                    Text(
                        text = "Unavailable",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    enabled = enableAvailable,
                    onClick = onEnable,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_play_arrow_24),
                        contentDescription = "Enable ${profile.appName}",
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        painter = painterResource(R.drawable.ic_delete_24),
                        contentDescription = "Delete ${profile.appName}",
                    )
                }
            }
        }
        Text(
            text = "Opacity ${config.opacityPercent}% | Width ${config.widthPercent}% | Height ${config.heightPercent}%",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = 52.dp, end = 12.dp),
        )
        Text(
            text = "Recorded ${formatProfileTime(profile.profile.recordedAt)}",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(start = 52.dp, end = 12.dp),
        )
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

private fun formatProfileTime(epochMillis: Long): String = DateTimeFormatter
    .ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
    .withLocale(Locale.getDefault())
    .format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))
