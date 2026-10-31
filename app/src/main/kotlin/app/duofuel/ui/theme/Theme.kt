package app.duofuel.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val Pine = Color(0xFF0E6B4F)
private val PineDark = Color(0xFF8FCBB4)
private val Amber = Color(0xFFC47B12)
private val Ink = Color(0xFF1C2421)
private val Paper = Color(0xFFF6F3EC)
private val Night = Color(0xFF101614)

private val LightColors = lightColorScheme(
    primary = Pine,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD3EBDF),
    onPrimaryContainer = Color(0xFF083528),
    secondary = Amber,
    onSecondary = Ink,
    secondaryContainer = Color(0xFFFFE6C2),
    onSecondaryContainer = Ink,
    background = Paper,
    surface = Color.White,
    surfaceVariant = Color(0xFFE4EDE7),
    onBackground = Ink,
    onSurface = Ink,
    onSurfaceVariant = Color(0xFF3E4A45),
    outline = Color(0xFF6F7D76),
    error = Color(0xFFB3261E),
)

private val DarkColors = darkColorScheme(
    primary = PineDark,
    onPrimary = Night,
    primaryContainer = Color(0xFF1E4A3A),
    onPrimaryContainer = Color(0xFFD3EBDF),
    secondary = Color(0xFFE2B15A),
    onSecondary = Night,
    secondaryContainer = Color(0xFF4A3714),
    onSecondaryContainer = Color(0xFFFFE6C2),
    background = Night,
    surface = Color(0xFF1A2420),
    surfaceVariant = Color(0xFF24302B),
    onBackground = Color(0xFFE7F0EB),
    onSurface = Color(0xFFE7F0EB),
    onSurfaceVariant = Color(0xFFC5D4CC),
    outline = Color(0xFF8A9A93),
    error = Color(0xFFFFB4AB),
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
)

@Composable
fun DuoFuelTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        shapes = AppShapes,
        content = content,
    )
}
