package team.ctrlv.musipedia

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val MusiumAqua = Color(0xFF42E4CE)

private val LightColors = lightColorScheme(
    primary = MusiumAqua,
    background = Color(0xFFFAFAF8),
    surface = Color.White,
    surfaceVariant = Color(0xFFF0F1EF),
    onBackground = Color(0xFF3F4944),
    onSurface = Color(0xFF414944),
    onSurfaceVariant = Color(0xFF7F8984),
    outlineVariant = Color(0xFFE1E5E2),
)

private val DarkColors = darkColorScheme(
    primary = MusiumAqua,
    background = Color(0xFF272827),
    surface = Color(0xFF303130),
    surfaceVariant = Color(0xFF393B39),
    onBackground = Color(0xFFF4F5F4),
    onSurface = Color(0xFFF4F5F4),
    onSurfaceVariant = Color(0xFFADB5B0),
    outlineVariant = Color(0xFF484B49),
)

@Composable
fun MusiumTheme(darkMode: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkMode) DarkColors else LightColors,
        content = content,
    )
}
