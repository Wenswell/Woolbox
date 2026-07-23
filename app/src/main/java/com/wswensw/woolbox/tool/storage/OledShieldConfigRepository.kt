package com.wswensw.woolbox.tool.storage

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.wswensw.woolbox.tool.model.OledShieldConfig
import com.wswensw.woolbox.tool.model.OledShieldProfile
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

class OledShieldConfigRepository internal constructor(
    private val preferences: SharedPreferences,
    private val currentTimeMillis: () -> Long,
) {
    constructor(context: Context) : this(
        preferences = context.applicationContext.getSharedPreferences(
            PREFERENCES_NAME,
            Context.MODE_PRIVATE,
        ),
        currentTimeMillis = System::currentTimeMillis,
    )

    val profiles: Flow<List<OledShieldProfile>> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, changedKey ->
            if (changedKey == PROFILE_PACKAGES || changedKey?.startsWith(PROFILE_PREFIX) == true) {
                trySend(loadProfiles())
            }
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        trySend(loadProfiles())
        awaitClose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }.distinctUntilChanged()

    fun loadDefault(): OledShieldConfig = load(DEFAULT_PREFIX, OledShieldConfig.Default)

    fun saveDefault(config: OledShieldConfig) {
        save(DEFAULT_PREFIX, config)
    }

    fun loadForPackage(packageName: String): OledShieldConfig {
        val normalizedPackageName = normalizedPackageName(packageName)
        return if (profilePackages().contains(normalizedPackageName)) {
            loadProfile(normalizedPackageName).config
        } else {
            loadDefault()
        }
    }

    fun loadProfiles(): List<OledShieldProfile> = profilePackages()
        .map(::loadProfile)
        .sortedWith(
            compareByDescending<OledShieldProfile> { it.recordedAt }
                .thenByDescending { it.packageName },
        )

    fun recordUse(
        packageName: String,
        config: OledShieldConfig,
    ): OledShieldProfile {
        val normalizedPackageName = normalizedPackageName(packageName)
        val profile = OledShieldProfile(
            packageName = normalizedPackageName,
            config = config.normalized(),
            recordedAt = currentTimeMillis(),
        )
        val packages = profilePackages() + normalizedPackageName
        preferences.edit {
            putStringSet(PROFILE_PACKAGES, packages)
            putConfig(profilePrefix(normalizedPackageName), profile.config)
            putLong(key(profilePrefix(normalizedPackageName), RECORDED_AT), profile.recordedAt)
        }
        return profile
    }

    fun saveForPackage(
        packageName: String,
        config: OledShieldConfig,
    ) {
        val normalizedPackageName = normalizedPackageName(packageName)
        check(profilePackages().contains(normalizedPackageName)) {
            "OLED shield profile does not exist: $normalizedPackageName"
        }
        loadProfile(normalizedPackageName)
        save(profilePrefix(normalizedPackageName), config)
    }

    fun deleteProfile(packageName: String) {
        val normalizedPackageName = normalizedPackageName(packageName)
        val packages = profilePackages() - normalizedPackageName
        val prefix = profilePrefix(normalizedPackageName)
        preferences.edit {
            if (packages.isEmpty()) {
                remove(PROFILE_PACKAGES)
            } else {
                putStringSet(PROFILE_PACKAGES, packages)
            }
            remove(key(prefix, OPACITY))
            remove(key(prefix, WIDTH))
            remove(key(prefix, HEIGHT))
            remove(key(prefix, PANEL_X))
            remove(key(prefix, PANEL_Y))
            remove(key(prefix, RECORDED_AT))
        }
    }

    private fun load(
        prefix: String,
        base: OledShieldConfig,
    ): OledShieldConfig = OledShieldConfig(
        opacityPercent = preferences.getInt(key(prefix, OPACITY), base.opacityPercent),
        widthPercent = preferences.getInt(key(prefix, WIDTH), base.widthPercent),
        heightPercent = preferences.getInt(key(prefix, HEIGHT), base.heightPercent),
        panelXPercent = preferences.getFloat(key(prefix, PANEL_X), base.panelXPercent),
        panelYPercent = preferences.getFloat(key(prefix, PANEL_Y), base.panelYPercent),
    ).normalized()

    private fun save(
        prefix: String,
        config: OledShieldConfig,
    ) {
        preferences.edit {
            putConfig(prefix, config.normalized())
        }
    }

    private fun loadProfile(packageName: String): OledShieldProfile {
        val prefix = profilePrefix(packageName)
        val requiredKeys = listOf(OPACITY, WIDTH, HEIGHT, PANEL_X, PANEL_Y, RECORDED_AT)
            .map { field -> key(prefix, field) }
        check(requiredKeys.all(preferences::contains)) {
            "OLED shield profile is incomplete: $packageName"
        }
        return OledShieldProfile(
            packageName = packageName,
            config = load(prefix, OledShieldConfig.Default),
            recordedAt = preferences.getLong(key(prefix, RECORDED_AT), 0L),
        )
    }

    private fun profilePackages(): Set<String> =
        preferences.getStringSet(PROFILE_PACKAGES, emptySet()).orEmpty().toSet()

    private fun SharedPreferences.Editor.putConfig(
        prefix: String,
        config: OledShieldConfig,
    ) {
        putInt(key(prefix, OPACITY), config.opacityPercent)
        putInt(key(prefix, WIDTH), config.widthPercent)
        putInt(key(prefix, HEIGHT), config.heightPercent)
        putFloat(key(prefix, PANEL_X), config.panelXPercent)
        putFloat(key(prefix, PANEL_Y), config.panelYPercent)
    }

    private fun normalizedPackageName(packageName: String): String = packageName.trim().also {
        require(it.isNotEmpty()) { "Package name must not be blank" }
    }

    private fun profilePrefix(packageName: String): String = "$PROFILE_PREFIX$packageName"

    private fun key(
        prefix: String,
        field: String,
    ): String = "$prefix.$field"

    private companion object {
        const val PREFERENCES_NAME = "oled_shield_config"
        const val DEFAULT_PREFIX = "default"
        const val PROFILE_PACKAGES = "profiles"
        const val PROFILE_PREFIX = "profile."
        const val OPACITY = "opacity"
        const val WIDTH = "width"
        const val HEIGHT = "height"
        const val PANEL_X = "panel_x"
        const val PANEL_Y = "panel_y"
        const val RECORDED_AT = "recorded_at"
    }
}
