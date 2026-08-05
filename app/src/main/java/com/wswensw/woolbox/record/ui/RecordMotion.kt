package com.wswensw.woolbox.record.ui

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

enum class MotionMode {
    SYSTEM,
    ON,
    OFF,
}

internal val LocalMotionMode = staticCompositionLocalOf { MotionMode.SYSTEM }

/** True while the Records root list is scrolling; freezes decorative item motion. */
internal val LocalPauseDecorativeMotion = staticCompositionLocalOf { false }

@Composable
internal fun prefersReducedMotion(): Boolean {
    val context = LocalContext.current
    val systemPrefersReducedMotion = remember(context) {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) == 0f
    }
    return when (LocalMotionMode.current) {
        MotionMode.SYSTEM -> systemPrefersReducedMotion
        MotionMode.ON -> false
        MotionMode.OFF -> true
    }
}
