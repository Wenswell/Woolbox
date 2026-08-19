package com.wswensw.woolbox.ui

import android.app.Activity
import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.res.stringResource
import androidx.core.view.WindowCompat
import androidx.core.content.ContextCompat
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
import com.wswensw.woolbox.record.ui.ManageTemplatesScreen
import com.wswensw.woolbox.record.ui.ManageGoalsScreen
import com.wswensw.woolbox.record.ui.ManageRecordsScreen
import com.wswensw.woolbox.record.ui.MeasurementPreviewUiState
import com.wswensw.woolbox.record.model.RecordItem
import com.wswensw.woolbox.record.model.RecordMode
import com.wswensw.woolbox.record.model.DailyFocusState
import com.wswensw.woolbox.record.ui.AccumulateItemFormScreen
import com.wswensw.woolbox.record.ui.AddTodayTaskSheet
import com.wswensw.woolbox.record.ui.DailyFocusScreen
import com.wswensw.woolbox.record.ui.GoalEditorScreen
import com.wswensw.woolbox.record.ui.GoalPoolSheet
import com.wswensw.woolbox.record.ui.MeasurementItemFormScreen
import com.wswensw.woolbox.record.ui.MeasurementPreviewSheet
import com.wswensw.woolbox.record.ui.MotionMode
import com.wswensw.woolbox.record.ui.RecordSheet
import com.wswensw.woolbox.record.ui.crossedGoalThreshold
import com.wswensw.woolbox.record.ui.displayRecordName
import com.wswensw.woolbox.record.ui.RecordsConnectionScreen
import com.wswensw.woolbox.record.ui.RecordsScreen
import com.wswensw.woolbox.record.ui.RecordsViewModel
import com.wswensw.woolbox.record.ui.TemplateEditorScreen
import com.wswensw.woolbox.record.ui.TodayTaskProgressSheet
import com.wswensw.woolbox.record.reminder.DailyReminderNotifications
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
fun WoolboxApp(
    dailyReviewRequest: Int,
    onLaunchTool: (ExternalToolCommand) -> ToolLaunchResult,
) {
    val context = LocalContext.current
    val taskDoneTemplate = stringResource(R.string.task_done)
    val goalReachedTemplate = stringResource(R.string.goal_reached)
    val recordedItemTemplate = stringResource(R.string.recorded_item)
    val undoLabel = stringResource(R.string.action_undo)
    val accessibilityAlreadyEnabledMessage = stringResource(R.string.accessibility_already_enabled)
    val batteryOptimizationAlreadyIgnoredMessage = stringResource(
        R.string.battery_optimization_already_ignored,
    )
    val copyHistorySavedMessage = stringResource(R.string.copy_history_saved)
    val copyHistoryDeletedMessage = stringResource(R.string.copy_history_deleted)
    val savedAppDeletedMessage = stringResource(R.string.saved_app_deleted)
    val notificationsNotEnabledMessage = stringResource(R.string.notifications_not_enabled)
    val navController = rememberNavController()
    val toolViewModel: ToolViewModel = viewModel()
    val copyHistoryViewModel: CopyHistoryViewModel = viewModel()
    val recordsViewModel: RecordsViewModel = viewModel(
        factory = RecordsViewModel.Factory(LocalContext.current),
    )
    val toolState by toolViewModel.serviceState.collectAsStateWithLifecycle()
    val batteryOptimizationIgnored by toolViewModel.batteryOptimizationIgnored.collectAsStateWithLifecycle()
    val defaultOledConfig by toolViewModel.defaultOledConfig.collectAsStateWithLifecycle()
    val oledProfiles by toolViewModel.oledProfiles.collectAsStateWithLifecycle()
    val copyHistory by copyHistoryViewModel.history.collectAsStateWithLifecycle()
    val selectedCopyHistory by copyHistoryViewModel.selectedHistory.collectAsStateWithLifecycle()
    val recordsState by recordsViewModel.uiState.collectAsStateWithLifecycle()
    val measurementPreviewState by recordsViewModel.measurementPreview.collectAsStateWithLifecycle()
    val updateState by AppUpdateManager.state.collectAsStateWithLifecycle()
    var selectedTab by rememberSaveable { mutableStateOf(MainTab.RECORDS) }
    var languageMode by rememberSaveable { mutableStateOf(LanguageMode.current()) }
    var themeMode by rememberSaveable { mutableStateOf(ThemeMode.SYSTEM) }
    var motionMode by rememberSaveable { mutableStateOf(MotionMode.SYSTEM) }
    var recordSheetItemId by rememberSaveable { mutableStateOf<Long?>(null) }
    var goalSheetId by rememberSaveable { mutableStateOf<Long?>(null) }
    var todayTaskSheetId by rememberSaveable { mutableStateOf<Long?>(null) }
    var showAddTodayTask by rememberSaveable { mutableStateOf(false) }
    var measurementSheetItemId by rememberSaveable { mutableStateOf<Long?>(null) }
    var showInAppOledShield by rememberSaveable { mutableStateOf(false) }
    var notificationsEnabled by remember {
        mutableStateOf(DailyReminderNotifications.areEnabled(context))
    }
    var notificationPermissionDenied by rememberSaveable { mutableStateOf(false) }
    var pendingNotificationAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var pendingNotificationFailure by remember { mutableStateOf<(() -> Unit)?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val hapticFeedback = LocalHapticFeedback.current
    val darkTheme = themeMode.resolvedDarkTheme(isSystemInDarkTheme())
    val lifecycleOwner = LocalLifecycleOwner.current
    val recordsClock = recordsViewModel.clock.collectAsStateWithLifecycle()
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        notificationPermissionDenied = !granted
        notificationsEnabled = DailyReminderNotifications.areEnabled(context)
        val action = pendingNotificationAction
        val failure = pendingNotificationFailure
        pendingNotificationAction = null
        pendingNotificationFailure = null
        if (notificationsEnabled) action?.invoke() else failure?.invoke()
    }
    val requireNotifications: (onUnavailable: () -> Unit, action: () -> Unit) -> Unit =
        { onUnavailable, action ->
            notificationsEnabled = DailyReminderNotifications.areEnabled(context)
            when {
                notificationsEnabled -> action()
                notificationPermissionDenied -> {
                    onUnavailable()
                    DailyReminderNotifications.openSettings(context)
                }
                Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) != PackageManager.PERMISSION_GRANTED -> {
                    pendingNotificationAction = action
                    pendingNotificationFailure = onUnavailable
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                else -> {
                    onUnavailable()
                    DailyReminderNotifications.openSettings(context)
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

    LaunchedEffect(selectedTab) {
        if (selectedTab == MainTab.SETTING) AppUpdateManager.checkForUpdate()
    }

    LaunchedEffect(dailyReviewRequest) {
        if (dailyReviewRequest == 0) return@LaunchedEffect
        selectedTab = MainTab.RECORDS
        navController.navigate(AppRoutes.ROOT) {
            popUpTo(AppRoutes.ROOT)
            launchSingleTop = true
        }
    }

    DisposableEffect(lifecycleOwner, toolViewModel, recordsViewModel, selectedTab) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notificationsEnabled = DailyReminderNotifications.areEnabled(context)
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
                                    goalSheetId = null
                                    todayTaskSheetId = null
                                    showAddTodayTask = false
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
                                batteryOptimizationIgnored = batteryOptimizationIgnored,
                                modifier = Modifier.fillMaxSize(),
                                onOpenGlobalCopy = { navController.navigate(AppRoutes.GLOBAL_COPY) },
                                onOpenOledShield = { navController.navigate(AppRoutes.OLED_SHIELD) },
                                onOpenAccessibilitySettings = toolViewModel::openAccessibilitySettings,
                                onAccessibilityAlreadyEnabled = {
                                    context.showMessage(accessibilityAlreadyEnabledMessage)
                                },
                                onOpenBatteryOptimizationSettings = toolViewModel::openBatteryOptimizationSettings,
                                onBatteryOptimizationAlreadyIgnored = {
                                    context.showMessage(batteryOptimizationAlreadyIgnoredMessage)
                                },
                            )

                            MainTab.RECORDS -> RecordsScreen(
                                state = recordsState,
                                clock = recordsClock,
                                onPlanFocus = { navController.navigate(AppRoutes.DAILY_FOCUS) },
                                onAddTask = { showAddTodayTask = true },
                                onManageTemplates = {
                                    todayTaskSheetId = null
                                    navController.navigate(AppRoutes.MANAGE_TEMPLATES)
                                },
                                onManageGoals = {
                                    goalSheetId = null
                                    navController.navigate(AppRoutes.MANAGE_GOALS)
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
                                onOpenGoal = { goalSheetId = it.id },
                                onToggleTask = { task, completed, onResult ->
                                    recordsViewModel.setTodayTaskCompleted(task, completed) { error ->
                                        onResult(error)
                                        if (error == null && completed) {
                                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                            coroutineScope.launch {
                                                snackbarHostState.showSnackbar(
                                                    message = taskDoneTemplate.formatLocalized(task.title),
                                                    duration = SnackbarDuration.Short,
                                                )
                                            }
                                        }
                                    }
                                },
                                onOpenTask = { todayTaskSheetId = it.id },
                                onOpenMeasurement = { item ->
                                    measurementSheetItemId = item.id
                                    recordsViewModel.loadMeasurementPreview(item.id)
                                },
                                notificationsEnabled = notificationsEnabled,
                                onEnableNotifications = {
                                    requireNotifications(
                                        {
                                            coroutineScope.launch {
                                                snackbarHostState.showSnackbar(notificationsNotEnabledMessage)
                                            }
                                        },
                                        {},
                                    )
                                },
                                onStartFocus = { focus, onResult ->
                                    requireNotifications(
                                        { onResult(notificationsNotEnabledMessage) },
                                        { recordsViewModel.startDailyFocus(focus, onResult) },
                                    )
                                },
                                onReviewFocus = recordsViewModel::reviewDailyFocus,
                                onEndFocus = recordsViewModel::endDailyFocus,
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
            composable(AppRoutes.MANAGE_TEMPLATES) {
                ManageTemplatesScreen(
                    state = recordsState,
                    onBack = { navController.popBackStack() },
                    onCreate = { navController.navigate(AppRoutes.TEMPLATE_NEW) },
                    onEdit = { templateId -> navController.navigate(AppRoutes.templateEdit(templateId)) },
                )
            }
            composable(AppRoutes.MANAGE_GOALS) {
                ManageGoalsScreen(
                    state = recordsState,
                    onBack = { navController.popBackStack() },
                    onNew = { navController.navigate(AppRoutes.GOAL_NEW) },
                    onEdit = { goalId -> navController.navigate(AppRoutes.goalEdit(goalId)) },
                    onRetry = { goalId -> navController.navigate(AppRoutes.goalRetry(goalId)) },
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
            composable(AppRoutes.TEMPLATE_NEW) {
                TemplateEditorScreen(
                    template = null,
                    editing = false,
                    loading = false,
                    onBack = { navController.popBackStack() },
                    onSave = { title, cadence, steps, onResult ->
                        recordsViewModel.saveTemplate(null, title, cadence, steps) { templateId, error ->
                            onResult(error)
                            if (templateId != null) navController.popBackStack()
                        }
                    },
                    onArchive = null,
                )
            }
            composable(
                route = AppRoutes.TEMPLATE_EDIT,
                arguments = listOf(navArgument(AppRoutes.TEMPLATE_ID) { type = NavType.LongType }),
            ) { entry ->
                val templateId = entry.arguments?.getLong(AppRoutes.TEMPLATE_ID) ?: return@composable
                TemplateEditorScreen(
                    template = recordsState.findTemplate(templateId),
                    editing = true,
                    loading = recordsState.isLoading,
                    onBack = { navController.popBackStack() },
                    onSave = { title, cadence, steps, onResult ->
                        recordsViewModel.saveTemplate(templateId, title, cadence, steps) { savedId, error ->
                            onResult(error)
                            if (savedId != null) navController.popBackStack()
                        }
                    },
                    onArchive = { onResult -> recordsViewModel.archiveTemplate(templateId, onResult) },
                )
            }
            composable(AppRoutes.DAILY_FOCUS) {
                DailyFocusScreen(
                    focus = recordsState.dailyFocus,
                    tasks = recordsState.todayTasks,
                    loading = recordsState.isLoading,
                    onBack = { navController.popBackStack() },
                    onSave = { firstMove, primaryTaskId, secondaryTaskId, onResult ->
                        val save = {
                            val finish: (String?) -> Unit = { error ->
                                onResult(error)
                                if (error == null) navController.popBackStack()
                            }
                            val focus = recordsState.dailyFocus
                            if (focus?.state == DailyFocusState.PAUSED) {
                                recordsViewModel.resumeDailyFocus(
                                    focus,
                                    primaryTaskId,
                                    secondaryTaskId,
                                    finish,
                                )
                            } else {
                                recordsViewModel.saveDailyFocus(
                                    focus,
                                    firstMove,
                                    primaryTaskId,
                                    secondaryTaskId,
                                    finish,
                                )
                            }
                        }
                        if (recordsState.dailyFocus?.state == DailyFocusState.PAUSED) {
                            requireNotifications(
                                { onResult(notificationsNotEnabledMessage) },
                                save,
                            )
                        } else {
                            save()
                        }
                    },
                )
            }
            composable(AppRoutes.GOAL_NEW) {
                GoalEditorScreen(
                    goal = null,
                    editing = false,
                    loading = false,
                    onBack = { navController.popBackStack() },
                    onSave = { draft, onResult ->
                        recordsViewModel.saveGoal(null, draft) { error ->
                            onResult(error)
                            if (error == null) navController.popBackStack()
                        }
                    },
                )
            }
            composable(
                route = AppRoutes.GOAL_EDIT,
                arguments = listOf(navArgument(AppRoutes.GOAL_ID) { type = NavType.LongType }),
            ) { entry ->
                val goalId = entry.arguments?.getLong(AppRoutes.GOAL_ID) ?: return@composable
                GoalEditorScreen(
                    goal = recordsState.findGoal(goalId),
                    editing = true,
                    loading = recordsState.isLoading,
                    onBack = { navController.popBackStack() },
                    onSave = { draft, onResult ->
                        recordsViewModel.saveGoal(goalId, draft) { error ->
                            onResult(error)
                            if (error == null) navController.popBackStack()
                        }
                    },
                )
            }
            composable(
                route = AppRoutes.GOAL_RETRY,
                arguments = listOf(navArgument(AppRoutes.GOAL_ID) { type = NavType.LongType }),
            ) { entry ->
                val sourceId = entry.arguments?.getLong(AppRoutes.GOAL_ID) ?: return@composable
                GoalEditorScreen(
                    goal = null,
                    source = recordsState.findGoal(sourceId),
                    editing = false,
                    loading = recordsState.isLoading,
                    onBack = { navController.popBackStack() },
                    onSave = { draft, onResult ->
                        recordsViewModel.saveGoal(null, draft) { error ->
                            onResult(error)
                            if (error == null) navController.popBackStack()
                        }
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

            goalSheetId?.let { goalId ->
                recordsState.findGoal(goalId)?.let { goal ->
                    GoalPoolSheet(
                        goal = goal,
                        onDismiss = { goalSheetId = null },
                        onRecord = { value, onResult -> recordsViewModel.createGoalEntry(goal, value, onResult) },
                        onToggleSubgoal = recordsViewModel::setGoalSubgoalCompleted,
                    )
                }
            }

            if (showAddTodayTask) {
                AddTodayTaskSheet(
                    dailySuggestions = recordsState.dailySuggestions,
                    unfinishedTasks = recordsState.unfinishedTasks,
                    templates = recordsState.templates,
                    onDismiss = { showAddTodayTask = false },
                    onAddTemplate = recordsViewModel::createTodayTaskFromTemplate,
                    onContinueTask = recordsViewModel::createTodayTaskFromTask,
                    onCreateTask = recordsViewModel::createTodayTask,
                )
            }

            todayTaskSheetId?.let { taskId ->
                recordsState.todayTasks.firstOrNull { it.id == taskId }?.let { task ->
                    TodayTaskProgressSheet(
                        task = task,
                        onDismiss = { todayTaskSheetId = null },
                        onToggleTask = { completed, onResult ->
                            recordsViewModel.setTodayTaskCompleted(task, completed) { error ->
                                onResult(error)
                                if (error == null && completed) {
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar(
                                            message = taskDoneTemplate.formatLocalized(task.title),
                                            duration = SnackbarDuration.Short,
                                        )
                                    }
                                }
                            }
                        },
                        onToggleStep = { step, completed, onResult ->
                            val completing = completed && !step.isCompleted
                            val taskCompleting = completing &&
                                task.steps.all { it.id == step.id || it.isCompleted }
                            recordsViewModel.setTodayTaskStepCompleted(task, step.id, completed) { error ->
                                onResult(error)
                                if (error == null && completing) {
                                    hapticFeedback.performHapticFeedback(
                                        if (taskCompleting) {
                                            HapticFeedbackType.LongPress
                                        } else {
                                            HapticFeedbackType.ContextClick
                                        },
                                    )
                                    if (taskCompleting) {
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
        ClipData.newPlainText(getString(R.string.global_copy_tile_label), text),
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
    const val MANAGE_TEMPLATES = "manage-templates"
    const val MANAGE_GOALS = "manage-goals"
    const val MANAGE_ACCUMULATES = "manage-accumulates"
    const val MANAGE_MEASUREMENTS = "manage-measurements"
    const val RECORDS_CONNECTION = "records-connection"
    const val TEMPLATE_NEW = "template/new"
    const val DAILY_FOCUS = "records/focus"
    const val GOAL_NEW = "goal/new"
    const val GOAL_ID = "goalId"
    const val GOAL_EDIT = "goal/{$GOAL_ID}"
    const val GOAL_RETRY = "goal/retry/{$GOAL_ID}"
    const val TEMPLATE_ID = "templateId"
    const val TEMPLATE_EDIT = "template/{$TEMPLATE_ID}"
    const val RECORD_ITEM_ID = "recordItemId"
    const val RECORD_ITEM_ACCUMULATE_NEW = "record-item/accumulate/new"
    const val RECORD_ITEM_ACCUMULATE_EDIT = "record-item/accumulate/{$RECORD_ITEM_ID}"
    const val RECORD_ITEM_MEASUREMENT_NEW = "record-item/measurement/new"
    const val RECORD_ITEM_MEASUREMENT_EDIT = "record-item/measurement/{$RECORD_ITEM_ID}"

    fun accumulateItemNew() = RECORD_ITEM_ACCUMULATE_NEW
    fun accumulateItemEdit(itemId: Long) = "record-item/accumulate/$itemId"
    fun measurementItemNew() = RECORD_ITEM_MEASUREMENT_NEW
    fun measurementItemEdit(itemId: Long) = "record-item/measurement/$itemId"
    fun templateEdit(templateId: Long) = "template/$templateId"
    fun goalEdit(goalId: Long) = "goal/$goalId"
    fun goalRetry(goalId: Long) = "goal/retry/$goalId"
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
