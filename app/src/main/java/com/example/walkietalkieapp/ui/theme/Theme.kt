package com.example.walkietalkieapp.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

val LocalWalkieDarkTheme = staticCompositionLocalOf { false }

private val TacticalDarkColorScheme = darkColorScheme(
    primary = WalkieAmber,
    onPrimary = WalkieDarkBackground,
    primaryContainer = WalkieButton,
    onPrimaryContainer = WalkieTextPrimary,
    secondary = WalkieAmberLight,
    onSecondary = WalkieDarkBackground,
    background = WalkieDarkBackground,
    onBackground = WalkieTextPrimary,
    surface = WalkieDeviceBody,
    onSurface = WalkieTextPrimary,
    surfaceVariant = WalkieCard,
    onSurfaceVariant = WalkieTextSecondary,
    outline = WalkieCardBorder,
    error = WalkieAmberDark
)

// Web Default Light Color Scheme (Warm retro cream background)
private val TacticalLightColorScheme = lightColorScheme(
    primary = WalkieAmber,
    onPrimary = WalkieWarmCream,
    primaryContainer = WalkieButton,
    onPrimaryContainer = WalkieTextPrimary,
    secondary = WalkieAmberDark,
    onSecondary = WalkieWarmCream,
    background = WalkieWarmCream,
    onBackground = WalkieDeviceBody,
    surface = WalkieDeviceBody,
    onSurface = WalkieTextPrimary,
    surfaceVariant = WalkieCard,
    onSurfaceVariant = WalkieTextSecondary,
    outline = WalkieCardBorder,
    error = WalkieAmberDark
)

@Composable
fun WalkieTalkieAppTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) TacticalDarkColorScheme else TacticalLightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                val bg = if (darkTheme) WalkieDarkBackground else WalkieWarmCream
                window.statusBarColor = bg.toArgb()
                window.navigationBarColor = bg.toArgb()
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = !darkTheme
                controller.isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(
        LocalWalkieDarkTheme provides darkTheme,
        androidx.compose.material3.LocalTextStyle provides androidx.compose.ui.text.TextStyle(
            fontFamily = PlusJakartaSans,
            color = if (darkTheme) WalkieTextPrimary else WalkieDeviceBody
        )
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = WalkieShapes,
            content = content
        )
    }
}