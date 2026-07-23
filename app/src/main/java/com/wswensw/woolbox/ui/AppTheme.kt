package com.wswensw.woolbox.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

enum class ThemeMode(
    val label: String,
) {
    SYSTEM("Follow system"),
    LIGHT("Light"),
    DARK("Dark"),
}

fun ThemeMode.resolvedDarkTheme(systemDarkTheme: Boolean): Boolean = when (this) {
    ThemeMode.SYSTEM -> systemDarkTheme
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

private val LightColors = lightColorScheme(
    primary = Color(0xFF3E6A57),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC0EAD6),
    onPrimaryContainer = Color(0xFF002115),
    secondary = Color(0xFF5C6245),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE1E7C5),
    onSecondaryContainer = Color(0xFF191E08),
    tertiary = Color(0xFF6F5C2E),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFADC9B),
    onTertiaryContainer = Color(0xFF241A00),
    background = Color(0xFFFAFAF4),
    onBackground = Color(0xFF1B1C18),
    surface = Color(0xFFFAFAF4),
    onSurface = Color(0xFF1B1C18),
    surfaceVariant = Color(0xFFE0E4D8),
    onSurfaceVariant = Color(0xFF44483F),
    outlineVariant = Color(0xFFC4C8BC),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA5CEBA),
    onPrimary = Color(0xFF0E3828),
    primaryContainer = Color(0xFF26503F),
    onPrimaryContainer = Color(0xFFC0EAD6),
    secondary = Color(0xFFC5CBAB),
    onSecondary = Color(0xFF2E331C),
    secondaryContainer = Color(0xFF444A30),
    onSecondaryContainer = Color(0xFFE1E7C5),
    tertiary = Color(0xFFDDC183),
    onTertiary = Color(0xFF3E2E04),
    tertiaryContainer = Color(0xFF564419),
    onTertiaryContainer = Color(0xFFFADC9B),
    background = Color(0xFF121410),
    onBackground = Color(0xFFE3E3DC),
    surface = Color(0xFF121410),
    onSurface = Color(0xFFE3E3DC),
    surfaceVariant = Color(0xFF44483F),
    onSurfaceVariant = Color(0xFFC4C8BC),
    outlineVariant = Color(0xFF44483F),
)

@Composable
fun AppTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    darkTheme: Boolean = themeMode.resolvedDarkTheme(isSystemInDarkTheme()),
    useDynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when {
        useDynamicColor && darkTheme -> dynamicDarkColorScheme(context)
        useDynamicColor -> dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = MaterialTheme.typography,
        content = content,
    )
}
