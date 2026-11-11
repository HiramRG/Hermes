package io.github.hiramrg.hermes.platform

import android.net.Uri
import io.github.hiramrg.hermes.core.GbHeader
import io.github.hiramrg.hermes.core.GbRom
import io.github.hiramrg.hermes.core.Gen1Save
import io.github.hiramrg.hermes.core.PokemonGame
import io.github.hiramrg.hermes.core.TrainerCard

/** A Pokémon Game Boy game the user owns: its ROM and (hopefully) its save. */
data class Cartridge(
    val romUri: Uri,
    val romName: String,
    val header: GbHeader,
    val game: PokemonGame,
    val saveUri: Uri?,
    val saveName: String?,
    val saveModified: Long,
    /** Null when there's no save, or it's empty/damaged. */
    val card: TrainerCard?,
) {
    val key: String get() = romUri.toString()
    val title: String get() = "Pokémon ${game.label}"
    val hasUsableSave: Boolean get() = card != null
}

/** Finds Pokémon Red / Blue / Yellow ROMs and their saves in the folders the user picked. */
class Library(private val storage: Storage, private val prefs: Prefs) {

    companion object {
        val ROM_EXTENSIONS = setOf("gb", "gbc", "sgb")
        val SAVE_EXTENSIONS = setOf("sav", "srm")
    }

    fun scan(): List<Cartridge> {
        val files = prefs.gameFolders.flatMap { storage.listFiles(it) }
            .filterNot { it.name == TransferPak.SLOT_ROM || it.name == TransferPak.SLOT_SAVE }
            .distinctBy { it.uri }
        val saves = files.filter { it.extension in SAVE_EXTENSIONS }
        val manual = prefs.manualSaves

        return files.filter { it.extension in ROM_EXTENSIONS }.mapNotNull { rom ->
            val head = storage.readBytes(rom.uri, GbRom.HEADER_END) ?: return@mapNotNull null
            val header = GbRom.readHeader(head) ?: return@mapNotNull null
            val game = header.game ?: return@mapNotNull null

            val manualUri = manual[rom.uri.toString()]?.let(Uri::parse)
            val save: Pair<Uri, String>? = if (manualUri != null) {
                manualUri to storage.displayName(manualUri)
            } else {
                saves.filter { it.baseName == rom.baseName }
                    // Same folder first (".sav next to the ROM"), then the newest (e.g. a RetroArch saves folder).
                    .sortedWith(compareByDescending<DocFile> { it.parentId == rom.parentId }.thenByDescending { it.lastModified })
                    .firstOrNull()?.let { it.uri to it.name }
            }
            val saveBytes = save?.let { storage.readBytes(it.first) }
            Cartridge(
                romUri = rom.uri,
                romName = rom.name,
                header = header,
                game = game,
                saveUri = save?.first,
                saveName = save?.second,
                saveModified = save?.let { storage.lastModified(it.first) } ?: 0L,
                card = saveBytes?.let { Gen1Save.parse(it) },
            )
        }.sortedWith(compareBy({ it.game.ordinal }, { it.romName.lowercase() }))
    }
}
