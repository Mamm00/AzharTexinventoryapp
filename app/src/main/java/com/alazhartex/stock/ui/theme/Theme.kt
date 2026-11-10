package com.alazhartex.stock.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** AlAzharTex brand (CONTENT.md §10): Azhar green + sand accent. Never the sole meaning carrier. */
val AzharGreen = Color(0xFF0F5132)
val AzharGreenLight = Color(0xFF1B6B47)
val Sand = Color(0xFFD4A017)
val SandSoft = Color(0xFFF3E3B3)
val GaugeGreen = Color(0xFF1E7B3C)
val GaugeAmber = Color(0xFFB45309)
val GaugeRed = Color(0xFFB4231E)
val SurfaceWarm = Color(0xFFFFFCF5)

private val LightColors = lightColorScheme(
    primary = AzharGreen,
    onPrimary = Color.White,
    secondary = Sand,
    onSecondary = Color(0xFF2A2200),
    background = SurfaceWarm,
    surface = Color.White,
)

private val DarkColors = darkColorScheme(
    primary = AzharGreenLight,
    onPrimary = Color(0xFF052B1B),
    secondary = SandSoft,
    onSecondary = Color(0xFF2A2200),
    background = Color(0xFF101512),
    surface = Color(0xFF161D19),
)

@Composable
fun AlAzharTexTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
