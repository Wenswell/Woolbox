package com.wswensw.template.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
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
    var selectedTab by rememberSaveable { mutableStateOf(MainTab.HOME) }

    TemplateTheme {
        TemplateSurface(
            bottomBar = {
                BottomNavigationBar(
                    selectedTab = selectedTab,
                    onSelectTab = { selectedTab = it },
                )
            },
        ) { innerPadding: PaddingValues ->
            when (selectedTab) {
                MainTab.HOME -> HomeScreen(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                )

                MainTab.SETTING -> SettingScreen(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                )
            }
        }
    }
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
