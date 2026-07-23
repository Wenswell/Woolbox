package com.wswensw.woolbox.tool.service

import com.wswensw.woolbox.tool.model.OledShieldConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class ToolActivity {
    NONE,
    COPY_PREPARING,
    COPY_ACTIVE,
    OLED_PREPARING,
    OLED_ACTIVE,
}

internal fun ToolActivity.matches(command: ExternalToolCommand): Boolean = when (command) {
    ExternalToolCommand.GLOBAL_COPY -> this == ToolActivity.COPY_PREPARING || this == ToolActivity.COPY_ACTIVE
    ExternalToolCommand.OLED_SHIELD,
    is ExternalToolCommand.OledShieldProfile,
    -> this == ToolActivity.OLED_PREPARING || this == ToolActivity.OLED_ACTIVE
}

internal fun ExternalToolCommand.acceptsTarget(packageName: String): Boolean = when (this) {
    ExternalToolCommand.GLOBAL_COPY,
    ExternalToolCommand.OLED_SHIELD,
    -> true

    is ExternalToolCommand.OledShieldProfile -> this.packageName == packageName
}

internal fun ExternalToolCommand.preparingActivity(): ToolActivity = when (this) {
    ExternalToolCommand.GLOBAL_COPY -> ToolActivity.COPY_PREPARING
    ExternalToolCommand.OLED_SHIELD,
    is ExternalToolCommand.OledShieldProfile,
    -> ToolActivity.OLED_PREPARING
}

internal fun ExternalToolCommand.timeoutMillis(): Long = when (this) {
    is ExternalToolCommand.OledShieldProfile -> PROFILE_COMMAND_TIMEOUT_MILLIS
    ExternalToolCommand.GLOBAL_COPY,
    ExternalToolCommand.OLED_SHIELD,
    -> STANDARD_COMMAND_TIMEOUT_MILLIS
}

private const val STANDARD_COMMAND_TIMEOUT_MILLIS = 2_000L
private const val PROFILE_COMMAND_TIMEOUT_MILLIS = 5_000L

data class ToolServiceState(
    val isAccessibilityEnabled: Boolean = false,
    val isServiceConnected: Boolean = false,
    val targetPackageName: String? = null,
    val activeTool: ToolActivity = ToolActivity.NONE,
    val activeOledConfig: OledShieldConfig? = null,
)

object ToolServiceStateStore {
    private val mutableState = MutableStateFlow(ToolServiceState())
    val state = mutableState.asStateFlow()

    fun update(transform: (ToolServiceState) -> ToolServiceState) {
        mutableState.update(transform)
    }
}
