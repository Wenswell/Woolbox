package com.wswensw.woolbox.tool.ui

import android.app.Application
import android.content.Intent
import android.graphics.Bitmap
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wswensw.woolbox.tool.model.OledShieldConfig
import com.wswensw.woolbox.tool.model.OledShieldProfile
import com.wswensw.woolbox.tool.service.AccessibilityServiceMonitor
import com.wswensw.woolbox.tool.service.ToolServiceStateStore
import com.wswensw.woolbox.tool.storage.OledShieldConfigRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

data class OledShieldProfileUi(
    val profile: OledShieldProfile,
    val appName: String,
    val appIcon: Bitmap?,
    val isLaunchable: Boolean,
)

class ToolViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = OledShieldConfigRepository(application)
    private val mutableDefaultOledConfig = MutableStateFlow(repository.loadDefault())
    private val profileRefresh = MutableStateFlow(0)

    val serviceState = ToolServiceStateStore.state
    val defaultOledConfig = mutableDefaultOledConfig.asStateFlow()
    val oledProfiles: StateFlow<List<OledShieldProfileUi>> = combine(
        repository.profiles,
        profileRefresh,
    ) { profiles, _ ->
        profiles.map(application::resolveProfile)
    }.flowOn(Dispatchers.IO).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    init {
        refreshAccessibilityState()
    }

    fun refreshAccessibilityState() {
        val enabled = AccessibilityServiceMonitor.isEnabled(getApplication())
        ToolServiceStateStore.update { it.copy(isAccessibilityEnabled = enabled) }
        profileRefresh.update(Int::inc)
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

    fun deleteOledProfile(packageName: String) {
        repository.deleteProfile(packageName)
    }
}

private fun Application.resolveProfile(profile: OledShieldProfile): OledShieldProfileUi {
    val appInfo = resolveAppDisplayInfo(profile.packageName)
    return if (appInfo == null) {
        OledShieldProfileUi(
            profile = profile,
            appName = profile.packageName,
            appIcon = null,
            isLaunchable = false,
        )
    } else {
        OledShieldProfileUi(
            profile = profile,
            appName = appInfo.name,
            appIcon = appInfo.icon,
            isLaunchable = packageManager.getLaunchIntentForPackage(profile.packageName) != null,
        )
    }
}
