package com.rinox.videoplayer.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val AmoledBlack = Color(0xFF000000)
val DarkSurface = Color(0xFF0D1117)
val DarkCard = Color(0xFF161B22)
val RinoxBlue = Color(0xFF2196F3)
val RinoxCyan = Color(0xFF00E5FF)
val TextPrimary = Color(0xFFFFFFFF)
val TextSecondary = Color(0xFF9EA7B3)

private val RinoxDarkColorScheme = darkColorScheme(
    primary = RinoxBlue,
    secondary = RinoxCyan,
    background = AmoledBlack,
    surface = DarkSurface,
    surfaceVariant = DarkCard,
    onPrimary = Color.White,
    onSecondary = Color.Black,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    onSurfaceVariant = TextSecondary
)

@Composable
fun RinoxPlayerTheme(
    accentColor: Color = RinoxBlue,
    content: @Composable () -> Unit
) {
    val dynamicScheme = RinoxDarkColorScheme.copy(primary = accentColor)
    MaterialTheme(
        colorScheme = dynamicScheme,
        content = content
    )
}
