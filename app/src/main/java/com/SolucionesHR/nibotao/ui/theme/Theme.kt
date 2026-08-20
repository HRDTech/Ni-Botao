package com.SolucionesHR.nibotao.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF2E7D32),
    secondary = Color(0xFF00897B),
    tertiary = Color(0xFFF9A825),
    error = Color(0xFFC62828)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF66BB6A),
    secondary = Color(0xFF4DB6AC),
    tertiary = Color(0xFFFFCA28),
    error = Color(0xFFEF5350)
)

@Composable
fun AutonomiaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = MaterialTheme.typography,
        content = content
    )
}
