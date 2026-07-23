package com.wswensw.woolbox.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.wswensw.woolbox.R
import com.wswensw.woolbox.tool.service.ToolActivity
import com.wswensw.woolbox.tool.service.ToolServiceState

@Composable
fun ToolsScreen(
    state: ToolServiceState,
    onOpenGlobalCopy: () -> Unit,
    onOpenOledShield: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onAccessibilityAlreadyEnabled: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AppPage(
        title = "Tools",
        subtitle = "Accessibility tools for the active app.",
        modifier = modifier,
    ) {
        AppSection(title = "Access") {
            AppListItem(
                title = "Accessibility service",
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
        }

        AppSection(title = "Available") {
            AppListItem(
                title = "Global Copy",
                subtitle = state.globalCopyStatus(),
                iconRes = R.drawable.ic_content_copy_24,
                navigate = true,
                onClick = onOpenGlobalCopy,
            )
            AppDivider()
            AppListItem(
                title = "OLED shield",
                subtitle = state.oledStatus(),
                iconRes = R.drawable.ic_oled_shield_24,
                navigate = true,
                onClick = onOpenOledShield,
            )
        }

    }
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

private fun ToolServiceState.accessibilityStatus(): String = when {
    isServiceConnected -> "Connected"
    isAccessibilityEnabled -> "Waiting for service"
    else -> "Not enabled"
}
