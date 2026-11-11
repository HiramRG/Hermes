package io.github.hiramrg.hermes.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import io.github.hiramrg.hermes.core.PokemonGame

/** Black background: on the Thor's AMOLED panels black pixels are off, which saves battery. */
private val Colors = darkColorScheme(
    primary = Color(0xFFFFCB05),
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF3A2F00),
    onPrimaryContainer = Color(0xFFFFE9A0),
    secondary = Color(0xFF5DA9FF),
    tertiary = Color(0xFFFF6B5B),
    background = Color.Black,
    onBackground = Color(0xFFECEDF1),
    surface = Color(0xFF101217),
    onSurface = Color(0xFFECEDF1),
    surfaceVariant = Color(0xFF1C1F27),
    onSurfaceVariant = Color(0xFFB0B5C3),
    surfaceContainer = Color(0xFF15181E),
    surfaceContainerHigh = Color(0xFF1C1F27),
    outline = Color(0xFF3B404C),
    error = Color(0xFFFF6B5B),
)

/** Cartridge colours. */
fun PokemonGame.color(): Color = when (this) {
    PokemonGame.RED -> Color(0xFFD8342C)
    PokemonGame.BLUE -> Color(0xFF2F62C9)
    PokemonGame.YELLOW -> Color(0xFFF3C623)
    PokemonGame.GREEN -> Color(0xFF3E9B4F)
}

fun PokemonGame.onColor(): Color = if (this == PokemonGame.YELLOW) Color(0xFF231C00) else Color.White

@Composable
fun HermesTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors) {
        Surface(Modifier.fillMaxSize(), color = Colors.background, contentColor = Colors.onBackground, content = content)
    }
}
