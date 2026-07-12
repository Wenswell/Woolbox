package com.wswensw.template.tool.storage

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.wswensw.template.tool.model.OledShieldConfig

class OledShieldConfigRepository(context: Context) {
    private val preferences: SharedPreferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    fun loadDefault(): OledShieldConfig = load(DEFAULT_PREFIX, OledShieldConfig.Default)

    fun saveDefault(config: OledShieldConfig) {
        save(DEFAULT_PREFIX, config)
    }

    fun loadForPackage(packageName: String): OledShieldConfig {
        val prefix = packagePrefix(packageName)
        return if (preferences.contains(key(prefix, OPACITY))) {
            load(prefix, loadDefault())
        } else {
            loadDefault()
        }
    }

    fun saveForPackage(
        packageName: String,
        config: OledShieldConfig,
    ) {
        save(packagePrefix(packageName), config)
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
        val normalized = config.normalized()
        preferences.edit {
            putInt(key(prefix, OPACITY), normalized.opacityPercent)
            putInt(key(prefix, WIDTH), normalized.widthPercent)
            putInt(key(prefix, HEIGHT), normalized.heightPercent)
            putFloat(key(prefix, PANEL_X), normalized.panelXPercent)
            putFloat(key(prefix, PANEL_Y), normalized.panelYPercent)
        }
    }

    private fun packagePrefix(packageName: String): String = "package.${packageName.trim()}"

    private fun key(
        prefix: String,
        field: String,
    ): String = "$prefix.$field"

    private companion object {
        const val PREFERENCES_NAME = "oled_shield_config"
        const val DEFAULT_PREFIX = "default"
        const val OPACITY = "opacity"
        const val WIDTH = "width"
        const val HEIGHT = "height"
        const val PANEL_X = "panel_x"
        const val PANEL_Y = "panel_y"
    }
}
