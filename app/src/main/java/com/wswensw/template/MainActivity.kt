package com.wswensw.template

import android.content.ActivityNotFoundException
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.wswensw.template.tool.service.ExternalToolCommand
import com.wswensw.template.tool.service.ToolLaunchResult
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

    private fun launchTool(command: ExternalToolCommand): ToolLaunchResult {
        if (!ToolServiceController.isConnected()) return ToolLaunchResult.ServiceUnavailable

        return when (command) {
            ExternalToolCommand.GLOBAL_COPY,
            ExternalToolCommand.OLED_SHIELD,
            -> launchForPreviousApplication(command)

            is ExternalToolCommand.OledShieldProfile -> launchProfile(command)
        }
    }

    private fun launchForPreviousApplication(command: ExternalToolCommand): ToolLaunchResult {
        pendingToolCommand = command
        return if (moveTaskToBack(true)) {
            ToolLaunchResult.Accepted
        } else {
            pendingToolCommand = null
            ToolLaunchResult.TargetLaunchFailed
        }
    }

    private fun launchProfile(command: ExternalToolCommand.OledShieldProfile): ToolLaunchResult {
        val intent = packageManager.getLaunchIntentForPackage(command.packageName)
            ?: return ToolLaunchResult.TargetAppUnavailable

        pendingToolCommand = command
        return try {
            startActivity(intent)
            ToolLaunchResult.Accepted
        } catch (_: ActivityNotFoundException) {
            pendingToolCommand = null
            ToolLaunchResult.TargetLaunchFailed
        }
    }
}
