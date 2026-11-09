package com.wswensw.woolbox.tool.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.res.Configuration
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import com.wswensw.woolbox.tool.model.OledShieldConfig
import com.wswensw.woolbox.tool.overlay.CopySessionController
import com.wswensw.woolbox.tool.overlay.OledShieldController
import com.wswensw.woolbox.tool.storage.CopyHistoryDatabase
import com.wswensw.woolbox.tool.storage.CopyHistoryRepository
import com.wswensw.woolbox.tool.storage.OledShieldConfigRepository
import com.wswensw.woolbox.tool.trigger.ToolTileRefresher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class ToolAccessibilityService : AccessibilityService() {
    private val persistenceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val persistenceMutex = Mutex()
    private val captureRunnable = Runnable {
        if (::copySessionController.isInitialized && copySessionController.isCapturing) {
            val target = windowResolver.resolve()
            if (target?.packageName == copySessionController.packageName) copySessionController.sample(snapshotBuilder.build(target))
        }
    }
    private val mainHandler = Handler(Looper.getMainLooper())
    private val windowResolver = ForegroundWindowResolver(this)
    private val snapshotBuilder = WindowSnapshotBuilder(this)
    private lateinit var copySessionController: CopySessionController
    private lateinit var copyHistoryRepository: CopyHistoryRepository
    private lateinit var oledShieldController: OledShieldController
    private var serviceScope: CoroutineScope? = null
    private var copyHistoryJob: Job? = null
    private var pendingCommand: PendingExternalCommand? = null
    private var isCleanedUp: Boolean = false
    private val refreshTargetRunnable = Runnable(::refreshForegroundTarget)
    private val resolvePendingCommandRunnable = Runnable(::resolvePendingCommand)

    override fun onServiceConnected() {
        super.onServiceConnected()
        isCleanedUp = false
        mainHandler.removeCallbacksAndMessages(null)
        pendingCommand = null
        serviceScope?.cancel()
        serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        if (::copySessionController.isInitialized) copySessionController.close(notify = false)
        if (::oledShieldController.isInitialized) oledShieldController.hide(notify = false)
        val repository = OledShieldConfigRepository(this)
        copyHistoryRepository = CopyHistoryRepository(CopyHistoryDatabase.getInstance(this))
        copySessionController = CopySessionController(this, { id, state ->
            persistenceScope.launch {
                persistenceMutex.withLock {
                    try { withContext(Dispatchers.IO) { copyHistoryRepository.updateSession(id, state) } }
                    catch (e: Exception) {
                        com.wswensw.woolbox.log.ToolLogs.record(this@ToolAccessibilityService, "copy", id.toString(), "history_error", e.toString())
                        withContext(Dispatchers.Main) { Toast.makeText(this@ToolAccessibilityService, com.wswensw.woolbox.R.string.copy_history_save_failed, Toast.LENGTH_LONG).show() }
                    }
                }
            }
        }, ::onCopySessionChanged)
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
        if (::copySessionController.isInitialized && copySessionController.isCapturing &&
            event.packageName?.toString() == copySessionController.packageName &&
            event.eventType in setOf(AccessibilityEvent.TYPE_VIEW_SCROLLED, AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED)) {
            copySessionController.captureEvent(event.eventType, event.fromIndex, event.toIndex, event.scrollY)
            mainHandler.removeCallbacks(captureRunnable)
            mainHandler.postDelayed(captureRunnable, 500L)
        }
        if (event.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
            scheduleTargetRefresh()
            return
        }

        val sourcePackageName = event.packageName?.toString()
        when (sourcePackageName) {
            packageName -> {
                if (windowResolver.isOwnApplicationForeground()) {
                    cancelPendingCommand()
                    closeAllTools("own_app_foreground")
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
        closeAllTools("service_disconnected")
        serviceScope?.cancel()
        serviceScope = null
        ToolServiceController.detach(this)
        ToolServiceStateStore.update {
            ToolServiceState(
                isAccessibilityEnabled = AccessibilityServiceMonitor.isEnabled(this),
            )
        }
        refreshTiles()
    }

    fun executeCommand(command: ExternalToolCommand) {
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
                deadlineAtMillis = SystemClock.uptimeMillis() + command.timeoutMillis(),
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
        if (target == null || !pending.command.acceptsTarget(target.packageName)) {
            schedulePendingResolution(pending)
            return
        }
        pendingCommand = null
        ToolServiceStateStore.update { it.copy(targetPackageName = target.packageName) }
        when (pending.command) {
            ExternalToolCommand.GLOBAL_COPY -> startCopySession(target)
            ExternalToolCommand.OLED_SHIELD -> oledShieldController.show(target.packageName)
            is ExternalToolCommand.OledShieldProfile -> {
                oledShieldController.show(target.packageName, pending.command.config)
            }
        }
    }

    private fun schedulePendingResolution(pending: PendingExternalCommand) {
        if (SystemClock.uptimeMillis() >= pending.deadlineAtMillis) {
            com.wswensw.woolbox.log.ToolLogs.record(this, if (pending.command == ExternalToolCommand.GLOBAL_COPY) "copy" else "oled", "", "launch_timeout", pending.command.toString())
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
            com.wswensw.woolbox.log.ToolLogs.record(this, "copy", "", "empty", target.packageName)
            ToolServiceStateStore.update { it.copy(activeTool = ToolActivity.NONE) }
            refreshTiles()
            return
        }
        copyHistoryJob?.cancel()
        copyHistoryJob = requireNotNull(serviceScope).launch {
            val saved = try {
                withContext(Dispatchers.IO) {
                    copyHistoryRepository.insertSnapshot(snapshot)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                com.wswensw.woolbox.log.ToolLogs.record(this@ToolAccessibilityService, "copy", "", "history_error", error.toString())
                null
            }
            copyHistoryJob = null
            if (saved == null) {
                ToolServiceStateStore.update { it.copy(activeTool = ToolActivity.NONE) }
                Toast.makeText(
                    this@ToolAccessibilityService,
                    getString(com.wswensw.woolbox.R.string.copy_history_save_failed),
                    Toast.LENGTH_SHORT,
                ).show()
                refreshTiles()
                return@launch
            }

            val state = ToolServiceStateStore.state.value
            if (
                !isCleanedUp &&
                state.activeTool == ToolActivity.COPY_PREPARING &&
                state.targetPackageName == target.packageName
            ) {
                copySessionController.show(snapshot, saved)
            } else if (state.activeTool == ToolActivity.COPY_PREPARING) {
                ToolServiceStateStore.update { it.copy(activeTool = ToolActivity.NONE) }
                refreshTiles()
            }
        }
    }

    private fun refreshForegroundTarget() {
        if (windowResolver.isOwnApplicationForeground()) {
            cancelPendingCommand()
            closeAllTools("target_unavailable")
            ToolServiceStateStore.update { it.copy(targetPackageName = null) }
            return
        }
        if (pendingCommand != null || windowResolver.isSystemUiForeground()) return

        val target = windowResolver.resolve()
        val currentState = ToolServiceStateStore.state.value
        if (target == null) {
            if (currentState.activeTool.isActive()) closeAllTools("target_unavailable")
            ToolServiceStateStore.update { it.copy(targetPackageName = null) }
            return
        }

        if (
            currentState.activeTool.isActive() &&
            currentState.targetPackageName != target.packageName
        ) {
            closeAllTools("target_changed")
        }
        ToolServiceStateStore.update { it.copy(targetPackageName = target.packageName) }
    }

    private fun closeAllTools(reason: String = "tool_switch") {
        mainHandler.removeCallbacks(captureRunnable)
        val hadActiveTool = ToolServiceStateStore.state.value.activeTool != ToolActivity.NONE
        copyHistoryJob?.cancel()
        copyHistoryJob = null
        if (::copySessionController.isInitialized) copySessionController.close(notify = false, reason = reason)
        if (::oledShieldController.isInitialized) oledShieldController.hide(notify = false, reason = reason)
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

    private fun ToolActivity.isActive(): Boolean = this == ToolActivity.COPY_ACTIVE || this == ToolActivity.OLED_ACTIVE

    private companion object {
        const val SYSTEM_UI_PACKAGE = "com.android.systemui"
        const val TARGET_REFRESH_DELAY_MS = 80L
        const val COMMAND_POLL_MILLIS = 120L
    }
}
