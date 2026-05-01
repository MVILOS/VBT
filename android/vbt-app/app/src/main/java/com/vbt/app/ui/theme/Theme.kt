package com.vbt.app.ui.theme

import android.app.Activity
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val VbtDarkColorScheme = darkColorScheme(
    primary = VbtTeal,
    onPrimary = VbtBackground,
    primaryContainer = VbtTeal,
    secondary = VbtPurple,
    onSecondary = VbtTextPrimary,
    secondaryContainer = VbtPurple,
    tertiary = VbtSuccess,
    onTertiary = VbtBackground,
    background = VbtBackground,
    onBackground = VbtTextPrimary,
    surface = VbtSurface,
    onSurface = VbtTextPrimary,
    surfaceVariant = VbtSurfaceVariant,
    onSurfaceVariant = VbtTextSecondary,
    error = VbtError,
    onError = VbtBackground,
    errorContainer = VbtError,
    outline = VbtTextSecondary,
    outlineVariant = VbtTextDisabled,
)

@Composable
fun VbtTheme(content: @Composable () -> Unit) {
    val colorScheme = VbtDarkColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view)?.isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = VbtTypography,
        content = content
    )
}
