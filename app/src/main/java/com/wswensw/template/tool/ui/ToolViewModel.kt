package com.wswensw.template.tool.ui

import android.app.Application
import android.content.Intent
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import com.wswensw.template.tool.model.OledShieldConfig
import com.wswensw.template.tool.service.AccessibilityServiceMonitor
import com.wswensw.template.tool.service.ToolServiceStateStore
import com.wswensw.template.tool.storage.OledShieldConfigRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class ToolViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = OledShieldConfigRepository(application)
    private val mutableDefaultOledConfig = MutableStateFlow(repository.loadDefault())

    val serviceState = ToolServiceStateStore.state
    val defaultOledConfig = mutableDefaultOledConfig.asStateFlow()

    init {
        refreshAccessibilityState()
    }

    fun refreshAccessibilityState() {
        val enabled = AccessibilityServiceMonitor.isEnabled(getApplication())
        ToolServiceStateStore.update { it.copy(isAccessibilityEnabled = enabled) }
    }

    fun openAccessibilitySettings() {
        getApplication<Application>().startActivity(
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    fun updateDefaultOledConfig(transform: (OledShieldConfig) -> OledShieldConfig) {
        mutableDefaultOledConfig.update { transform(it).normalized() }
    }

    fun saveDefaultOledConfig() {
        repository.saveDefault(mutableDefaultOledConfig.value)
    }
}
