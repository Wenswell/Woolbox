package com.wswensw.template.tool.trigger

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Intent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.wswensw.template.MainActivity
import com.wswensw.template.R
import com.wswensw.template.tool.service.ExternalToolCommand
import com.wswensw.template.tool.service.ToolServiceController
import com.wswensw.template.tool.service.ToolServiceStateStore
import com.wswensw.template.tool.service.matches

class GlobalCopyTileService : ToolTileService() {
    override val command: ExternalToolCommand = ExternalToolCommand.GLOBAL_COPY
    override val labelRes: Int = R.string.global_copy_tile_label
}

class OledShieldTileService : ToolTileService() {
    override val command: ExternalToolCommand = ExternalToolCommand.OLED_SHIELD
    override val labelRes: Int = R.string.oled_shield_tile_label
}

abstract class ToolTileService : TileService() {
    protected abstract val command: ExternalToolCommand
    protected abstract val labelRes: Int

    override fun onStartListening() {
        super.onStartListening()
        refreshTile()
    }

    override fun onClick() {
        unlockAndRun {
            if (ToolServiceController.isConnected()) {
                ToolServiceController.execute(command)
            } else {
                openApp()
            }
        }
    }

    private fun refreshTile() {
        val state = ToolServiceStateStore.state.value
        qsTile.label = getString(labelRes)
        qsTile.state = when {
            state.isServiceConnected && state.activeTool.matches(command) -> Tile.STATE_ACTIVE
            else -> Tile.STATE_INACTIVE
        }
        qsTile.updateTile()
    }

    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            labelRes,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        startActivityAndCollapse(pendingIntent)
    }
}

object ToolTileRefresher {
    fun requestRefresh(context: android.content.Context) {
        listOf(GlobalCopyTileService::class.java, OledShieldTileService::class.java).forEach { serviceClass ->
            TileService.requestListeningState(context, ComponentName(context, serviceClass))
        }
    }
}
