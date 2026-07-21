package com.wswensw.template.tool.trigger

import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.Icon
import android.os.Build
import com.wswensw.template.R

enum class ToolTile {
    GLOBAL_COPY,
    OLED_SHIELD,
}

enum class TileRequestResult {
    ADDED,
    ALREADY_ADDED,
    NOT_ADDED,
    ERROR,
}

object TileRequestManager {
    val isRequestSupported: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    fun request(
        context: Context,
        tile: ToolTile,
        onResult: (TileRequestResult) -> Unit,
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            error("Tile add requests require Android 13 or newer.")
        }
        val serviceClass = when (tile) {
            ToolTile.GLOBAL_COPY -> GlobalCopyTileService::class.java
            ToolTile.OLED_SHIELD -> OledShieldTileService::class.java
        }
        val labelRes = when (tile) {
            ToolTile.GLOBAL_COPY -> R.string.global_copy_tile_label
            ToolTile.OLED_SHIELD -> R.string.oled_shield_tile_label
        }
        val iconRes = when (tile) {
            ToolTile.GLOBAL_COPY -> R.drawable.ic_content_copy_24
            ToolTile.OLED_SHIELD -> R.drawable.ic_oled_shield_24
        }
        val manager = context.getSystemService(StatusBarManager::class.java)
        manager.requestAddTileService(
            ComponentName(context, serviceClass),
            context.getString(labelRes),
            Icon.createWithResource(context, iconRes),
            context.mainExecutor,
        ) { result ->
            onResult(
                when (result) {
                    StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED -> TileRequestResult.ADDED
                    StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED -> TileRequestResult.ALREADY_ADDED
                    StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_NOT_ADDED -> TileRequestResult.NOT_ADDED
                    else -> TileRequestResult.ERROR
                },
            )
        }
    }
}
