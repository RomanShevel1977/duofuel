package app.duofuel.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Pine = Color(0xFF0E6B4F)
private val PineDark = Color(0xFF8FCBB4)
private val Amber = Color(0xFFC47B12)
private val Ink = Color(0xFF1C2421)
private val Paper = Color(0xFFF6F3EC)
private val Night = Color(0xFF101614)

private val LightColors = lightColorScheme(
    primary = Pine,
    onPrimary = Color.White,
    secondary = Amber,
    onSecondary = Ink,
    background = Paper,
    surface = Color.White,
    onBackground = Ink,
    onSurface = Ink,
)

private val DarkColors = darkColorScheme(
    primary = PineDark,
    onPrimary = Night,
    secondary = Color(0xFFE2B15A),
    onSecondary = Night,
    background = Night,
    surface = Color(0xFF1A2420),
    onBackground = Color(0xFFE7F0EB),
    onSurface = Color(0xFFE7F0EB),
)

@Composable
fun DuoFuelTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
