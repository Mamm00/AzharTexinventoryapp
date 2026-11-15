package com.alazhartex.stock.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

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

/**
 * Material 3 Expressive-leaning shape scale: generous radii, pill-like chrome.
 * Applied everywhere via MaterialTheme.shapes so a single place controls the rounding language.
 */
private val AlAzharShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

/**
 * On API 31+ the app follows the device's dynamic (wallpaper) palette — the M3 Expressive
 * "Personal" pillar — with the AzharTex brand scheme as the guaranteed fallback.
 * Semantic gauge/drift colours are set explicitly at their use sites and are unaffected.
 */
@Composable
fun AlAzharTexTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val scheme = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val context = LocalContext.current
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        if (dark) DarkColors else LightColors
    }
    MaterialTheme(
        colorScheme = scheme,
        shapes = AlAzharShapes,
        content = content,
    )
}
