package com.wswensw.woolbox.ui

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.wswensw.woolbox.R
import com.wswensw.woolbox.record.ui.ManageTasksScreen
import com.wswensw.woolbox.record.ui.ManageRecordsScreen
import com.wswensw.woolbox.record.ui.MeasurementPreviewUiState
import com.wswensw.woolbox.record.model.RecordItem
import com.wswensw.woolbox.record.model.RecordMode
import com.wswensw.woolbox.record.model.TodoStep
import com.wswensw.woolbox.record.model.TodoTask
import com.wswensw.woolbox.record.ui.AccumulateItemFormScreen
import com.wswensw.woolbox.record.ui.MeasurementItemFormScreen
import com.wswensw.woolbox.record.ui.MeasurementPreviewSheet
import com.wswensw.woolbox.record.ui.MotionMode
import com.wswensw.woolbox.record.ui.RecordSheet
import com.wswensw.woolbox.record.model.dayRing
import com.wswensw.woolbox.record.ui.crossedGoalThreshold
import com.wswensw.woolbox.record.ui.displayRecordName
import com.wswensw.woolbox.record.ui.RecordsConnectionScreen
import com.wswensw.woolbox.record.ui.RecordsScreen
import com.wswensw.woolbox.record.ui.RecordsViewModel
import com.wswensw.woolbox.record.ui.TaskCreateScreen
import com.wswensw.woolbox.record.ui.TaskDetailScreen
import com.wswensw.woolbox.record.ui.TaskProgressSheet
import com.wswensw.woolbox.tool.service.ExternalToolCommand
import com.wswensw.woolbox.tool.service.ToolLaunchResult
import com.wswensw.woolbox.tool.overlay.InAppOledShield
import com.wswensw.woolbox.tool.trigger.TileRequestManager
import com.wswensw.woolbox.tool.trigger.TileRequestResult
import com.wswensw.woolbox.tool.trigger.ToolTile
import com.wswensw.woolbox.tool.ui.CopyHistoryEditorScreen
import com.wswensw.woolbox.tool.ui.CopyHistoryScreen
import com.wswensw.woolbox.tool.ui.CopyHistoryViewModel
import com.wswensw.woolbox.tool.ui.ToolViewModel
import com.wswensw.woolbox.update.AppUpdateManager
import java.util.Locale
import kotlinx.coroutines.launch

private enum class MainTab(
    val labelRes: Int,
    val iconRes: Int,
) {
    RECORDS(R.string.tab_records, R.drawable.ic_records_24),
    TOOLS(R.string.tab_tools, R.drawable.ic_tools_24),
    SETTING(R.string.tab_setting, R.drawable.ic_settings_24),
}

@Composable
fun WoolboxApp(onLaunchTool: (ExternalToolCommand) -> ToolLaunchResult) {
    val context = LocalContext.current
    val taskDoneTemplate = stringResource(R.string.task_done)
    val goalReachedTemplate = stringResource(R.string.goal_reached)
    val recordedItemTemplate = stringResource(R.string.recorded_item)
    val undoLabel = stringResource(R.string.action_undo)
    val accessibilityAlreadyEnabledMessage = stringResource(R.string.accessibility_already_enabled)
    val copyHistorySavedMessage = stringResource(R.string.copy_history_saved)
    val copyHistoryDeletedMessage = stringResource(R.string.copy_history_deleted)
    val savedAppDeletedMessage = stringResource(R.string.saved_app_deleted)
    val navController = rememberNavController()
    val toolViewModel: ToolViewModel = viewModel()
    val copyHistoryViewModel: CopyHistoryViewModel = viewModel()
    val recordsViewModel: RecordsViewModel = viewModel(
        factory = RecordsViewModel.Factory(LocalContext.current),
    )
    val toolState by toolViewModel.serviceState.collectAsStateWithLifecycle()
    val defaultOledConfig by toolViewModel.defaultOledConfig.collectAsStateWithLifecycle()
    val oledProfiles by toolViewModel.oledProfiles.collectAsStateWithLifecycle()
    val copyHistory by copyHistoryViewModel.history.collectAsStateWithLifecycle()
    val selectedCopyHistory by copyHistoryViewModel.selectedHistory.collectAsStateWithLifecycle()
    val recordsState by recordsViewModel.uiState.collectAsStateWithLifecycle()
    val recordsToday by recordsViewModel.today.collectAsStateWithLifecycle()
    val rootTaskPreviewLimit by recordsViewModel.rootTaskPreviewLimit.collectAsStateWithLifecycle()
    val measurementPreviewState by recordsViewModel.measurementPreview.collectAsStateWithLifecycle()
    val updateState by AppUpdateManager.state.collectAsStateWithLifecycle()
    var selectedTab by rememberSaveable { mutableStateOf(MainTab.RECORDS) }
    var languageMode by rememberSaveable { mutableStateOf(LanguageMode.current()) }
    var themeMode by rememberSaveable { mutableStateOf(ThemeMode.SYSTEM) }
    var motionMode by rememberSaveable { mutableStateOf(MotionMode.SYSTEM) }
    var recordSheetItemId by rememberSaveable { mutableStateOf<Long?>(null) }
    var taskSheetId by rememberSaveable { mutableStateOf<Long?>(null) }
    var measurementSheetItemId by rememberSaveable { mutableStateOf<Long?>(null) }
    var showInAppOledShield by rememberSaveable { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val hapticFeedback = LocalHapticFeedback.current
    val darkTheme = themeMode.resolvedDarkTheme(isSystemInDarkTheme())
    val lifecycleOwner = LocalLifecycleOwner.current
    val recordsClock = recordsViewModel.clock.collectAsStateWithLifecycle()

    // Shared by the task progress Sheet and the focus card so completing a
    // subtask gives the same haptics and parent-done Snackbar either way.
    val completeStep: (TodoTask, TodoStep, (String?) -> Unit) -> Unit = { task, step, onResult ->
        val parentCompleting = task.steps.all { it.id == step.id || it.isCompleted }
        recordsViewModel.setStepCompleted(step.id, true) { error ->
            onResult(error)
            if (error == null) {
                hapticFeedback.performHapticFeedback(
                    if (parentCompleting) {
                        HapticFeedbackType.LongPress
                    } else {
                        HapticFeedbackType.ContextClick
                    },
                )
                if (parentCompleting) {
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar(
                            message = taskDoneTemplate.formatLocalized(task.title),
                            duration = SnackbarDuration.Short,
                        )
                    }
                }
            }
        }
    }

    // Shared by the record Sheet and the focus card so both produce the same
    // haptics, goal detection and undo.
    val recordValue: (RecordItem, Double, (String?) -> Unit) -> Unit = { item, value, onResult ->
        val previous = recordsState.goalValues[item.id]
            ?: recordsState.entries.filter { it.itemId == item.id }.sumOf { it.value }
        recordsViewModel.createEntry(item, value) { entryId, error ->
            onResult(error)
            if (entryId != null) {
                val goalJustReached = crossedGoalThreshold(
                    previous = previous,
                    current = previous + value,
                    target = item.goalValue,
                )
                hapticFeedback.performHapticFeedback(
                    if (goalJustReached) {
                        HapticFeedbackType.LongPress
                    } else {
                        HapticFeedbackType.ContextClick
                    },
                )
                recordSheetItemId = null
                coroutineScope.launch {
                    val label = displayRecordName(item.name)
                    val result = snackbarHostState.showSnackbar(
                        message = if (goalJustReached) {
                            goalReachedTemplate.formatLocalized(label)
                        } else {
                            recordedItemTemplate.formatLocalized(label)
                        },
                        actionLabel = undoLabel,
                        duration = SnackbarDuration.Short,
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        recordsViewModel.deleteEntry(entryId)
                    }
                }
            }
        }
    }

    // The moment today's last commitment closes, announced once. Closing the
    // ring is the day's own result, so it does not wait for a screen to notice.
    val dayRing = dayRing(
        items = recordsState.activeItems,
        tasks = recordsState.tasks,
        goalValues = recordsState.goalValues,
        today = recordsToday,
    )
    val dayClosedMessage = pluralStringResource(
        R.plurals.day_closed,
        dayRing.total,
        dayRing.total,
    )
    // Starts from whatever is already true, so opening the app on an
    // already-closed day says nothing; only the crossing is worth announcing.
    var ringWasComplete by remember { mutableStateOf(dayRing.isComplete) }
    LaunchedEffect(dayRing.isComplete) {
        val justClosed = dayRing.isComplete && !ringWasComplete
        ringWasComplete = dayRing.isComplete
        if (!justClosed) return@LaunchedEffect
        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
        snackbarHostState.showSnackbar(
            message = dayClosedMessage,
            duration = SnackbarDuration.Short,
        )
    }

    LaunchedEffect(selectedTab) {
        if (selectedTab == MainTab.SETTING) AppUpdateManager.checkForUpdate()
    }

    DisposableEffect(lifecycleOwner, toolViewModel, recordsViewModel, selectedTab) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                toolViewModel.refreshAccessibilityState()
                AppUpdateManager.syncInstallPermission()
                if (selectedTab == MainTab.RECORDS) recordsViewModel.refresh()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    AppSystemBars(darkTheme = darkTheme)

    AppTheme(
        themeMode = themeMode,
        darkTheme = darkTheme,
        motionMode = motionMode,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            NavHost(
                navController = navController,
                startDestination = AppRoutes.ROOT,
                modifier = Modifier.fillMaxSize(),
                enterTransition = { pageEnterTransition() },
                exitTransition = { pageExitTransition() },
                popEnterTransition = { pagePopEnterTransition() },
                popExitTransition = { pagePopExitTransition() },
            ) {
            composable(AppRoutes.ROOT) {
                AppSurface(
                    bottomBar = {
                        BottomNavigationBar(
                            selectedTab = selectedTab,
                            onSelectTab = { tab ->
                                selectedTab = tab
                                if (tab != MainTab.RECORDS) {
                                    recordSheetItemId = null
                                    taskSheetId = null
                                    measurementSheetItemId = null
                                }
                            },
                        )
                    },
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                ) { innerPadding: PaddingValues ->
                    AnimatedContent(
                        targetState = selectedTab,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        transitionSpec = { tabContentTransition() },
                        label = "MainTabContent",
                    ) { tab ->
                        when (tab) {
                            MainTab.TOOLS -> ToolsScreen(
                                state = toolState,
                                modifier = Modifier.fillMaxSize(),
                                onOpenGlobalCopy = { navController.navigate(AppRoutes.GLOBAL_COPY) },
                                onOpenOledShield = { navController.navigate(AppRoutes.OLED_SHIELD) },
                                onOpenAccessibilitySettings = toolViewModel::openAccessibilitySettings,
                                onAccessibilityAlreadyEnabled = {
                                    context.showMessage(accessibilityAlreadyEnabledMessage)
                                },
                            )

                            MainTab.RECORDS -> RecordsScreen(
                                state = recordsState,
                                clock = recordsClock,
                                today = recordsToday,
                                rootTaskPreviewLimit = rootTaskPreviewLimit,
                                onManageTasks = {
                                    taskSheetId = null
                                    navController.navigate(AppRoutes.MANAGE_TASKS)
                                },
                                onManageAccumulates = {
                                    recordSheetItemId = null
                                    navController.navigate(AppRoutes.MANAGE_ACCUMULATES)
                                },
                                onManageMeasurements = {
                                    measurementSheetItemId = null
                                    navController.navigate(AppRoutes.MANAGE_MEASUREMENTS)
                                },
                                onConfigureConnection = {
                                    navController.navigate(AppRoutes.RECORDS_CONNECTION)
                                },
                                onRetry = recordsViewModel::refresh,
                                onRecordItem = { recordSheetItemId = it.id },
                                onQuickRecord = recordValue,
                                onCompleteStep = completeStep,
                                onDeferTarget = recordsViewModel::deferTarget,
                                onOpenMeasurement = { item ->
                                    measurementSheetItemId = item.id
                                    recordsViewModel.loadMeasurementPreview(item.id)
                                },
                                onOpenTask = { task ->
                                    taskSheetId = task.id
                                },
                                modifier = Modifier.fillMaxSize(),
                            )

                            MainTab.SETTING -> SettingScreen(
                                languageMode = languageMode,
                                onLanguageModeChange = {
                                    languageMode = it
                                    it.apply()
                                },
                                themeMode = themeMode,
                                onThemeModeChange = { themeMode = it },
                                motionMode = motionMode,
                                onMotionModeChange = { motionMode = it },
                                recordsConnection = recordsState.connection.username
                                    .takeIf { recordsState.connection.isConfigured }
                                    ?: stringResource(R.string.not_configured),
                                onOpenRecordsConnection = {
                                    navController.navigate(AppRoutes.RECORDS_CONNECTION)
                                },
                                rootTaskPreviewLimit = rootTaskPreviewLimit,
                                onRootTaskPreviewLimitChange = recordsViewModel::setRootTaskPreviewLimit,
                                updateState = updateState,
                                onCheckForUpdate = AppUpdateManager::checkForUpdate,
                                onDownloadAndInstall = AppUpdateManager::downloadAndInstall,
                                onOpenInstallSettings = AppUpdateManager::openInstallSettings,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }
            }
            composable(AppRoutes.GLOBAL_COPY) {
                val context = LocalContext.current
                GlobalCopyRoute(
                    state = toolState,
                    historyCount = copyHistory.size,
                    onBack = { navController.popBackStack() },
                    onLaunchTool = {
                        context.showToolLaunchResult(onLaunchTool(ExternalToolCommand.GLOBAL_COPY))
                    },
                    onOpenHistory = { navController.navigate(AppRoutes.COPY_HISTORY) },
                    tileRequestAvailable = TileRequestManager.isRequestSupported,
                    onRequestTile = {
                        TileRequestManager.request(
                            context = context,
                            tile = ToolTile.GLOBAL_COPY,
                        ) { result ->
                            context.showTileRequestResult(result)
                        }
                    },
                )
            }
            composable(AppRoutes.COPY_HISTORY) {
                CopyHistoryScreen(
                    history = copyHistory,
                    onBack = { navController.popBackStack() },
                    onOpen = { historyId ->
                        navController.navigate(AppRoutes.copyHistoryEdit(historyId))
                    },
                )
            }
            composable(
                route = AppRoutes.COPY_HISTORY_EDIT,
                arguments = listOf(
                    navArgument(AppRoutes.COPY_HISTORY_ID) { type = NavType.LongType },
                ),
            ) { entry ->
                val context = LocalContext.current
                val historyId = entry.arguments?.getLong(AppRoutes.COPY_HISTORY_ID)
                    ?: return@composable
                DisposableEffect(historyId) {
                    onDispose { copyHistoryViewModel.closeEditor() }
                }
                CopyHistoryEditorScreen(
                    historyId = historyId,
                    entry = selectedCopyHistory?.takeIf { it.id == historyId },
                    onLoad = { id, onResult ->
                        copyHistoryViewModel.open(id, onResult)
                    },
                    onClose = {
                        copyHistoryViewModel.closeEditor()
                        navController.popBackStack()
                    },
                    onSave = { id, state, onResult ->
                        copyHistoryViewModel.save(id, state) { error ->
                            if (error == null) {
                                context.showMessage(copyHistorySavedMessage)
                            }
                            onResult(error)
                        }
                    },
                    onCopy = { text ->
                        context.copyToClipboard(text)
                        copyHistoryViewModel.closeEditor()
                        navController.popBackStack()
                    },
                    onDelete = { id, onResult ->
                        copyHistoryViewModel.delete(id) { error ->
                            if (error == null) {
                                context.showMessage(copyHistoryDeletedMessage)
                            }
                            onResult(error)
                        }
                    },
                    onError = context::showMessage,
                )
            }
            composable(AppRoutes.OLED_SHIELD) {
                val context = LocalContext.current
                OledShieldRoute(
                    state = toolState,
                    defaultConfig = defaultOledConfig,
                    profiles = oledProfiles,
                    onBack = { navController.popBackStack() },
                    onLaunchTool = {
                        if (toolState.isServiceConnected) {
                            context.showToolLaunchResult(onLaunchTool(ExternalToolCommand.OLED_SHIELD))
                        } else {
                            showInAppOledShield = true
                        }
                    },
                    tileRequestAvailable = TileRequestManager.isRequestSupported,
                    onRequestTile = {
                        TileRequestManager.request(
                            context = context,
                            tile = ToolTile.OLED_SHIELD,
                        ) { result ->
                            context.showTileRequestResult(result)
                        }
                    },
                    onConfigChange = toolViewModel::updateDefaultOledConfig,
                    onSaveDefaultConfig = toolViewModel::saveDefaultOledConfig,
                    onEnableProfile = { profile ->
                        context.showToolLaunchResult(
                            onLaunchTool(
                                ExternalToolCommand.OledShieldProfile(
                                    packageName = profile.profile.packageName,
                                    config = profile.profile.config,
                                ),
                            ),
                        )
                    },
                    onDeleteProfile = { packageName ->
                        toolViewModel.deleteOledProfile(packageName)
                        context.showMessage(savedAppDeletedMessage)
                    },
                )
            }
            composable(AppRoutes.MANAGE_TASKS) {
                ManageTasksScreen(
                    state = recordsState,
                    today = recordsToday,
                    onBack = { navController.popBackStack() },
                    onCreate = { navController.navigate(AppRoutes.TASK_NEW) },
                    onEdit = { taskId -> navController.navigate(AppRoutes.taskDetail(taskId)) },
                )
            }
            composable(AppRoutes.MANAGE_ACCUMULATES) {
                ManageRecordsScreen(
                    state = recordsState,
                    mode = RecordMode.ACCUMULATE,
                    onBack = { navController.popBackStack() },
                    onNew = {
                        navController.navigate(AppRoutes.RECORD_ITEM_ACCUMULATE_NEW)
                    },
                    onEditItem = { itemId ->
                        navController.navigate(AppRoutes.accumulateItemEdit(itemId))
                    },
                )
            }
            composable(AppRoutes.MANAGE_MEASUREMENTS) {
                ManageRecordsScreen(
                    state = recordsState,
                    mode = RecordMode.MEASUREMENT,
                    onBack = { navController.popBackStack() },
                    onNew = {
                        navController.navigate(AppRoutes.RECORD_ITEM_MEASUREMENT_NEW)
                    },
                    onEditItem = { itemId ->
                        navController.navigate(AppRoutes.measurementItemEdit(itemId))
                    },
                )
            }
            composable(AppRoutes.RECORDS_CONNECTION) {
                RecordsConnectionScreen(
                    state = recordsState,
                    onBack = { navController.popBackStack() },
                    onSave = recordsViewModel::configureCredentials,
                )
            }
            composable(AppRoutes.TASK_NEW) {
                TaskCreateScreen(
                    today = recordsToday,
                    onBack = { navController.popBackStack() },
                    onCreate = { title, steps, plannedDate, onResult ->
                        recordsViewModel.createTask(title, steps, plannedDate) { taskId, error ->
                            onResult(error)
                            if (taskId != null) navController.popBackStack()
                        }
                    },
                )
            }
            composable(
                route = AppRoutes.TASK_DETAIL,
                arguments = listOf(navArgument(AppRoutes.TASK_ID) { type = NavType.LongType }),
            ) { entry ->
                val taskId = entry.arguments?.getLong(AppRoutes.TASK_ID) ?: return@composable
                TaskDetailScreen(
                    task = recordsState.findTask(taskId),
                    loading = recordsState.isLoading,
                    today = recordsToday,
                    onBack = { navController.popBackStack() },
                    onSave = { title, plannedDate, onResult ->
                        recordsViewModel.updateTask(taskId, title, plannedDate, onResult)
                    },
                    onCreateStep = { title, windows, onResult ->
                        recordsViewModel.createStep(taskId, title, windows, onResult)
                    },
                    onUpdateStep = { step, title, windows, onResult ->
                        recordsViewModel.updateStep(step.id, title, windows, onResult)
                    },
                    onToggleStep = { step, completed ->
                        recordsViewModel.setStepCompleted(step.id, completed)
                    },
                    onDeleteStep = { step, onResult ->
                        recordsViewModel.deleteStep(step.id, onResult)
                    },
                    onReorderSteps = { stepIds, onResult ->
                        recordsViewModel.reorderSteps(taskId, stepIds, onResult)
                    },
                    onDeleteTask = { onResult ->
                        recordsViewModel.deleteTask(taskId, onResult)
                    },
                )
            }
            composable(AppRoutes.RECORD_ITEM_ACCUMULATE_NEW) {
                AccumulateItemFormScreen(
                    item = null,
                    editing = false,
                    loading = false,
                    fieldsLocked = false,
                    onBack = { navController.popBackStack() },
                    onSave = { draft, onResult ->
                        recordsViewModel.saveItem(itemId = null, draft = draft) { error ->
                            onResult(error)
                            if (error == null) {
                                navController.popBackStack()
                            }
                        }
                    },
                )
            }
            composable(
                route = AppRoutes.RECORD_ITEM_ACCUMULATE_EDIT,
                arguments = listOf(navArgument(AppRoutes.RECORD_ITEM_ID) { type = NavType.LongType }),
            ) { entry ->
                val itemId = entry.arguments?.getLong(AppRoutes.RECORD_ITEM_ID) ?: return@composable
                AccumulateItemFormScreen(
                    item = recordsState.findItem(itemId),
                    editing = true,
                    loading = recordsState.isLoading,
                    fieldsLocked = true,
                    onBack = { navController.popBackStack() },
                    onSave = { draft, onResult ->
                        recordsViewModel.saveItem(itemId = itemId, draft = draft) { error ->
                            onResult(error)
                            if (error == null) {
                                navController.popBackStack()
                            }
                        }
                    },
                )
            }
            composable(AppRoutes.RECORD_ITEM_MEASUREMENT_NEW) {
                MeasurementItemFormScreen(
                    item = null,
                    editing = false,
                    loading = false,
                    fieldsLocked = false,
                    onBack = { navController.popBackStack() },
                    onSave = { draft, onResult ->
                        recordsViewModel.saveItem(itemId = null, draft = draft) { error ->
                            onResult(error)
                            if (error == null) {
                                navController.popBackStack()
                            }
                        }
                    },
                )
            }
            composable(
                route = AppRoutes.RECORD_ITEM_MEASUREMENT_EDIT,
                arguments = listOf(navArgument(AppRoutes.RECORD_ITEM_ID) { type = NavType.LongType }),
            ) { entry ->
                val itemId = entry.arguments?.getLong(AppRoutes.RECORD_ITEM_ID) ?: return@composable
                MeasurementItemFormScreen(
                    item = recordsState.findItem(itemId),
                    editing = true,
                    loading = recordsState.isLoading,
                    fieldsLocked = true,
                    onBack = { navController.popBackStack() },
                    onSave = { draft, onResult ->
                        recordsViewModel.saveItem(itemId = itemId, draft = draft) { error ->
                            onResult(error)
                            if (error == null) {
                                navController.popBackStack()
                            }
                        }
                    },
                )
            }
            }

            recordSheetItemId?.let { itemId ->
                val item = recordsState.activeItems.firstOrNull { it.id == itemId }
                if (item != null) {
                    RecordSheet(
                        item = item,
                        entries = recordsState.entries.filter { it.itemId == itemId },
                        cumulative = recordsState.goalValues[itemId]
                            ?: recordsState.entries.filter { it.itemId == itemId }.sumOf { it.value },
                        onDismiss = { recordSheetItemId = null },
                        onRecord = recordValue,
                    )
                }
            }

            taskSheetId?.let { taskId ->
                recordsState.findTask(taskId)?.let { task ->
                    TaskProgressSheet(
                        task = task,
                        onDismiss = { taskSheetId = null },
                        onToggleStep = { step, completed, onResult ->
                            val completing = completed && !step.isCompleted
                            val parentCompleting = completing &&
                                task.steps.all { it.id == step.id || it.isCompleted }
                            recordsViewModel.setStepCompleted(step.id, completed) { error ->
                                onResult(error)
                                if (error == null && completing) {
                                    hapticFeedback.performHapticFeedback(
                                        if (parentCompleting) {
                                            HapticFeedbackType.LongPress
                                        } else {
                                            HapticFeedbackType.ContextClick
                                        },
                                    )
                                    if (parentCompleting) {
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar(
                                                message = taskDoneTemplate.formatLocalized(task.title),
                                                duration = SnackbarDuration.Short,
                                            )
                                        }
                                    }
                                }
                            }
                        },
                    )
                }
            }

            measurementSheetItemId?.let { itemId ->
                recordsState.activeItems.firstOrNull { it.id == itemId }?.let { item ->
                    MeasurementPreviewSheet(
                        item = item,
                        state = measurementPreviewState.takeIf { it.itemId == itemId }
                            ?: MeasurementPreviewUiState(itemId = itemId, isLoading = true),
                        onDismiss = { measurementSheetItemId = null },
                        onRecord = { value, onResult ->
                            recordsViewModel.createEntry(item, value) { entryId, error ->
                                onResult(error)
                                if (entryId != null) {
                                    coroutineScope.launch {
                                        val result = snackbarHostState.showSnackbar(
                                            message = recordedItemTemplate.formatLocalized(
                                                displayRecordName(item.name),
                                            ),
                                            actionLabel = undoLabel,
                                            duration = SnackbarDuration.Short,
                                        )
                                        if (result == SnackbarResult.ActionPerformed) {
                                            recordsViewModel.deleteEntry(entryId)
                                        }
                                    }
                                }
                            }
                        },
                    )
                }
            }

            if (showInAppOledShield) {
                InAppOledShield(
                    config = defaultOledConfig,
                    onConfigChange = { updated ->
                        toolViewModel.updateDefaultOledConfig { updated }
                    },
                    onSave = toolViewModel::saveDefaultOledConfig,
                    onHide = { showInAppOledShield = false },
                )
            }
        }
    }
}

@Composable
private fun AppSystemBars(darkTheme: Boolean) {
    val view = LocalView.current
    if (view.isInEditMode) {
        return
    }

    SideEffect {
        val window = view.context.findActivity().window
        val controller = WindowCompat.getInsetsController(window, view)
        controller.isAppearanceLightStatusBars = !darkTheme
        controller.isAppearanceLightNavigationBars = !darkTheme
    }
}

private tailrec fun Context.findActivity(): Activity = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> error("WoolboxApp must be hosted in an Activity context.")
}

private fun Context.showTileRequestResult(result: TileRequestResult) {
    val message = when (result) {
        TileRequestResult.ADDED -> getString(R.string.tile_added)
        TileRequestResult.ALREADY_ADDED -> getString(R.string.tile_already_added)
        TileRequestResult.NOT_ADDED -> getString(R.string.tile_not_added)
        TileRequestResult.ERROR -> getString(R.string.tile_add_failed)
    }
    showMessage(message)
}

private fun Context.showToolLaunchResult(result: ToolLaunchResult) {
    val message = when (result) {
        ToolLaunchResult.Accepted -> return
        ToolLaunchResult.ServiceUnavailable -> getString(R.string.accessibility_not_connected)
        ToolLaunchResult.TargetAppUnavailable -> getString(R.string.target_app_unavailable)
        ToolLaunchResult.TargetLaunchFailed -> getString(R.string.target_app_open_failed)
    }
    showMessage(message)
}

private fun Context.copyToClipboard(text: String) {
    check(text.isNotBlank()) { "Copy history text must not be blank." }
    getSystemService(ClipboardManager::class.java).setPrimaryClip(
        ClipData.newPlainText("Global Copy", text),
    )
    showMessage(getString(R.string.copied_to_clipboard))
}

private fun Context.showMessage(message: String) {
    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}

private fun String.formatLocalized(vararg args: Any): String =
    String.format(Locale.getDefault(), this, *args)

@Composable
private fun BottomNavigationBar(
    selectedTab: MainTab,
    onSelectTab: (MainTab) -> Unit,
) {
    NavigationBar {
        MainTab.entries.forEach { tab ->
            NavigationBarItem(
                selected = selectedTab == tab,
                onClick = { onSelectTab(tab) },
                icon = {
                    Icon(
                        painter = painterResource(tab.iconRes),
                        contentDescription = null,
                    )
                },
                label = { Text(stringResource(tab.labelRes)) },
            )
        }
    }
}

private object AppRoutes {
    const val ROOT = "root"
    const val GLOBAL_COPY = "global-copy"
    const val COPY_HISTORY = "global-copy/history"
    const val COPY_HISTORY_ID = "historyId"
    const val COPY_HISTORY_EDIT = "global-copy/history/{$COPY_HISTORY_ID}"
    const val OLED_SHIELD = "oled-shield"

    fun copyHistoryEdit(historyId: Long) = "global-copy/history/$historyId"
    const val MANAGE_TASKS = "manage-tasks"
    const val MANAGE_ACCUMULATES = "manage-accumulates"
    const val MANAGE_MEASUREMENTS = "manage-measurements"
    const val RECORDS_CONNECTION = "records-connection"
    const val TASK_NEW = "task/new"
    const val TASK_ID = "taskId"
    const val TASK_DETAIL = "task/{$TASK_ID}"
    const val RECORD_ITEM_ID = "recordItemId"
    const val RECORD_ITEM_ACCUMULATE_NEW = "record-item/accumulate/new"
    const val RECORD_ITEM_ACCUMULATE_EDIT = "record-item/accumulate/{$RECORD_ITEM_ID}"
    const val RECORD_ITEM_MEASUREMENT_NEW = "record-item/measurement/new"
    const val RECORD_ITEM_MEASUREMENT_EDIT = "record-item/measurement/{$RECORD_ITEM_ID}"

    fun accumulateItemNew() = RECORD_ITEM_ACCUMULATE_NEW
    fun accumulateItemEdit(itemId: Long) = "record-item/accumulate/$itemId"
    fun measurementItemNew() = RECORD_ITEM_MEASUREMENT_NEW
    fun measurementItemEdit(itemId: Long) = "record-item/measurement/$itemId"
    fun taskDetail(taskId: Long) = "task/$taskId"
}

private const val PageTransitionMillis = 220
private const val TabTransitionMillis = 220

private fun AnimatedContentTransitionScope<MainTab>.tabContentTransition() =
    if (targetState.ordinal > initialState.ordinal) {
        slideIntoContainer(
            towards = AnimatedContentTransitionScope.SlideDirection.Left,
            animationSpec = tween(TabTransitionMillis),
        ) togetherWith slideOutOfContainer(
            towards = AnimatedContentTransitionScope.SlideDirection.Left,
            animationSpec = tween(TabTransitionMillis),
        )
    } else {
        slideIntoContainer(
            towards = AnimatedContentTransitionScope.SlideDirection.Right,
            animationSpec = tween(TabTransitionMillis),
        ) togetherWith slideOutOfContainer(
            towards = AnimatedContentTransitionScope.SlideDirection.Right,
            animationSpec = tween(TabTransitionMillis),
        )
    }

private fun AnimatedContentTransitionScope<NavBackStackEntry>.pageEnterTransition(): EnterTransition =
    slideIntoContainer(
        towards = AnimatedContentTransitionScope.SlideDirection.Left,
        animationSpec = tween(PageTransitionMillis),
    )

private fun AnimatedContentTransitionScope<NavBackStackEntry>.pageExitTransition(): ExitTransition =
    slideOutOfContainer(
        towards = AnimatedContentTransitionScope.SlideDirection.Left,
        animationSpec = tween(PageTransitionMillis),
    )

private fun AnimatedContentTransitionScope<NavBackStackEntry>.pagePopEnterTransition(): EnterTransition =
    slideIntoContainer(
        towards = AnimatedContentTransitionScope.SlideDirection.Right,
        animationSpec = tween(PageTransitionMillis),
    )

private fun AnimatedContentTransitionScope<NavBackStackEntry>.pagePopExitTransition(): ExitTransition =
    slideOutOfContainer(
        towards = AnimatedContentTransitionScope.SlideDirection.Right,
        animationSpec = tween(PageTransitionMillis),
    )
