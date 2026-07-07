package com.yourname.pillpantry.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Teal = Color(0xFF00796B)
val TealDark = Color(0xFF004D40)

private val LightColors = lightColorScheme(
    primary = Teal,
    secondary = TealDark
)

private val DarkColors = darkColorScheme(
    primary = Teal,
    secondary = TealDark
)

@Composable
fun PillPantryTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colors,
        content = content
    )
}
