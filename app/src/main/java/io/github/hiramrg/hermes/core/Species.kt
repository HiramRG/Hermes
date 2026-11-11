package io.github.hiramrg.hermes.core

/**
 * Generation I stores species by an internal index, not the Pokédex number.
 * Index → name; empty strings are unused indexes ("MissingNo.").
 */
object Species {
    private val byIndex: Array<String> = arrayOf(
        "", // 0x00
        "Rhydon", "Kangaskhan", "Nidoran♂", "Clefairy", "Spearow", "Voltorb", "Nidoking", "Slowbro", // 0x01-0x08
        "Ivysaur", "Exeggutor", "Lickitung", "Exeggcute", "Grimer", "Gengar", "Nidoran♀", "Nidoqueen", // 0x09-0x10
        "Cubone", "Rhyhorn", "Lapras", "Arcanine", "Mew", "Gyarados", "Shellder", "Tentacool", // 0x11-0x18
        "Gastly", "Scyther", "Staryu", "Blastoise", "Pinsir", "Tangela", "", "", // 0x19-0x20
        "Growlithe", "Onix", "Fearow", "Pidgey", "Slowpoke", "Kadabra", "Graveler", "Chansey", // 0x21-0x28
        "Machoke", "Mr. Mime", "Hitmonlee", "Hitmonchan", "Arbok", "Parasect", "Psyduck", "Drowzee", // 0x29-0x30
        "Golem", "", "Magmar", "", "Electabuzz", "Magneton", "Koffing", "", // 0x31-0x38
        "Mankey", "Seel", "Diglett", "Tauros", "", "", "", "Farfetch'd", // 0x39-0x40
        "Venonat", "Dragonite", "", "", "", "Doduo", "Poliwag", "Jynx", // 0x41-0x48
        "Moltres", "Articuno", "Zapdos", "Ditto", "Meowth", "Krabby", "", "", // 0x49-0x50
        "", "Vulpix", "Ninetales", "Pikachu", "Raichu", "", "", "Dratini", // 0x51-0x58
        "Dragonair", "Kabuto", "Kabutops", "Horsea", "Seadra", "", "", "Sandshrew", // 0x59-0x60
        "Sandslash", "Omanyte", "Omastar", "Jigglypuff", "Wigglytuff", "Eevee", "Flareon", "Jolteon", // 0x61-0x68
        "Vaporeon", "Machop", "Zubat", "Ekans", "Paras", "Poliwhirl", "Poliwrath", "Weedle", // 0x69-0x70
        "Kakuna", "Beedrill", "", "Dodrio", "Primeape", "Dugtrio", "Venomoth", "Dewgong", // 0x71-0x78
        "", "", "Caterpie", "Metapod", "Butterfree", "Machamp", "", "Golduck", // 0x79-0x80
        "Hypno", "Golbat", "Mewtwo", "Snorlax", "Magikarp", "", "", "Muk", // 0x81-0x88
        "", "Kingler", "Cloyster", "", "Electrode", "Clefable", "Weezing", "Persian", // 0x89-0x90
        "Marowak", "", "Haunter", "Abra", "Alakazam", "Pidgeotto", "Pidgeot", "Starmie", // 0x91-0x98
        "Bulbasaur", "Venusaur", "Tentacruel", "", "Goldeen", "Seaking", "", "", // 0x99-0xA0
        "", "", "Ponyta", "Rapidash", "Rattata", "Raticate", "Nidorino", "Nidorina", // 0xA1-0xA8
        "Geodude", "Porygon", "Aerodactyl", "", "Magnemite", "", "", "Charmander", // 0xA9-0xB0
        "Squirtle", "Charmeleon", "Wartortle", "Charizard", "", "", "", "", // 0xB1-0xB8
        "Oddish", "Gloom", "Vileplume", "Bellsprout", "Weepinbell", "Victreebel", // 0xB9-0xBE
    )

    val allNames: List<String> get() = byIndex.filter { it.isNotEmpty() }

    fun name(index: Int): String = byIndex.getOrNull(index)?.takeIf { it.isNotEmpty() } ?: "MissingNo."

    fun isValid(index: Int): Boolean = byIndex.getOrNull(index)?.isNotEmpty() == true
}
