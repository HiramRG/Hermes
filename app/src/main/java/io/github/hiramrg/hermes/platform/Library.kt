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

    private fun normalize(name: String): String =
        name.substringBeforeLast('.').lowercase()
            .replace(Regex("\\(.*?\\)|\\[.*?]"), "") // "(USA, Europe)", "[!]"
            .replace(Regex("[^a-z0-9]"), "")

    private val keywords = mapOf(
        PokemonGame.RED to listOf("red", "rot", "rouge", "rosso", "rojo"),
        PokemonGame.BLUE to listOf("blue", "blau", "bleu", "blu", "azul"),
        PokemonGame.YELLOW to listOf("yellow", "gelb", "jaune", "giallo", "amarillo"),
        PokemonGame.GREEN to listOf("green", "midori"),
    )

    /**
     * 3: same file name. 2: same name once region tags and punctuation are ignored
     * ("Pokemon - Red Version (USA).gb" ~ "Pokemon Red Version.sav"). 1: the save's name names
     * the same game ("pokemon red.srm") and it holds a real Gen 1 save. 0: no match.
     */
    private fun matchScore(rom: DocFile, save: DocFile, game: PokemonGame): Int {
        if (save.baseName == rom.baseName) return 3
        val s = normalize(save.name)
        if (s.isNotEmpty() && s == normalize(rom.name)) return 2
        val names = keywords[game].orEmpty()
        val others = keywords.filterKeys { it != game }.values.flatten().filter { k -> names.none { k in it } }
        if (names.any { it in s } && others.none { it in s }) {
            val bytes = storage.readBytes(save.uri) ?: return 0
            if (Gen1Save.isValid(bytes)) return 1
        }
        return 0
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
                saves.map { it to matchScore(rom, it, game) }
                    .filter { it.second > 0 }
                    // Best name match, then same folder (".sav next to the ROM"), then newest.
                    .sortedWith(
                        compareByDescending<Pair<DocFile, Int>> { it.second }
                            .thenByDescending { it.first.parentId == rom.parentId }
                            .thenByDescending { it.first.lastModified },
                    )
                    .firstOrNull()?.first?.let { it.uri to it.name }
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
