package com.alexpo.grammermate.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF2F5D62),
    onPrimary = Color.White,
    secondary = Color(0xFF5E8B7E),
    onSecondary = Color.White,
    background = Color(0xFFF7F4F1),
    onBackground = Color(0xFF1F1F1F),
    surface = Color.White,
    onSurface = Color(0xFF1F1F1F)
)

@Composable
fun GrammarMateTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        content = content
    )
}
