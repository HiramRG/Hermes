package io.github.hiramrg.hermes.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.hiramrg.hermes.core.PokemonGame
import io.github.hiramrg.hermes.core.TrainerCard

/** Yellow ring around whatever the D-pad has selected. */
@Composable
fun focusBorder(interaction: MutableInteractionSource, width: Dp = 3.dp): BorderStroke? {
    val focused by interaction.collectIsFocusedAsState()
    return if (focused) BorderStroke(width, MaterialTheme.colorScheme.primary) else null
}

/** A big, controller-friendly button. */
@Composable
fun HButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    primary: Boolean = false,
    enabled: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    val bg = when {
        !enabled -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        primary -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val fg = if (primary && enabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = bg,
        contentColor = fg,
        border = focusBorder(interaction, 3.dp) ?: if (primary) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        interactionSource = interaction,
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            if (icon != null) {
                Icon(icon, contentDescription = null, Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(text, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        }
    }
}

/** A drawn Game Boy cartridge in the game's colour (no official artwork). */
@Composable
fun CartridgeArt(game: PokemonGame, modifier: Modifier = Modifier, width: Dp = 96.dp) {
    val h = width * 1.1f
    Box(
        modifier
            .size(width, h)
            .background(game.color(), RoundedCornerShape(topStart = 6.dp, topEnd = 18.dp, bottomStart = 6.dp, bottomEnd = 6.dp)),
    ) {
        // Label area
        Box(
            Modifier
                .align(Alignment.Center)
                .padding(top = 10.dp)
                .size(width * 0.74f, h * 0.52f)
                .background(Color(0xFFF5F2E8), RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // A simple ball emblem
                Box(Modifier.size(width * 0.22f).background(game.color(), CircleShape), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(width * 0.08f).background(Color(0xFFF5F2E8), CircleShape))
                }
                Spacer(Modifier.height(4.dp))
                Text(game.label.uppercase(), color = Color(0xFF22252C), fontWeight = FontWeight.Black, fontSize = (width.value / 8).sp)
            }
        }
        // Grip ridges
        Row(Modifier.align(Alignment.TopCenter).padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            repeat(5) { Box(Modifier.size(width * 0.1f, 3.dp).background(Color.Black.copy(alpha = 0.18f), RoundedCornerShape(2.dp))) }
        }
    }
}

/** Eight badge dots, filled for the ones earned. */
@Composable
fun BadgeRow(badges: Int, size: Dp = 14.dp) {
    val colors = listOf(
        Color(0xFF9E9E9E), Color(0xFF4FC3F7), Color(0xFFFFB300), Color(0xFF66BB6A),
        Color(0xFFEC407A), Color(0xFFFFD54F), Color(0xFFFF7043), Color(0xFF8D6E63),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        colors.forEachIndexed { i, c ->
            val earned = (badges shr i) and 1 == 1
            Box(Modifier.size(size).background(if (earned) c else Color(0xFF2A2E38), CircleShape))
        }
    }
}

@Composable
fun StatLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
        Text(value, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    }
}

/** Trainer card + party, used on both screens. */
@Composable
fun TrainerPanel(card: TrainerCard, compact: Boolean = false) {
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(card.name, fontWeight = FontWeight.Bold, fontSize = if (compact) 20.sp else 26.sp)
            Spacer(Modifier.width(10.dp))
            Text("ID ${card.idText}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
        }
        Spacer(Modifier.height(6.dp))
        BadgeRow(card.badges)
        Spacer(Modifier.height(8.dp))
        if (!compact) {
            StatLine("Pokédex", "${card.owned} caught · ${card.seen} seen")
            StatLine("Time played", card.playTime)
            StatLine("Money", "₽${card.money}")
            Spacer(Modifier.height(8.dp))
        }
        Text("Party", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        Spacer(Modifier.height(4.dp))
        if (card.party.isEmpty()) Text("No Pokémon in the party", fontSize = 14.sp)
        card.party.forEach { mon ->
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(mon.nickname, Modifier.weight(1f), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (!mon.nickname.equals(mon.species, ignoreCase = true)) {
                    Text(mon.species, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                    Spacer(Modifier.width(10.dp))
                }
                Text("Lv ${mon.level}", fontSize = 14.sp)
            }
        }
    }
}

/** Rounded panel. */
@Composable
fun Panel(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(modifier, shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Box(Modifier.padding(18.dp)) { content() }
    }
}
