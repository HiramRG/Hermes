package io.github.hiramrg.hermes.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.hiramrg.hermes.platform.Backup
import io.github.hiramrg.hermes.platform.Cartridge
import io.github.hiramrg.hermes.platform.Hermes
import io.github.hiramrg.hermes.platform.UpdateState
import java.text.DateFormat
import java.util.Date

@Composable
private fun ScreenHeader(title: String, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        HButton("Back", onBack, icon = Icons.AutoMirrored.Filled.ArrowBack)
        Spacer(Modifier.width(18.dp))
        Text(title, fontSize = 28.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
fun BackupsScreen(cart: Cartridge, onBack: () -> Unit) {
    var refresh by remember { mutableIntStateOf(0) }
    val list = remember(cart.key, refresh, Hermes.cartridges) { Hermes.backups.list(cart.key) }
    var confirm by remember { mutableStateOf<Backup?>(null) }
    val fmt = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 36.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ScreenHeader("Backups · ${cart.title}", onBack)
        Text(
            "Hermes copies this save before every Stadium session and before writing anything back. The newest 15 are kept.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (list.isEmpty()) Text("No backups yet.", fontSize = 18.sp)
        list.forEach { b ->
            Panel(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(fmt.format(Date(b.time)), fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        Text(b.reason + (b.trainer?.let { " · $it" } ?: ""), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                    }
                    HButton("Restore", { confirm = b }, icon = Icons.Filled.Restore, enabled = cart.saveUri != null)
                }
            }
        }
    }
    confirm?.let { b ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text("Restore this backup?") },
            text = { Text("Your ${cart.title} save will be replaced by the one from ${fmt.format(Date(b.time))}. The current save is backed up first.") },
            confirmButton = { TextButton({ Hermes.restore(cart, b); confirm = null; refresh++ }) { Text("Restore") } },
            dismissButton = { TextButton({ confirm = null }) { Text("Cancel") } },
        )
    }
}

@Composable
fun SettingsScreen(onBack: () -> Unit, onSetup: () -> Unit) {
    @Suppress("UNUSED_VARIABLE") val v = Hermes.prefsVersion
    val prefs = Hermes.prefs
    val pickStadium = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(Hermes::setStadium) }
    val pickGames = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { it?.let(Hermes::addGameFolder) }
    val pickSlot = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { it?.let(Hermes::setSlotFolder) }
    var swap by remember { mutableStateOf(prefs.swapScreens) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 36.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        ScreenHeader("Settings", onBack)

        Panel(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Pokémon Stadium", fontWeight = FontWeight.Bold, fontSize = 19.sp)
                Text(Hermes.stadium?.title?.let { "$it · ${Hermes.stadiumName}" } ?: Hermes.stadiumName ?: "Not chosen",
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                HButton("Choose another file", { pickStadium.launch(arrayOf("*/*")) })
            }
        }

        Panel(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Game Boy game folders", fontWeight = FontWeight.Bold, fontSize = 19.sp)
                prefs.gameFolders.forEach { f ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(Hermes.storage.treeLabel(f), Modifier.weight(1f))
                        HButton("Remove", { Hermes.removeGameFolder(f) }, icon = Icons.Filled.Delete)
                    }
                }
                HButton("Add a folder", { pickGames.launch(null) }, icon = Icons.Filled.FolderOpen)
            }
        }

        Panel(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Transfer Pak", fontWeight = FontWeight.Bold, fontSize = 19.sp)
                Text(
                    (prefs.slotTree?.let { "Folder: " + Hermes.storage.treeLabel(it) } ?: "No folder chosen") +
                        if (prefs.linkVerified) " · working" else " · not tested yet",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    HButton("Set up the Transfer Pak", onSetup)
                    HButton("Choose another folder", { pickSlot.launch(null) })
                }
                Text("If you change the folder, do the M64Plus FZ steps again so Stadium uses the new one.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            }
        }

        Panel(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Swap screens", fontWeight = FontWeight.Bold, fontSize = 19.sp)
                    Text("Run Stadium on the bottom screen and the guide on the top one.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = swap, onCheckedChange = { swap = it; prefs.swapScreens = it })
            }
        }

        Panel(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Updates", fontWeight = FontWeight.Bold, fontSize = 19.sp)
                val u = Hermes.update
                Text(
                    "Installed: version ${Hermes.updater.installedVersionName()}. " + when (u) {
                        UpdateState.Checking -> "Checking…"
                        is UpdateState.Available -> "A new version is ready."
                        is UpdateState.Downloading -> "Downloading… ${(u.progress * 100).toInt()}%"
                        UpdateState.Installing -> "Installing…"
                        UpdateState.UpToDate -> "You have the latest version."
                        is UpdateState.Failed -> "Couldn't update: ${u.reason}"
                        UpdateState.Idle -> ""
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (u is UpdateState.Available) HButton("Download and install", { Hermes.installUpdate(u.release) }, primary = true)
                else HButton("Check for updates", { Hermes.checkForUpdate(silent = false) },
                    enabled = u !is UpdateState.Checking && u !is UpdateState.Downloading && u !is UpdateState.Installing)
            }
        }

        Text("Backups use %.1f MB.".format(Hermes.backups.totalSize() / 1_048_576.0), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        Text(
            "Hermes is an independent project, not affiliated with Nintendo, The Pokémon Company or the M64Plus FZ developers. " +
                "Use your own game backups.",
            color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp,
        )
    }
}
