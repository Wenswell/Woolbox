package com.wswensw.woolbox.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.AddToHomeScreen
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.rounded.Check
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalIconButton
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wswensw.woolbox.R
import com.wswensw.woolbox.tool.model.OledShieldConfig
import com.wswensw.woolbox.tool.service.ToolServiceState
import com.wswensw.woolbox.tool.ui.OledShieldProfileUi
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
    AppRouteScaffold(
        title = stringResource(R.string.global_copy_tile_label),
        onBack = onBack,
    ) {
        AppSection(title = stringResource(R.string.section_actions)) {
            AppListItem(
                title = stringResource(R.string.global_copy_start),
                subtitle = if (state.isServiceConnected) {
                    state.globalCopyStatus()
                } else {
                    state.accessibilityStatus()
                },
                leadingContent = {
                    AppListLeadingIcon(
                        imageVector = Icons.Outlined.PlayArrow,
                        enabled = state.isServiceConnected,
                    )
                },
                enabled = state.isServiceConnected,
                onClick = onLaunchTool,
            )
        }


        AppSection(title = stringResource(R.string.section_quick_settings)) {
            AppListItem(
                title = stringResource(R.string.global_copy_tile),
                subtitle = if (tileRequestAvailable) {
                    stringResource(R.string.tile_add)
                } else {
                    stringResource(R.string.tile_add_from_system)
                },
                leadingContent = {
                    AppListLeadingIcon(
                        imageVector = Icons.AutoMirrored.Outlined.AddToHomeScreen,
                        enabled = tileRequestAvailable,
                    )
                },
                enabled = tileRequestAvailable,
                onClick = onRequestTile,
            )
        }


        AppSection(title = stringResource(R.string.section_history)) {
            AppListItem(
                title = stringResource(R.string.captured_text),
                subtitle = if (historyCount == 0) {
                    stringResource(R.string.no_captures)
                } else {
                    pluralStringResource(R.plurals.capture_count, historyCount, historyCount)
                },
                leadingContent = {
                    AppListLeadingIcon(imageVector = Icons.Outlined.History)
                },
                navigate = true,
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

    AppRouteScaffold(
        title = stringResource(R.string.oled_shield_tile_label),
        onBack = onBack,
    ) {
        AppSection(title = stringResource(R.string.section_actions)) {
            AppListItem(
                title = stringResource(R.string.oled_show_shield),
                subtitle = state.oledStatus(),
                leadingContent = {
                    AppListLeadingIcon(imageVector = Icons.Outlined.DarkMode)
                },
                onClick = onLaunchTool,
            )
        }


        AppSection(title = stringResource(R.string.section_quick_settings)) {
            AppListItem(
                title = stringResource(R.string.oled_tile),
                subtitle = if (tileRequestAvailable) {
                    stringResource(R.string.tile_add)
                } else {
                    stringResource(R.string.tile_add_from_system)
                },
                leadingContent = {
                    AppListLeadingIcon(
                        imageVector = Icons.AutoMirrored.Outlined.AddToHomeScreen,
                        enabled = tileRequestAvailable,
                    )
                },
                enabled = tileRequestAvailable,
                onClick = onRequestTile,
            )
        }


        AppSection(title = stringResource(R.string.oled_default_shield)) {
            Column(
                modifier = Modifier.padding(
                    horizontal = AppStyle.spacing.pageHorizontal,
                    vertical = 8.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OledDefaultSlider(
                    label = stringResource(R.string.oled_opacity),
                    value = defaultConfig.opacityPercent,
                    onValueChange = { value ->
                        onConfigChange { config -> config.copy(opacityPercent = value) }
                    },
                )
                OledDefaultSlider(
                    label = stringResource(R.string.oled_width),
                    value = defaultConfig.widthPercent,
                    onValueChange = { value ->
                        onConfigChange { config -> config.copy(widthPercent = value) }
                    },
                )
                OledDefaultSlider(
                    label = stringResource(R.string.oled_height),
                    value = defaultConfig.heightPercent,
                    onValueChange = { value ->
                        onConfigChange { config -> config.copy(heightPercent = value) }
                    },
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    FilledTonalIconButton(onClick = onSaveDefaultConfig) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = stringResource(R.string.oled_save_default),
                        )
                    }
                }
            }
        }


        AppSection(title = stringResource(R.string.oled_saved_apps)) {
            if (profiles.isEmpty()) {
                Text(
                    text = stringResource(R.string.oled_no_saved_apps),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(
                        horizontal = AppStyle.spacing.pageHorizontal,
                        vertical = 20.dp,
                    ),
                )
            } else {
                profiles.forEachIndexed { index, profile ->
                    OledProfileRow(
                        profile = profile,
                        serviceConnected = state.isServiceConnected,
                        onEnable = { onEnableProfile(profile) },
                        onDelete = { pendingDelete = profile.profile.packageName },
                    )
                    if (index != profiles.lastIndex) {
                        AppDivider(start = 72.dp)
                    }
                }
            }
        }
    }

    pendingDelete?.let { packageName ->
        val profile = profiles.firstOrNull { it.profile.packageName == packageName }
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.oled_delete_saved_app)) },
            text = {
                Text(stringResource(R.string.oled_delete_saved_app_body, profile?.appName ?: packageName))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteProfile(packageName)
                        pendingDelete = null
                    },
                ) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun OledProfileRow(
    profile: OledShieldProfileUi,
    serviceConnected: Boolean,
    onEnable: () -> Unit,
    onDelete: () -> Unit,
) {
    val config = profile.profile.config
    val enableAvailable = serviceConnected && profile.isLaunchable
    val availability = when {
        !profile.isLaunchable -> stringResource(R.string.oled_unavailable)
        !serviceConnected -> stringResource(R.string.oled_accessibility_required)
        else -> null
    }
    val values = stringResource(
        R.string.oled_profile_values,
        config.opacityPercent,
        config.widthPercent,
        config.heightPercent,
    )
    val recorded = stringResource(R.string.oled_recorded_at, formatProfileTime(profile.profile.recordedAt))
    val subtitle = buildString {
        when {
            availability != null -> appendLine(availability)
        }
        appendLine(values)
        append(recorded)
    }.trimEnd()

    AppListItem(
        title = profile.appName,
        subtitle = subtitle,
        leadingContent = {
            AppIcon(
                bitmap = profile.appIcon,
                fallbackIconRes = R.drawable.ic_oled_shield_24,
                contentDescription = null,
            )
        },
        subtitleMaxLines = 3,
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    enabled = enableAvailable,
                    onClick = onEnable,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_play_arrow_24),
                        contentDescription = stringResource(R.string.oled_enable_app, profile.appName),
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        painter = painterResource(R.drawable.ic_delete_24),
                        contentDescription = stringResource(R.string.oled_delete_app, profile.appName),
                    )
                }
            }
        },
    )
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
            Text(
                text = label,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "$value%",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.roundToInt()) },
            valueRange = OledShieldConfig.MIN_PERCENT.toFloat()..OledShieldConfig.MAX_PERCENT.toFloat(),
        )
    }
}

private fun formatProfileTime(epochMillis: Long): String = DateTimeFormatter
    .ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
    .withLocale(Locale.getDefault())
    .format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))
