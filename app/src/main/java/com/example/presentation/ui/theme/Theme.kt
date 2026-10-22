package com.example.presentation.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = GoogleBlueDark,
    onPrimary = Color(0xFF003063),
    primaryContainer = Color(0xFF00468B),
    onPrimaryContainer = Color(0xFFD6E3FF),
    secondary = GoogleGreenDark,
    onSecondary = Color(0xFF003914),
    tertiary = GoogleYellowDark,
    onTertiary = Color(0xFF3F2E00),
    error = GoogleRedDark,
    onError = Color(0xFF601410),
    background = GoogleBackgroundDark,
    onBackground = GoogleOnSurfaceDark,
    surface = GoogleSurfaceDark,
    onSurface = GoogleOnSurfaceDark,
    surfaceVariant = GoogleSurfaceContainerDark,
    onSurfaceVariant = Color(0xFFC4C6D0),
    outline = CardBorderDark
)

private val LightColorScheme = lightColorScheme(
    primary = GoogleBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E3FF),
    onPrimaryContainer = Color(0xFF001B3E),
    secondary = GoogleGreen,
    onSecondary = Color.White,
    tertiary = GoogleYellow,
    onTertiary = Color(0xFF261A00),
    error = GoogleRed,
    onError = Color.White,
    background = GoogleBackgroundLight,
    onBackground = GoogleOnSurfaceLight,
    surface = GoogleSurfaceLight,
    onSurface = GoogleOnSurfaceLight,
    surfaceVariant = GoogleSurfaceContainerLight,
    onSurfaceVariant = Color(0xFF44474E),
    outline = CardBorderLight
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
