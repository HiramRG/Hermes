package io.github.hiramrg.hermes.ui

import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.hiramrg.hermes.platform.Hermes

/** Where the folder pickers start: the Documents folder on internal storage. */
private val DOCUMENTS: Uri = DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", "primary:Documents")

@Composable
fun SetupScreen(dualScreen: Boolean, onDone: () -> Unit) {
    @Suppress("UNUSED_VARIABLE") val v = Hermes.prefsVersion // recompose when prefs change
    val prefs = Hermes.prefs
    val pickStadium = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(Hermes::setStadium) }
    val pickGames = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { it?.let(Hermes::addGameFolder) }
    val pickSlot = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { it?.let(Hermes::setSlotFolder) }
    val first = remember { FocusRequester() }

    val emuOk = Hermes.emulator.isInstalled
    val stadiumOk = prefs.stadiumUri != null
    val gamesOk = Hermes.cartridges.isNotEmpty()
    val slotOk = prefs.slotTree != null
    val linked = prefs.linkVerified
    val slotLabel = prefs.slotTree?.let { Hermes.storage.treeLabel(it) } ?: "Transfer Pak"

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 48.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Welcome to Hermes", fontSize = 32.sp, fontWeight = FontWeight.Black)
        Text(
            "Hermes plugs your Pokémon Red, Blue and Yellow saves into Pokémon Stadium, like the Transfer Pak did. " +
                "Five quick steps and you're set.",
            color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 17.sp,
        )

        Step(1, "Get M64Plus FZ", emuOk,
            if (emuOk) "Installed." else "Hermes uses the M64Plus FZ emulator to run Pokémon Stadium.") {
            if (!emuOk) HButton("Get it on Google Play", { Hermes.emulator.openStore() }, Modifier.focusRequester(first), icon = Icons.Filled.Shop, primary = true)
        }

        Step(2, "Choose Pokémon Stadium", stadiumOk && Hermes.stadium?.isStadium != false,
            when {
                !stadiumOk -> "Pick your Pokémon Stadium game file (.z64, .n64, .v64 or .zip)."
                Hermes.stadium == null -> "${Hermes.stadiumName}: couldn't read it, but we'll try it."
                Hermes.stadium?.isStadium == true -> "${Hermes.stadium?.title} · ${Hermes.stadiumName}"
                else -> "${Hermes.stadiumName} doesn't look like Pokémon Stadium. Pick another file?"
            }) {
            HButton(if (stadiumOk) "Change" else "Choose file", { pickStadium.launch(arrayOf("*/*")) },
                if (emuOk) Modifier.focusRequester(first) else Modifier, icon = Icons.Filled.InsertDriveFile, primary = emuOk && !stadiumOk)
        }

        Step(3, "Find your Game Boy games", gamesOk,
            when {
                Hermes.scanning -> "Looking…"
                prefs.gameFolders.isEmpty() -> "Pick the folder with your Pokémon Red, Blue or Yellow games and their saves (.sav or .srm). If your saves are in another folder, add that one too."
                !gamesOk -> "No Pokémon Red, Blue or Yellow found there yet. Add another folder?"
                else -> "Found " + Hermes.cartridges.joinToString { c -> c.game.label + (c.card?.let { " (${it.name})" } ?: " (no save yet)") } + "."
            }) {
            HButton(if (prefs.gameFolders.isEmpty()) "Choose folder" else "Add another folder", { pickGames.launch(DOCUMENTS) },
                icon = Icons.Filled.FolderOpen, primary = stadiumOk && !gamesOk)
        }

        Step(4, "Make a Transfer Pak folder", slotOk,
            if (slotOk) "Using $slotLabel. Hermes puts the cartridge you choose in here."
            else "Pick an empty folder for Hermes' virtual Transfer Pak. Tip: in the picker, make a new folder called \"Transfer Pak\".") {
            HButton(if (slotOk) "Change" else "Choose folder", { pickSlot.launch(DOCUMENTS) },
                icon = Icons.Filled.CreateNewFolder, primary = gamesOk && !slotOk)
        }

        Step(5, "Plug the Transfer Pak into Stadium", linked,
            if (linked) "Working! Stadium read the cartridge through Hermes."
            else if (dualScreen) "M64Plus FZ opens on the top screen and the steps appear down here on the bottom screen. It takes a minute, once."
            else "Open M64Plus FZ and follow these steps:") {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HButton("Open M64Plus FZ", { Hermes.openEmulatorForSetup() }, enabled = emuOk && slotOk,
                    icon = Icons.Filled.OpenInNew, primary = slotOk && !linked)
                HButton("Add Stadium to M64Plus FZ", { Hermes.addStadiumToEmulator() }, enabled = emuOk && stadiumOk)
                HButton("Test it", { Hermes.selected?.let { Hermes.play(it) } },
                    enabled = slotOk && Hermes.cartridges.any { it.hasUsableSave }, icon = Icons.Filled.PlayArrow)
            }
        }
        if (!dualScreen && slotOk && !linked) Panel(Modifier.fillMaxWidth()) { LinkGuide(slotLabel) }

        Spacer(Modifier.height(4.dp))
        HButton(
            if (linked) "Done" else "Done (I'll test it later)",
            onDone,
            enabled = emuOk && stadiumOk && gamesOk && slotOk,
            primary = linked,
            icon = Icons.Filled.Check,
        )
    }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
}

@Composable
private fun Step(n: Int, title: String, done: Boolean, detail: String, action: @Composable () -> Unit) {
    Panel(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(40.dp).background(
                    if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant, CircleShape,
                ),
                contentAlignment = Alignment.Center,
            ) {
                if (done) Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.onPrimary)
                else Text("$n", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Spacer(Modifier.height(2.dp))
                Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 15.sp)
            }
            Spacer(Modifier.width(16.dp))
            action()
        }
    }
}
