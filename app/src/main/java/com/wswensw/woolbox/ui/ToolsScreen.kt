package com.wswensw.woolbox.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.wswensw.woolbox.R
import com.wswensw.woolbox.tool.service.ToolActivity
import com.wswensw.woolbox.tool.service.ToolServiceState

@Composable
fun ToolsScreen(
    state: ToolServiceState,
    batteryOptimizationIgnored: Boolean,
    onOpenGlobalCopy: () -> Unit,
    onOpenOledShield: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onAccessibilityAlreadyEnabled: () -> Unit,
    onOpenBatteryOptimizationSettings: () -> Unit,
    onBatteryOptimizationAlreadyIgnored: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AppPage(
        title = stringResource(R.string.tools_title),
        subtitle = stringResource(R.string.tools_subtitle),
        modifier = modifier,
    ) {
        AppSection(title = stringResource(R.string.tools_access)) {
            AppListItem(
                title = stringResource(R.string.tools_accessibility_service),
                subtitle = state.accessibilityStatus(),
                leadingContent = {
                    Icon(
                        imageVector = if (state.isAccessibilityEnabled) {
                            Icons.Outlined.CheckCircle
                        } else {
                            Icons.Outlined.Lock
                        },
                        contentDescription = null,
                        tint = if (state.isAccessibilityEnabled) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                    )
                },
                onClick = if (state.isAccessibilityEnabled) {
                    onAccessibilityAlreadyEnabled
                } else {
                    onOpenAccessibilitySettings
                },
            )
            AppDivider()
            AppListItem(
                title = stringResource(R.string.tools_ignore_battery_optimization),
                subtitle = batteryOptimizationStatus(batteryOptimizationIgnored),
                leadingContent = {
                    Icon(
                        imageVector = if (batteryOptimizationIgnored) {
                            Icons.Outlined.CheckCircle
                        } else {
                            Icons.Outlined.Lock
                        },
                        contentDescription = null,
                        tint = if (batteryOptimizationIgnored) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                    )
                },
                onClick = if (batteryOptimizationIgnored) {
                    onBatteryOptimizationAlreadyIgnored
                } else {
                    onOpenBatteryOptimizationSettings
                },
            )
        }

        AppSection(title = stringResource(R.string.tools_available)) {
            AppListItem(
                title = stringResource(R.string.global_copy_tile_label),
                subtitle = state.globalCopyStatus(),
                iconRes = R.drawable.ic_content_copy_24,
                navigate = true,
                onClick = onOpenGlobalCopy,
            )
            AppDivider()
            AppListItem(
                title = stringResource(R.string.oled_shield_tile_label),
                subtitle = state.oledStatus(),
                iconRes = R.drawable.ic_oled_shield_24,
                navigate = true,
                onClick = onOpenOledShield,
            )
        }

    }
}

@Composable
internal fun ToolServiceState.globalCopyStatus(): String = when (activeTool) {
    ToolActivity.COPY_PREPARING -> stringResource(R.string.status_preparing)
    ToolActivity.COPY_ACTIVE -> stringResource(R.string.status_active)
    else -> stringResource(R.string.status_ready)
}

@Composable
internal fun ToolServiceState.oledStatus(): String = when (activeTool) {
    ToolActivity.OLED_PREPARING -> stringResource(R.string.status_preparing)
    ToolActivity.OLED_ACTIVE -> stringResource(R.string.status_active)
    else -> stringResource(R.string.status_ready)
}

@Composable
internal fun ToolServiceState.accessibilityStatus(): String = when {
    isServiceConnected -> stringResource(R.string.status_connected)
    isAccessibilityEnabled -> stringResource(R.string.status_waiting_for_service)
    else -> stringResource(R.string.status_not_enabled)
}

@Composable
private fun batteryOptimizationStatus(ignored: Boolean): String = if (ignored) {
    stringResource(R.string.status_authorized)
} else {
    stringResource(R.string.status_not_authorized)
}
