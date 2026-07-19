package com.wswensw.template.tool.service

import com.wswensw.template.tool.model.OledShieldConfig
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
    ExternalToolCommand.OLED_SHIELD -> this == ToolActivity.OLED_PREPARING || this == ToolActivity.OLED_ACTIVE
}

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
