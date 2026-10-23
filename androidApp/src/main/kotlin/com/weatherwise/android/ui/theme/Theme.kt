package com.weatherwise.android.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Beige = Color(0xFFE4DCCC)
private val BeigeLight = Color(0xFFF1ECE2)
private val Gold = Color(0xFFA28639)
private val GoldDeep = Color(0xFF6E5A22)
private val Charcoal = Color(0xFF1C1B1A)
private val CharcoalLight = Color(0xFF2B2926)
private val BurntOrange = Color(0xFF9A3412)
private val SoftOrange = Color(0xFFFB923C)

// One accent per theme for every icon, button, chip, border and bar.
// On beige the brand gold is only 2.6:1, so light mode uses its deeper shade (4.9:1).
private val LightColors = lightColorScheme(
    primary = GoldDeep,
    onPrimary = Color.White,
    secondary = GoldDeep,
    background = Beige,
    onBackground = Charcoal,
    surface = BeigeLight,
    onSurface = Charcoal,
    surfaceVariant = Color(0xFFD8CEBA),
    onSurfaceVariant = Color(0xFF4A453D),
    outline = GoldDeep,
    error = BurntOrange,
)

private val DarkColors = darkColorScheme(
    primary = Gold,
    onPrimary = Charcoal,
    secondary = Gold,
    background = Charcoal,
    onBackground = Beige,
    surface = CharcoalLight,
    onSurface = Beige,
    surfaceVariant = Color(0xFF3A3632),
    onSurfaceVariant = Color(0xFFCFC6B4),
    outline = Gold,
    error = SoftOrange,
)

@Composable
fun WeatherWiseTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
