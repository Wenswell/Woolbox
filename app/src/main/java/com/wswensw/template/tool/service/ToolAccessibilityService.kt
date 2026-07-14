package com.wswensw.template.tool.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.res.Configuration
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import com.wswensw.template.tool.model.OledShieldConfig
import com.wswensw.template.tool.overlay.CopySessionController
import com.wswensw.template.tool.overlay.OledShieldController
import com.wswensw.template.tool.storage.OledShieldConfigRepository
import com.wswensw.template.tool.trigger.ToolTileRefresher

class ToolAccessibilityService : AccessibilityService() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val windowResolver = ForegroundWindowResolver(this)
    private val snapshotBuilder = WindowSnapshotBuilder(this)
    private lateinit var copySessionController: CopySessionController
    private lateinit var oledShieldController: OledShieldController
    private var pendingCommand: PendingExternalCommand? = null
    private var isCleanedUp: Boolean = false
    private val refreshTargetRunnable = Runnable(::refreshForegroundTarget)
    private val resolvePendingCommandRunnable = Runnable(::resolvePendingCommand)

    override fun onServiceConnected() {
        super.onServiceConnected()
        isCleanedUp = false
        mainHandler.removeCallbacksAndMessages(null)
        pendingCommand = null
        if (::copySessionController.isInitialized) copySessionController.close(notify = false)
        if (::oledShieldController.isInitialized) oledShieldController.hide(notify = false)
        val repository = OledShieldConfigRepository(this)
        copySessionController = CopySessionController(this, ::onCopySessionChanged)
        oledShieldController = OledShieldController(this, repository, ::onOledShieldChanged)
        ToolServiceController.attach(this)
        ToolServiceStateStore.update {
            it.copy(
                isAccessibilityEnabled = true,
                isServiceConnected = true,
            )
        }
        refreshForegroundTarget()
        refreshTiles()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
            scheduleTargetRefresh()
            return
        }

        val sourcePackageName = event.packageName?.toString()
        when (sourcePackageName) {
            packageName -> {
                if (windowResolver.isOwnApplicationForeground()) {
                    cancelPendingCommand()
                    closeAllTools()
                    ToolServiceStateStore.update { it.copy(targetPackageName = null) }
                }
            }

            SYSTEM_UI_PACKAGE -> Unit
            else -> scheduleTargetRefresh()
        }
    }

    override fun onInterrupt() = Unit

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (::oledShieldController.isInitialized) {
            oledShieldController.onConfigurationChanged()
        }
    }

    override fun onDestroy() {
        cleanUp()
        super.onDestroy()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        cleanUp()
        return super.onUnbind(intent)
    }

    private fun cleanUp() {
        if (isCleanedUp) return
        isCleanedUp = true
        mainHandler.removeCallbacksAndMessages(null)
        cancelPendingCommand()
        closeAllTools()
        ToolServiceController.detach(this)
        ToolServiceStateStore.update {
            ToolServiceState(
                isAccessibilityEnabled = AccessibilityServiceMonitor.isEnabled(this),
            )
        }
        refreshTiles()
    }

    fun executeQuickSettingsCommand(command: ExternalToolCommand) {
        mainHandler.post {
            val activeTool = ToolServiceStateStore.state.value.activeTool
            if (activeTool.matches(command)) {
                cancelPendingCommand()
                closeAllTools()
                performGlobalAction(GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE)
                return@post
            }

            cancelPendingCommand()
            closeAllTools()

            pendingCommand = PendingExternalCommand(
                command = command,
                deadlineAtMillis = SystemClock.uptimeMillis() + COMMAND_TIMEOUT_MILLIS,
            )
            ToolServiceStateStore.update {
                it.copy(
                    activeTool = command.preparingActivity(),
                    activeOledConfig = null,
                )
            }
            refreshTiles()
            performGlobalAction(GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE)
            mainHandler.postDelayed(resolvePendingCommandRunnable, COMMAND_POLL_MILLIS)
        }
    }

    private fun resolvePendingCommand() {
        val pending = pendingCommand ?: return
        if (windowResolver.isSystemUiForeground()) {
            schedulePendingResolution(pending)
            return
        }

        val target = windowResolver.resolve()
        if (target == null) {
            schedulePendingResolution(pending)
            return
        }
        pendingCommand = null
        ToolServiceStateStore.update { it.copy(targetPackageName = target.packageName) }
        when (pending.command) {
            ExternalToolCommand.GLOBAL_COPY -> startCopySession(target)
            ExternalToolCommand.OLED_SHIELD -> oledShieldController.show(target.packageName)
        }
    }

    private fun schedulePendingResolution(pending: PendingExternalCommand) {
        if (SystemClock.uptimeMillis() >= pending.deadlineAtMillis) {
            pendingCommand = null
            ToolServiceStateStore.update {
                it.copy(
                    activeTool = ToolActivity.NONE,
                    activeOledConfig = null,
                )
            }
            refreshTiles()
        } else {
            mainHandler.postDelayed(resolvePendingCommandRunnable, COMMAND_POLL_MILLIS)
        }
    }

    private fun startCopySession(target: ForegroundTarget) {
        val snapshot = snapshotBuilder.build(target)
        if (snapshot.blocks.isEmpty()) {
            ToolServiceStateStore.update { it.copy(activeTool = ToolActivity.NONE) }
            refreshTiles()
            return
        }
        copySessionController.show(snapshot)
    }

    private fun refreshForegroundTarget() {
        if (windowResolver.isOwnApplicationForeground()) {
            cancelPendingCommand()
            closeAllTools()
            ToolServiceStateStore.update { it.copy(targetPackageName = null) }
            return
        }
        if (pendingCommand != null || windowResolver.isSystemUiForeground()) return

        val target = windowResolver.resolve()
        val currentState = ToolServiceStateStore.state.value
        if (target == null) {
            if (currentState.activeTool.isActive()) closeAllTools()
            ToolServiceStateStore.update { it.copy(targetPackageName = null) }
            return
        }

        if (
            currentState.activeTool.isActive() &&
            currentState.targetPackageName != target.packageName
        ) {
            closeAllTools()
        }
        ToolServiceStateStore.update { it.copy(targetPackageName = target.packageName) }
    }

    private fun closeAllTools() {
        val hadActiveTool = ToolServiceStateStore.state.value.activeTool != ToolActivity.NONE
        if (::copySessionController.isInitialized) copySessionController.close(notify = false)
        if (::oledShieldController.isInitialized) oledShieldController.hide(notify = false)
        if (ToolServiceStateStore.state.value.activeTool != ToolActivity.NONE) {
            ToolServiceStateStore.update {
                it.copy(
                    activeTool = ToolActivity.NONE,
                    activeOledConfig = null,
                )
            }
        }
        if (hadActiveTool) refreshTiles()
    }

    private fun onCopySessionChanged(
        active: Boolean,
        packageName: String?,
    ) {
        ToolServiceStateStore.update {
            it.copy(
                targetPackageName = packageName ?: it.targetPackageName,
                activeTool = if (active) ToolActivity.COPY_ACTIVE else ToolActivity.NONE,
                activeOledConfig = null,
            )
        }
        refreshTiles()
    }

    private fun onOledShieldChanged(
        active: Boolean,
        packageName: String?,
        config: OledShieldConfig?,
    ) {
        ToolServiceStateStore.update {
            it.copy(
                targetPackageName = packageName ?: it.targetPackageName,
                activeTool = if (active) ToolActivity.OLED_ACTIVE else ToolActivity.NONE,
                activeOledConfig = config,
            )
        }
        refreshTiles()
    }

    private fun cancelPendingCommand() {
        pendingCommand = null
        mainHandler.removeCallbacks(resolvePendingCommandRunnable)
    }

    private fun scheduleTargetRefresh() {
        mainHandler.removeCallbacks(refreshTargetRunnable)
        mainHandler.postDelayed(refreshTargetRunnable, TARGET_REFRESH_DELAY_MS)
    }

    private fun refreshTiles() {
        ToolTileRefresher.requestRefresh(this)
    }

    private data class PendingExternalCommand(
        val command: ExternalToolCommand,
        val deadlineAtMillis: Long,
    )

    private fun ToolActivity.matches(command: ExternalToolCommand): Boolean = when (command) {
        ExternalToolCommand.GLOBAL_COPY ->
            this == ToolActivity.COPY_PREPARING || this == ToolActivity.COPY_ACTIVE

        ExternalToolCommand.OLED_SHIELD ->
            this == ToolActivity.OLED_PREPARING || this == ToolActivity.OLED_ACTIVE
    }

    private fun ExternalToolCommand.preparingActivity(): ToolActivity = when (this) {
        ExternalToolCommand.GLOBAL_COPY -> ToolActivity.COPY_PREPARING
        ExternalToolCommand.OLED_SHIELD -> ToolActivity.OLED_PREPARING
    }

    private fun ToolActivity.isActive(): Boolean = this == ToolActivity.COPY_ACTIVE || this == ToolActivity.OLED_ACTIVE

    private companion object {
        const val SYSTEM_UI_PACKAGE = "com.android.systemui"
        const val TARGET_REFRESH_DELAY_MS = 80L
        const val COMMAND_POLL_MILLIS = 120L
        const val COMMAND_TIMEOUT_MILLIS = 2_000L
    }
}
