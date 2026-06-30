package com.wswensw.template.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
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
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.core.view.WindowCompat
import androidx.navigation.NavBackStackEntry
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.wswensw.template.R

private enum class MainTab(
    val labelRes: Int,
    val iconRes: Int,
) {
    HOME(R.string.tab_home, R.drawable.ic_home_24),
    SETTING(R.string.tab_setting, R.drawable.ic_settings_24),
}

@Composable
fun TemplateApp() {
    val navController = rememberNavController()
    var selectedTab by rememberSaveable { mutableStateOf(MainTab.HOME) }
    var themeMode by rememberSaveable { mutableStateOf(TemplateThemeMode.SYSTEM) }
    var templateOption by rememberSaveable { mutableStateOf("Compact rows") }
    val darkTheme = themeMode.resolvedDarkTheme(isSystemInDarkTheme())

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
                            onSelectTab = { selectedTab = it },
                        )
                    },
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
                                modifier = Modifier.fillMaxSize(),
                                onOpenComponents = { navController.navigate(TemplateRoutes.COMPONENTS) },
                                onOpenAction = { navController.navigate(TemplateRoutes.ACTION) },
                                onOpenRules = { navController.navigate(TemplateRoutes.RULES) },
                                onOpenExtension = { navController.navigate(TemplateRoutes.EXTENSION) },
                            )

                            MainTab.SETTING -> SettingScreen(
                                themeMode = themeMode,
                                onThemeModeChange = { themeMode = it },
                                templateOption = templateOption,
                                onTemplateOptionChange = { templateOption = it },
                                onOpenSettingsGuide = { navController.navigate(TemplateRoutes.SETTINGS_GUIDE) },
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }
            }
            composable(TemplateRoutes.COMPONENTS) {
                ComponentsRoute(
                    onBack = { navController.popBackStack() },
                    onOpenAction = { navController.navigate(TemplateRoutes.ACTION) },
                )
            }
            composable(TemplateRoutes.ACTION) {
                ActionRoute(onBack = { navController.popBackStack() })
            }
            composable(TemplateRoutes.RULES) {
                RulesRoute(onBack = { navController.popBackStack() })
            }
            composable(TemplateRoutes.EXTENSION) {
                ExtensionRoute(onBack = { navController.popBackStack() })
            }
            composable(TemplateRoutes.SETTINGS_GUIDE) {
                SettingsGuideRoute(onBack = { navController.popBackStack() })
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
    const val COMPONENTS = "components"
    const val ACTION = "action"
    const val RULES = "rules"
    const val EXTENSION = "extension"
    const val SETTINGS_GUIDE = "settings-guide"
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
