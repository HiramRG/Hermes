package io.github.hiramrg.hermes.core

/** The Game Boy games Pokémon Stadium can read through the Transfer Pak. */
enum class PokemonGame(val label: String) {
    RED("Red"),
    BLUE("Blue"),
    YELLOW("Yellow"),
    GREEN("Green"),
}

/** What the cartridge header of a Game Boy ROM says. */
data class GbHeader(
    val title: String,
    /** Destination code 0x00 = sold in Japan. Japanese carts only work with the Japanese Stadium. */
    val isJapanese: Boolean,
    val headerChecksumOk: Boolean,
) {
    val game: PokemonGame? get() = GbRom.identify(title)
}

object GbRom {
    /** Everything up to the end of the cartridge header. */
    const val HEADER_END = 0x150

    private const val TITLE_START = 0x134
    private const val TITLE_END = 0x143 // exclusive: 0x143 is the Color flag on newer carts (Yellow sets it)
    private const val DESTINATION = 0x14A
    private const val HEADER_CHECKSUM = 0x14D

    fun readHeader(bytes: ByteArray): GbHeader? {
        if (bytes.size < HEADER_END) return null
        val title = buildString {
            for (i in TITLE_START until TITLE_END) {
                val c = bytes[i].toInt() and 0xFF
                if (c == 0) break
                if (c in 0x20..0x7E) append(c.toChar())
            }
        }.trim()
        var check = 0
        for (i in TITLE_START until HEADER_CHECKSUM) check = (check - (bytes[i].toInt() and 0xFF) - 1) and 0xFF
        return GbHeader(
            title = title,
            isJapanese = bytes[DESTINATION].toInt() == 0,
            headerChecksumOk = check == (bytes[HEADER_CHECKSUM].toInt() and 0xFF),
        )
    }

    /**
     * Names the game from its header title, including the European translations
     * (ROT/ROUGE/ROSSO/ROJO, BLAU/BLEU/BLU/AZUL, GELB/JAUNE/GIALLO/AMARILLO).
     */
    fun identify(title: String): PokemonGame? {
        val t = title.uppercase()
        if (!t.startsWith("POKEMON")) return null
        val rest = t.removePrefix("POKEMON").trim()
        return when (rest) {
            "RED", "ROT", "ROUGE", "ROSSO", "ROJO" -> PokemonGame.RED
            "BLUE", "BLAU", "BLEU", "BLU", "AZUL" -> PokemonGame.BLUE
            "YELLOW", "GELB", "JAUNE", "GIALLO", "AMARILLO" -> PokemonGame.YELLOW
            "GREEN" -> PokemonGame.GREEN
            else -> null
        }
    }
}
