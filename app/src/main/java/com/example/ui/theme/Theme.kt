package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val NothingDarkColorScheme = darkColorScheme(
    primary = NothingRed,
    onPrimary = Color.White,
    secondary = Color.White,
    onSecondary = Color.Black,
    tertiary = Color.Gray,
    background = PureBlack,
    surface = DarkSurface,
    surfaceVariant = DarkSurfaceVariant,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    onSurfaceVariant = TextSecondary,
    error = NothingRed,
    outline = Color(0xFF333333) // For thin card outlines
)

private val NothingLightColorScheme = lightColorScheme(
    primary = NothingRed,
    onPrimary = Color.White,
    secondary = Color.Black,
    onSecondary = Color.White,
    tertiary = Color.Gray,
    background = LightBackground,
    surface = LightSurface,
    surfaceVariant = LightSurfaceVariant,
    onBackground = Color.Black,
    onSurface = Color.Black,
    onSurfaceVariant = Color(0xFF666666),
    error = NothingRed,
    outline = Color(0xFFDDDDDD)
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true, // Force Dark Mode for the Nothing OS vibe
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = NothingDarkColorScheme,
        typography = Typography,
        content = content
    )
}
