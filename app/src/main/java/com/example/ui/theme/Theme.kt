package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = GrowBlue,
    secondary = GainGreen,
    tertiary = SlateBorder,
    background = SlateBg,
    surface = SlateSurface,
    onPrimary = SlateTextPrimary,
    onSecondary = SlateTextPrimary,
    onBackground = SlateTextPrimary,
    onSurface = SlateTextPrimary,
    outline = SlateBorder
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
