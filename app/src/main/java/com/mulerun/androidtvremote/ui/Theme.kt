package com.mulerun.androidtvremote.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Seed = Color(0xFF4F6DF5)

private val LightScheme = lightColorScheme(
    primary = Seed,
    onPrimary = Color.White,
    secondary = Color(0xFF7A88C7),
    background = Color(0xFFF7F8FB),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFEBEEF6),
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF9AB0FF),
    onPrimary = Color(0xFF00195C),
    secondary = Color(0xFFBDC6E9),
    background = Color(0xFF0F1220),
    surface = Color(0xFF161A2C),
    surfaceVariant = Color(0xFF1F2440),
    onSurface = Color(0xFFE4E7F7),
)

@Composable
fun AppTheme(darkOverride: Boolean? = null, content: @Composable () -> Unit) {
    val dark = darkOverride ?: isSystemInDarkTheme()
    MaterialTheme(colorScheme = if (dark) DarkScheme else LightScheme, content = content)
}
