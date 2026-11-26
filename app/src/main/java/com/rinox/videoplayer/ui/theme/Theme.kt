package com.rinox.videoplayer.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val AmoledBlack = Color(0xFF000000)
val DarkSurface = Color(0xFF0E1218)
val DarkCard = Color(0xFF161B22)
val LightBg = Color(0xFFF6F8FA)
val LightCard = Color(0xFFFFFFFF)
val RinoxBlue = Color(0xFF2196F3)
val RinoxCyan = Color(0xFF00E5FF)
val TextPrimary = Color(0xFFFFFFFF)
val TextSecondary = Color(0xFF8B949E)

@Composable
fun RinoxPlayerTheme(
    darkTheme: Boolean = true,
    accentColor: Color = RinoxBlue,
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) {
        darkColorScheme(
            primary = accentColor,
            secondary = RinoxCyan,
            background = AmoledBlack,
            surface = DarkSurface,
            surfaceVariant = DarkCard,
            onBackground = Color.White,
            onSurface = Color.White,
            onSurfaceVariant = Color(0xFF8B949E)
        )
    } else {
        lightColorScheme(
            primary = accentColor,
            secondary = RinoxBlue,
            background = LightBg,
            surface = LightCard,
            surfaceVariant = Color(0xFFEAEEF2),
            onBackground = Color(0xFF1F2328),
            onSurface = Color(0xFF1F2328),
            onSurfaceVariant = Color(0xFF656D76)
        )
    }
    MaterialTheme(colorScheme = colors, content = content)
}
