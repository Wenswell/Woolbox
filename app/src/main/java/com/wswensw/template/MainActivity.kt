package com.wswensw.template

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.wswensw.template.tool.service.ExternalToolCommand
import com.wswensw.template.tool.service.ToolServiceController
import com.wswensw.template.ui.TemplateApp

class MainActivity : ComponentActivity() {
    private var pendingToolCommand: ExternalToolCommand? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                lightScrim = android.graphics.Color.TRANSPARENT,
                darkScrim = android.graphics.Color.TRANSPARENT,
            ),
            navigationBarStyle = SystemBarStyle.auto(
                lightScrim = android.graphics.Color.TRANSPARENT,
                darkScrim = android.graphics.Color.TRANSPARENT,
            ),
        )
        setContent {
            TemplateApp(onLaunchTool = ::launchTool)
        }
    }

    override fun onStop() {
        super.onStop()
        pendingToolCommand?.let(ToolServiceController::execute)
        pendingToolCommand = null
    }

    private fun launchTool(command: ExternalToolCommand) {
        if (!ToolServiceController.isConnected()) return

        pendingToolCommand = command
        if (!moveTaskToBack(true)) {
            pendingToolCommand = null
        }
    }
}
