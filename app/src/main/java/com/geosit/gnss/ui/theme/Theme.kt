package com.geosit.gnss.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Define your colors
val GeoSitGreen = Color(0xFF4FA95A)
val GeoSitGreenDark = Color(0xFF388E3C)
val GeoSitBlack = Color(0xFF000000)
val GeoSitWhite = Color(0xFFFFFFFF)
val GeoSitGray = Color(0xFF808080)
val GeoSitGrayLight = Color(0xFFCCCCCC)
val GeoSitRed = Color(0xFFFF6B6B)

private val DarkColorScheme = darkColorScheme(
    primary = GeoSitGreen,
    onPrimary = GeoSitWhite,
    primaryContainer = GeoSitGreenDark,
    onPrimaryContainer = GeoSitWhite,
    secondary = GeoSitGreen,
    onSecondary = GeoSitWhite,
    background = GeoSitBlack,
    onBackground = GeoSitWhite,
    surface = GeoSitBlack,
    onSurface = GeoSitWhite,
    error = GeoSitRed,
    onError = GeoSitWhite
)

private val LightColorScheme = lightColorScheme(
    primary = GeoSitGreen,
    onPrimary = GeoSitWhite,
    primaryContainer = GeoSitGreenDark,
    onPrimaryContainer = GeoSitWhite,
    secondary = GeoSitGreen,
    onSecondary = GeoSitWhite,
    background = GeoSitWhite,
    onBackground = GeoSitBlack,
    surface = GeoSitWhite,
    onSurface = GeoSitBlack,
    error = GeoSitRed,
    onError = GeoSitWhite
)

@Composable
fun GeoSitTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    // For now, always use dark theme to match rawX
    val colorScheme = DarkColorScheme
    
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
