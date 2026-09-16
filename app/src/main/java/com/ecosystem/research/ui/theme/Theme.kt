package com.ecosystem.research.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable

private val LightColorScheme = lightColorScheme(
    primary = ResearchPrimary,
    secondary = ResearchSecondary,
    tertiary = ResearchTertiary,
    background = ResearchBackground,
    surface = ResearchSurface
)

private val DarkColorScheme = darkColorScheme(
    primary = ResearchSecondary,
    secondary = ResearchTertiary,
    tertiary = ResearchPrimary
)

@Composable
fun ResearchTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
