package com.vz.networkbooster.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Accent = Color(0xFF22D3EE)
private val AccentDark = Color(0xFF0E7490)
private val SurfaceDark = Color(0xFF0F151C)
private val SurfaceVariantDark = Color(0xFF1B2430)

private val DarkColors = darkColorScheme(
    primary = Accent,
    onPrimary = Color(0xFF00222B),
    primaryContainer = AccentDark,
    onPrimaryContainer = Color(0xFFDFF7FF),
    secondary = Color(0xFF9BD1E5),
    background = Color(0xFF0B0F14),
    onBackground = Color(0xFFE6EDF3),
    surface = SurfaceDark,
    onSurface = Color(0xFFE6EDF3),
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = Color(0xFFB6C2CF),
    error = Color(0xFFFF6B6B),
)

private val LightColors = lightColorScheme(
    primary = AccentDark,
    secondary = AccentDark,
    background = Color(0xFFF6F8FA),
    surface = Color(0xFFFFFFFF),
)

@Composable
fun VzTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography(),
        content = content,
    )
}
