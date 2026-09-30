package com.example.personal_financestudydaily_routine_assistant.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val DarkColorScheme = darkColorScheme(
    primary = Mint,
    onPrimary = Color(0xFF073B2E),
    primaryContainer = Color(0xFF164D3E),
    onPrimaryContainer = Color(0xFFD0F1E2),
    secondary = Color(0xFFC2D2C8),
    onSecondary = Color(0xFF27352D),
    secondaryContainer = Color(0xFF33443A),
    onSecondaryContainer = Color(0xFFDCE9DF),
    tertiary = Color(0xFFE7B879),
    onTertiary = Color(0xFF482900),
    error = Color(0xFFFFB4AB),
    background = DarkBackground,
    onBackground = DarkText,
    surface = DarkSurface,
    onSurface = DarkText,
    surfaceVariant = DarkSurfaceLow,
    onSurfaceVariant = DarkTextMuted,
    surfaceContainerLow = DarkSurfaceLow,
    surfaceContainer = DarkSurface,
    surfaceContainerHigh = DarkSurfaceHigh,
    outline = DarkOutline,
    outlineVariant = Color(0xFF35433A)
)

private val LightColorScheme = lightColorScheme(
    primary = Forest,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD7EEE4),
    onPrimaryContainer = Color(0xFF123E31),
    secondary = Color(0xFF53665D),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDCE9E0),
    onSecondaryContainer = Color(0xFF27372F),
    tertiary = Amber,
    onTertiary = Color.White,
    error = Rose,
    background = LightBackground,
    onBackground = LightText,
    surface = LightSurface,
    onSurface = LightText,
    surfaceVariant = LightSurfaceLow,
    onSurfaceVariant = LightTextMuted,
    surfaceContainerLow = LightSurface,
    surfaceContainer = LightSurfaceLow,
    surfaceContainerHigh = LightSurfaceHigh,
    outline = Color(0xFF718078),
    outlineVariant = LightOutline
)

private val AppShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(22.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(28.dp)
)

@Composable
fun Personal_FinanceStudyDaily_Routine_AssistantTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && darkTheme -> androidx.compose.material3.dynamicDarkColorScheme(androidx.compose.ui.platform.LocalContext.current)
        dynamicColor -> androidx.compose.material3.dynamicLightColorScheme(androidx.compose.ui.platform.LocalContext.current)
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = AppShapes,
        content = content
    )
}
