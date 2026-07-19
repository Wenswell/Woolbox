package com.wswensw.template.ui

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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
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
import com.wswensw.template.R
import com.wswensw.template.record.ui.ManageRecordsScreen
import com.wswensw.template.record.ui.RecordItemFormScreen
import com.wswensw.template.record.ui.RecordsScreen
import com.wswensw.template.record.ui.RecordsViewModel
import com.wswensw.template.tool.service.ExternalToolCommand
import com.wswensw.template.tool.service.ToolLaunchResult
import com.wswensw.template.tool.trigger.TileRequestManager
import com.wswensw.template.tool.trigger.TileRequestResult
import com.wswensw.template.tool.trigger.ToolTile
import com.wswensw.template.tool.ui.ToolViewModel
import com.wswensw.template.tool.ui.CopyHistoryScreen
import com.wswensw.template.tool.ui.CopyHistoryViewModel
import kotlinx.coroutines.launch

private enum class MainTab(
    val labelRes: Int,
    val iconRes: Int,
) {
    HOME(R.string.tab_home, R.drawable.ic_home_24),
    RECORDS(R.string.tab_records, R.drawable.ic_records_24),
    SETTING(R.string.tab_setting, R.drawable.ic_settings_24),
}

@Composable
fun TemplateApp(onLaunchTool: (ExternalToolCommand) -> ToolLaunchResult) {
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
    var selectedTab by rememberSaveable { mutableStateOf(MainTab.HOME) }
    var themeMode by rememberSaveable { mutableStateOf(TemplateThemeMode.SYSTEM) }
    var showRecordSheet by rememberSaveable { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val darkTheme = themeMode.resolvedDarkTheme(isSystemInDarkTheme())
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner, toolViewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                toolViewModel.refreshAccessibilityState()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    TemplateSystemBars(darkTheme = darkTheme)

    TemplateTheme(
        themeMode = themeMode,
        darkTheme = darkTheme,
    ) {
        NavHost(
            navController = navController,
            startDestination = TemplateRoutes.ROOT,
            enterTransition = { pageEnterTransition() },
            exitTransition = { pageExitTransition() },
            popEnterTransition = { pagePopEnterTransition() },
            popExitTransition = { pagePopExitTransition() },
        ) {
            composable(TemplateRoutes.ROOT) {
                TemplateSurface(
                    bottomBar = {
                        BottomNavigationBar(
                            selectedTab = selectedTab,
                            onSelectTab = { tab ->
                                selectedTab = tab
                                if (tab != MainTab.RECORDS) {
                                    showRecordSheet = false
                                }
                            },
                        )
                    },
                    floatingActionButton = {
                        if (selectedTab == MainTab.RECORDS && recordsState.activeItems.isNotEmpty()) {
                            ExtendedFloatingActionButton(
                                onClick = { showRecordSheet = true },
                                icon = {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_add_24),
                                        contentDescription = null,
                                    )
                                },
                                text = { Text("Record") },
                            )
                        }
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
                            MainTab.HOME -> HomeScreen(
                                state = toolState,
                                modifier = Modifier.fillMaxSize(),
                                onOpenGlobalCopy = { navController.navigate(TemplateRoutes.GLOBAL_COPY) },
                                onOpenOledShield = { navController.navigate(TemplateRoutes.OLED_SHIELD) },
                                onOpenAccessibilitySettings = toolViewModel::openAccessibilitySettings,
                            )

                            MainTab.RECORDS -> RecordsScreen(
                                state = recordsState,
                                onManage = {
                                    showRecordSheet = false
                                    navController.navigate(TemplateRoutes.MANAGE_RECORDS)
                                },
                                onCreateItem = {
                                    showRecordSheet = false
                                    navController.navigate(TemplateRoutes.RECORD_ITEM_NEW)
                                },
                                showRecordSheet = showRecordSheet,
                                onDismissRecordSheet = { showRecordSheet = false },
                                onRecord = { item, value, onResult ->
                                    recordsViewModel.createEntry(item.id, value) { entryId, error ->
                                        onResult(error)
                                        if (entryId != null) {
                                            showRecordSheet = false
                                            coroutineScope.launch {
                                                val result = snackbarHostState.showSnackbar(
                                                    message = "Recorded ${item.name}.",
                                                    actionLabel = "Undo",
                                                    withDismissAction = true,
                                                )
                                                if (result == SnackbarResult.ActionPerformed) {
                                                    recordsViewModel.deleteEntry(entryId)
                                                }
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxSize(),
                            )

                            MainTab.SETTING -> SettingScreen(
                                themeMode = themeMode,
                                onThemeModeChange = { themeMode = it },
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }
            }
            composable(TemplateRoutes.GLOBAL_COPY) {
                val context = LocalContext.current
                GlobalCopyRoute(
                    state = toolState,
                    historyCount = copyHistory.size,
                    onBack = { navController.popBackStack() },
                    onLaunchTool = {
                        context.showToolLaunchResult(onLaunchTool(ExternalToolCommand.GLOBAL_COPY))
                    },
                    onOpenHistory = { navController.navigate(TemplateRoutes.COPY_HISTORY) },
                    onOpenAccessibilitySettings = toolViewModel::openAccessibilitySettings,
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
            composable(TemplateRoutes.COPY_HISTORY) {
                val context = LocalContext.current
                CopyHistoryScreen(
                    history = copyHistory,
                    selectedHistory = selectedCopyHistory,
                    onBack = { navController.popBackStack() },
                    onOpen = { historyId ->
                        copyHistoryViewModel.open(historyId, context::showMessage)
                    },
                    onCloseEditor = copyHistoryViewModel::closeEditor,
                    onSave = { historyId, state, onResult ->
                        copyHistoryViewModel.save(historyId, state) { error ->
                            if (error == null) context.showMessage("Copy history saved.")
                            onResult(error)
                        }
                    },
                    onCopy = { text ->
                        context.copyToClipboard(text)
                        copyHistoryViewModel.closeEditor()
                    },
                    onDelete = { historyId, onResult ->
                        copyHistoryViewModel.delete(historyId) { error ->
                            if (error == null) context.showMessage("Copy history deleted.")
                            onResult(error)
                        }
                    },
                    onError = context::showMessage,
                )
            }
            composable(TemplateRoutes.OLED_SHIELD) {
                val context = LocalContext.current
                OledShieldRoute(
                    state = toolState,
                    defaultConfig = defaultOledConfig,
                    profiles = oledProfiles,
                    onBack = { navController.popBackStack() },
                    onLaunchTool = {
                        context.showToolLaunchResult(onLaunchTool(ExternalToolCommand.OLED_SHIELD))
                    },
                    onOpenAccessibilitySettings = toolViewModel::openAccessibilitySettings,
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
                        context.showMessage("Saved app deleted.")
                    },
                )
            }
            composable(TemplateRoutes.MANAGE_RECORDS) {
                ManageRecordsScreen(
                    state = recordsState,
                    onBack = { navController.popBackStack() },
                    onNewItem = { navController.navigate(TemplateRoutes.RECORD_ITEM_NEW) },
                    onEditItem = { itemId ->
                        navController.navigate(TemplateRoutes.recordItemEdit(itemId))
                    },
                )
            }
            composable(TemplateRoutes.RECORD_ITEM_NEW) {
                RecordItemFormScreen(
                    item = null,
                    editing = false,
                    loading = false,
                    measurementLocked = false,
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
                route = TemplateRoutes.RECORD_ITEM_EDIT,
                arguments = listOf(navArgument(TemplateRoutes.RECORD_ITEM_ID) { type = NavType.LongType }),
            ) { entry ->
                val itemId = entry.arguments?.getLong(TemplateRoutes.RECORD_ITEM_ID) ?: return@composable
                var measurementLocked by remember(itemId) { mutableStateOf(false) }
                LaunchedEffect(itemId) {
                    recordsViewModel.loadEntryCount(itemId) { count -> measurementLocked = count > 0 }
                }
                RecordItemFormScreen(
                    item = recordsState.findItem(itemId),
                    editing = true,
                    loading = recordsState.isLoading,
                    measurementLocked = measurementLocked,
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
    }
}

@Composable
private fun TemplateSystemBars(darkTheme: Boolean) {
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
    else -> error("TemplateApp must be hosted in an Activity context.")
}

private fun Context.showTileRequestResult(result: TileRequestResult) {
    val message = when (result) {
        TileRequestResult.ADDED -> "Tile added"
        TileRequestResult.ALREADY_ADDED -> "Tile already added"
        TileRequestResult.NOT_ADDED -> "Tile was not added"
        TileRequestResult.ERROR -> "Unable to add tile"
    }
    showMessage(message)
}

private fun Context.showToolLaunchResult(result: ToolLaunchResult) {
    val message = when (result) {
        ToolLaunchResult.Accepted -> return
        ToolLaunchResult.ServiceUnavailable -> "Accessibility service is not connected."
        ToolLaunchResult.TargetAppUnavailable -> "Target app is not available."
        ToolLaunchResult.TargetLaunchFailed -> "Unable to open target app."
    }
    showMessage(message)
}

private fun Context.copyToClipboard(text: String) {
    check(text.isNotBlank()) { "Copy history text must not be blank." }
    getSystemService(ClipboardManager::class.java).setPrimaryClip(
        ClipData.newPlainText("Global Copy", text),
    )
    showMessage("Copied to clipboard.")
}

private fun Context.showMessage(message: String) {
    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}

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

private object TemplateRoutes {
    const val ROOT = "root"
    const val GLOBAL_COPY = "global-copy"
    const val COPY_HISTORY = "global-copy/history"
    const val OLED_SHIELD = "oled-shield"
    const val MANAGE_RECORDS = "manage-records"
    const val RECORD_ITEM_NEW = "record-item/new"
    const val RECORD_ITEM_ID = "recordItemId"
    const val RECORD_ITEM_EDIT = "record-item/{$RECORD_ITEM_ID}"

    fun recordItemEdit(itemId: Long) = "record-item/$itemId"
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
