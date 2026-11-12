package io.github.hiramrg.hermes.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.hiramrg.hermes.core.N64Rom
import io.github.hiramrg.hermes.platform.Cartridge
import io.github.hiramrg.hermes.platform.Hermes
import io.github.hiramrg.hermes.platform.Pending
import io.github.hiramrg.hermes.platform.UpdateState

@Composable
fun HomeScreen(onSettings: () -> Unit, onBackups: (Cartridge) -> Unit) {
    @Suppress("UNUSED_VARIABLE") val v = Hermes.prefsVersion
    val carts = Hermes.cartridges
    val selected = Hermes.selected
    val playFocus = remember { FocusRequester() }

    Column(Modifier.fillMaxSize().padding(horizontal = 36.dp, vertical = 22.dp)) {
        // Top bar
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Hermes", fontSize = 30.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.width(16.dp))
            StatusChip()
            Spacer(Modifier.weight(1f))
            HButton("Settings", onSettings, icon = Icons.Filled.Settings)
        }
        Spacer(Modifier.height(12.dp))
        UpdateBanner()
        Hermes.message?.let { MessageBanner(it) { Hermes.message = null }; Spacer(Modifier.height(12.dp)) }
        Hermes.busy?.let { Text(it, color = MaterialTheme.colorScheme.primary, fontSize = 16.sp); Spacer(Modifier.height(8.dp)) }

        if (carts.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (Hermes.scanning) "Looking for your games…" else "No Pokémon Red, Blue or Yellow found", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    if (!Hermes.scanning) {
                        Text("Add the folder with your Game Boy games in Settings.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(16.dp))
                        HButton("Open Settings", onSettings, primary = true)
                    }
                }
            }
            return@Column
        }

        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            // Cartridge shelf
            LazyColumn(Modifier.width(380.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(carts, key = { it.key }) { cart ->
                    CartridgeRow(cart, selected = cart.key == selected?.key) { Hermes.select(cart) }
                }
            }
            // Details
            if (selected != null) {
                Details(selected, playFocus, onBackups)
            }
        }
    }

    PendingDialog()
    LaunchedEffect(selected?.key) { runCatching { playFocus.requestFocus() } }
}

@Composable
private fun StatusChip() {
    val linked = Hermes.prefs.linkVerified
    val inserted = Hermes.prefs.insertion
    val text = when {
        !linked -> "Transfer Pak not tested yet"
        inserted != null -> "In the Transfer Pak: ${inserted.gameLabel}" + (if (inserted.trainer.isNotEmpty()) " (${inserted.trainer})" else "")
        else -> "Transfer Pak ready"
    }
    Surface(
        shape = RoundedCornerShape(50),
        color = if (linked) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Text(text, Modifier.padding(horizontal = 14.dp, vertical = 6.dp), fontSize = 14.sp,
            color = if (linked) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Shown when a newer Hermes is on GitHub, and while it downloads. */
@Composable
private fun UpdateBanner() {
    val text: String
    val action: (() -> Unit)?
    when (val u = Hermes.update) {
        is UpdateState.Available -> { text = "A new version of Hermes is ready."; action = { Hermes.installUpdate(u.release) } }
        is UpdateState.Downloading -> { text = "Downloading the update… ${(u.progress * 100).toInt()}%"; action = null }
        UpdateState.Installing -> { text = "Installing the update…"; action = null }
        else -> return
    }
    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.primaryContainer) {
        Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text, Modifier.weight(1f), fontSize = 16.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
            if (action != null) HButton("Install", action, primary = true)
        }
    }
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun MessageBanner(text: String, onClose: () -> Unit) {
    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)) {
        Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 6.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text, Modifier.weight(1f), fontSize = 16.sp)
            IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = "Dismiss") }
        }
    }
}

@Composable
private fun CartridgeRow(cart: Cartridge, selected: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().onFocusChanged { if (it.isFocused) onClick() },
        shape = RoundedCornerShape(16.dp),
        color = if (selected) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainer,
        border = focusBorder(interaction) ?: if (selected) BorderStroke(2.dp, cart.game.color()) else null,
        interactionSource = interaction,
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            CartridgeArt(cart.game, width = 56.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(cart.title, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text(
                    cart.card?.let { "${it.name} · ${it.badgeCount} badges · ${it.party.size} Pokémon" }
                        ?: if (cart.saveUri == null) "No save found" else "Save is empty or damaged",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp,
                )
            }
        }
    }
}

@Composable
private fun Details(cart: Cartridge, playFocus: FocusRequester, onBackups: (Cartridge) -> Unit) {
    val pickSave = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { Hermes.pairSave(cart, it) } }
    val stadium = Hermes.stadium
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(verticalAlignment = Alignment.Top) {
            CartridgeArt(cart.game, width = 150.dp)
            Spacer(Modifier.width(24.dp))
            Column(Modifier.weight(1f)) {
                Text(cart.title, fontSize = 28.sp, fontWeight = FontWeight.Black)
                Text(cart.romName, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                Text("Save: " + (cart.saveName ?: "none found"), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    HButton("Play in Pokémon Stadium", { Hermes.play(cart) }, Modifier.focusRequester(playFocus),
                        icon = Icons.Filled.PlayArrow, primary = true, enabled = cart.hasUsableSave && Hermes.busy == null)
                    HButton("Backups", { onBackups(cart) }, icon = Icons.Filled.History)
                    HButton(if (cart.saveUri == null) "Choose save file" else "Use a different save", { pickSave.launch(arrayOf("*/*")) }, icon = Icons.Filled.InsertDriveFile)
                }
                if (stadium != null && !N64Rom.regionsMatch(stadium, cart.header)) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        if (cart.header.isJapanese) "This is a Japanese cartridge; your Pokémon Stadium is not Japanese, so Stadium won't accept it."
                        else "Your Pokémon Stadium is Japanese; it only accepts Japanese cartridges.",
                        color = MaterialTheme.colorScheme.error, fontSize = 14.sp,
                    )
                }
                if (!cart.hasUsableSave) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        if (cart.saveUri == null) "Hermes couldn't find a save for this game. Choose its .sav or .srm file."
                        else "This save is empty or damaged. Play the game and save it first.",
                        color = MaterialTheme.colorScheme.error, fontSize = 14.sp,
                    )
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        cart.card?.let { Panel(Modifier.fillMaxWidth()) { TrainerPanel(it) } }
        Spacer(Modifier.height(14.dp))
        Text(
            "When you finish in Stadium, exit the game from M64Plus FZ's menu. Hermes then copies any changes back into this save and keeps a backup of the old one.",
            color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp,
        )
    }
}

@Composable
private fun PendingDialog() {
    when (Hermes.pending) {
        Pending.Conflict -> AlertDialog(
            onDismissRequest = {},
            title = { Text("Two versions of this save") },
            text = {
                Text(
                    "Stadium changed the save, and so did the Game Boy game (it was played somewhere else in the meantime). " +
                        "Which one do you want to keep? The other one is kept in Backups.",
                )
            },
            confirmButton = { TextButton({ Hermes.resolveConflict(keepStadium = true) }) { Text("Keep Stadium's") } },
            dismissButton = { TextButton({ Hermes.resolveConflict(keepStadium = false) }) { Text("Keep the Game Boy one") } },
        )
        Pending.NotHandedBack -> AlertDialog(
            onDismissRequest = { Hermes.dismissPending() },
            title = { Text("Stadium still has the cartridge") },
            text = {
                Column {
                    Text(
                        "M64Plus FZ hasn't given the save back yet. If Stadium is still open, go back to it, open M64Plus FZ's menu " +
                            "and choose Exit. Hermes copies your Pokémon back as soon as it does.",
                    )
                    if (!Hermes.prefs.linkVerified) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "If Stadium said there was no Game Boy cartridge, the Transfer Pak isn't plugged in yet: Settings → Set up the Transfer Pak.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
            confirmButton = {
                Row {
                    TextButton({ Hermes.dismissPending(); Hermes.returnToStadium() }) { Text("Back to Stadium") }
                    TextButton({ Hermes.dismissPending(); Hermes.sync(waitForStadium = true) }) { Text("Check again") }
                }
            },
            dismissButton = {
                val cart = Hermes.lastRequested
                if (cart != null) TextButton({ Hermes.dismissPending(); Hermes.play(cart, force = true) }) { Text("Start anyway") }
                else TextButton({ Hermes.dismissPending() }) { Text("OK") }
            },
        )
        null -> {}
    }
}
