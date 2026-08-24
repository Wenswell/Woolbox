package com.wswensw.woolbox

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.wswensw.woolbox.record.reminder.DailyReminderNotifications
import com.wswensw.woolbox.tool.service.ExternalToolCommand
import com.wswensw.woolbox.tool.service.ToolLaunchResult
import com.wswensw.woolbox.tool.service.ToolServiceController
import com.wswensw.woolbox.ui.WoolboxApp
import com.wswensw.woolbox.update.AppUpdateManager

class MainActivity : AppCompatActivity() {
    private var pendingToolCommand: ExternalToolCommand? = null
    private var dailyReviewRequest by mutableIntStateOf(0)
    private var hourlyCheckinRequest by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppUpdateManager.initialize(applicationContext)
        consumeReminderIntents(intent)
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
            WoolboxApp(
                dailyReviewRequest = dailyReviewRequest,
                hourlyCheckinRequest = hourlyCheckinRequest,
                onLaunchTool = ::launchTool,
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeReminderIntents(intent)
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

    private fun consumeReminderIntents(intent: Intent?) {
        if (intent?.getBooleanExtra(DailyReminderNotifications.EXTRA_OPEN_DAILY_REVIEW, false) == true) {
            dailyReviewRequest += 1
            intent.removeExtra(DailyReminderNotifications.EXTRA_OPEN_DAILY_REVIEW)
        }
        if (intent?.getBooleanExtra(DailyReminderNotifications.EXTRA_OPEN_HOURLY_CHECKIN, false) == true) {
            hourlyCheckinRequest += 1
            intent.removeExtra(DailyReminderNotifications.EXTRA_OPEN_HOURLY_CHECKIN)
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
