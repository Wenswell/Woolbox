package com.wswensw.template.tool.ui

import android.app.Application
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.core.graphics.drawable.toBitmap

internal data class AppDisplayInfo(
    val name: String,
    val icon: Bitmap,
)

internal fun Application.resolveAppDisplayInfo(packageName: String): AppDisplayInfo? = try {
    val applicationInfo = packageManager.getApplicationInfo(packageName, 0)
    AppDisplayInfo(
        name = applicationInfo.loadLabel(packageManager).toString(),
        icon = applicationInfo.loadIcon(packageManager).toBitmap(
            width = APP_ICON_SIZE_PX,
            height = APP_ICON_SIZE_PX,
            config = Bitmap.Config.ARGB_8888,
        ),
    )
} catch (_: PackageManager.NameNotFoundException) {
    null
}

private const val APP_ICON_SIZE_PX = 128
