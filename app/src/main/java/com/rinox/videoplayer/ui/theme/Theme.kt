package com.rinox.videoplayer.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val DarkBg = Color(0xFF0A0D12)
val DarkCard = Color(0xFF141923)
val LightBg = Color(0xFFF6F8FB)

val RinoxCyan = Color(0xFF00E5FF)
val RinoxPurple = Color(0xFFBB86FC)
val RinoxEmerald = Color(0xFF00E676)
val RinoxAmber = Color(0xFFFFB300)
val RinoxCrimson = Color(0xFFFF5252)

@Composable
fun RinoxPlayerTheme(
    darkTheme: Boolean = true,
    accentColor: Color = RinoxCyan,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = accentColor,
            secondary = accentColor,
            background = DarkBg,
            surface = DarkCard,
            onPrimary = Color.Black,
            onBackground = Color.White,
            onSurface = Color.White
        )
    } else {
        lightColorScheme(
            primary = accentColor,
            secondary = accentColor,
            background = LightBg,
            surface = Color.White,
            onPrimary = Color.White,
            onBackground = Color(0xFF111318),
            onSurface = Color(0xFF111318)
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
