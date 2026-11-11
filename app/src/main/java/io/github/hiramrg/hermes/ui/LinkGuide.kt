package io.github.hiramrg.hermes.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.hiramrg.hermes.platform.TransferPak

/** "Plain **bold** plain" → styled text. */
@Composable
fun rich(text: String): AnnotatedString {
    val accent = MaterialTheme.colorScheme.primary
    return buildAnnotatedString {
        text.split("**").forEachIndexed { i, part ->
            if (i % 2 == 1) withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = accent)) { append(part) } else append(part)
        }
    }
}

/** The steps inside M64Plus FZ, worded exactly like its menus. */
fun linkSteps(folder: String): List<String> = listOf(
    "In M64Plus FZ, tap **Pokémon Stadium** in your games. (Not in the list? Tap **Add Stadium to M64Plus FZ** in Hermes, then exit the game.)",
    "In the menu that opens, tap **Settings**.",
    "Open **Controller**.",
    "Under **Controller Paks**, tap **Player 1** and choose **Transfer Pak**.",
    "Under **Transfer Paks**, tap **Player 1 Gameboy ROM** and pick **${TransferPak.SLOT_ROM}** in the **$folder** folder.",
    "Tap **Player 1 Gameboy RAM** and pick **${TransferPak.SLOT_SAVE}** in the same folder.",
    "Come back to Hermes and tap **Test it**. You only do this once.",
)

@Composable
fun LinkGuide(folder: String, big: Boolean = false) {
    Column(verticalArrangement = Arrangement.spacedBy(if (big) 12.dp else 8.dp)) {
        linkSteps(folder).forEachIndexed { i, step ->
            Row(verticalAlignment = Alignment.Top) {
                Box(
                    Modifier.size(if (big) 30.dp else 24.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("${i + 1}", fontWeight = FontWeight.Bold, fontSize = if (big) 16.sp else 13.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                Spacer(Modifier.width(12.dp))
                Text(rich(step), Modifier.fillMaxWidth().padding(top = 2.dp), fontSize = if (big) 18.sp else 15.sp, lineHeight = if (big) 25.sp else 21.sp)
            }
        }
    }
}
