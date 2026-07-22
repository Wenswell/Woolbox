package com.wswensw.template.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.wswensw.template.R
import com.wswensw.template.tool.service.ToolActivity
import com.wswensw.template.tool.service.ToolServiceState

@Composable
fun HomeScreen(
    state: ToolServiceState,
    onOpenGlobalCopy: () -> Unit,
    onOpenOledShield: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onAccessibilityAlreadyEnabled: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TemplatePage(
        title = "Tools",
        subtitle = "Accessibility tools for the active app.",
        modifier = modifier,
    ) {
        TemplateSection(title = "Access") {
            TemplateListItem(
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

        TemplateSection(title = "Available") {
            TemplateListItem(
                title = "Global Copy",
                subtitle = state.globalCopyStatus(),
                iconRes = R.drawable.ic_content_copy_24,
                navigate = true,
                onClick = onOpenGlobalCopy,
            )
            TemplateDivider()
            TemplateListItem(
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
