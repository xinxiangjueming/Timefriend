package com.example.timefriend.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme

private val LightGrayBackground = Color(0xFFF0F0F0)
private val LightGraySurface = Color(0xFFE8E8E8)

@Composable
fun TimefriendTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) {
        darkColorScheme(
            surfaceContainer = Color(0xFF303030),
            surfaceContainerHigh = Color(0xFF3A3A3A),
            background = Color(0xFF1A1A1A),
            surface = Color(0xFF222222)
        )
    } else {
        lightColorScheme(
            background = LightGrayBackground,
            surface = LightGraySurface,
            surfaceContainer = Color.White,
            surfaceContainerHigh = Color(0xFFE0E0E0)
        )
    }

    MiuixTheme(
        colors = colors,
        content = content
    )
}
