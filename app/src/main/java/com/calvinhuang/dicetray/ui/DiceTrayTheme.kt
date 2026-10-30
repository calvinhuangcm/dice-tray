package com.calvinhuang.dicetray.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DiceDarkColors = darkColorScheme(
    primary = Color(0xFFF5B942),
    onPrimary = Color(0xFF2B210A),
    secondary = Color(0xFF67D39B),
    background = Color(0xFF0D1016),
    surface = Color(0xFF171B23),
    surfaceVariant = Color(0xFF242A34),
    onSurface = Color(0xFFF5F7FB),
    onSurfaceVariant = Color(0xFFB3BAC6),
)

@Composable
fun DiceTrayTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DiceDarkColors,
        content = content,
    )
}
