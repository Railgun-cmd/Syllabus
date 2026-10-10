package com.example.demo.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val OceanBlue = Color(0xFF3D6DD8)
private val DeepSea = Color(0xFF1B3A8F)
private val Coral = Color(0xFFFF7A63)

private val LightColors = lightColorScheme(
    primary = OceanBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE6FF),
    onPrimaryContainer = DeepSea,
    secondary = Coral,
    surface = Color(0xFFF8F9FC),
    surfaceVariant = Color(0xFFE8EDF6),
    onSurface = Color(0xFF1A1C20),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9DB8FF),
    onPrimary = Color(0xFF0A2A66),
    primaryContainer = Color(0xFF1E3A75),
    onPrimaryContainer = Color(0xFFDCE6FF),
    secondary = Color(0xFFFFB4A8),
    surface = Color(0xFF12151C),
    surfaceVariant = Color(0xFF232834),
    onSurface = Color(0xFFE2E4EA),
)

@Composable
fun ScheduleTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
