package com.example.ui.theme

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

private val LightColorScheme = lightColorScheme(
    primary = WasilPrimary,
    onPrimary = Color.White,
    primaryContainer = WasilPrimaryContainer,
    onPrimaryContainer = WasilOnPrimaryContainer,
    secondary = WasilSecondary,
    onSecondary = Color.White,
    secondaryContainer = WasilSecondaryContainer,
    onSecondaryContainer = WasilOnSecondaryContainer,
    tertiary = WasilTertiary,
    onTertiary = Color.White,
    tertiaryContainer = WasilTertiaryContainer,
    background = WasilBackground,
    onBackground = WasilTextPrimary,
    surface = WasilSurface,
    onSurface = WasilTextPrimary,
    surfaceVariant = WasilSurfaceVariant,
    onSurfaceVariant = WasilTextSecondary,
    outline = WasilOutline
)

private val DarkColorScheme = darkColorScheme(
    primary = WasilPrimaryLight,
    onPrimary = Color.Black,
    primaryContainer = WasilPrimaryDark,
    onPrimaryContainer = Color.White,
    secondary = WasilSecondaryLight,
    onSecondary = Color.Black,
    secondaryContainer = WasilOnSecondaryContainer,
    tertiary = WasilTertiary,
    background = DarkBackground,
    onBackground = DarkTextPrimary,
    surface = DarkSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkTextSecondary,
    outline = WasilOutline
)

@Composable
fun WasilDeliveryTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // We stick with our curated brand palette
    content: @Composable () -> Unit
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
        typography = AppTypography,
        content = content
    )
}
