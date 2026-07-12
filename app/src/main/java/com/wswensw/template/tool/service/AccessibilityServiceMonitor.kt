package com.wswensw.template.tool.service

import android.content.ComponentName
import android.content.Context
import android.provider.Settings

object AccessibilityServiceMonitor {
    fun isEnabled(context: Context): Boolean {
        val target = ComponentName(context, ToolAccessibilityService::class.java).flattenToString()
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ).orEmpty()

        return enabledServices
            .split(':')
            .any { ComponentName.unflattenFromString(it)?.flattenToString() == target }
    }
}
