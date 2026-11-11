package io.github.hiramrg.hermes.core

data class PartyMon(val species: String, val nickname: String, val level: Int)

/** What the app shows about a save: a trainer card and the party. */
data class TrainerCard(
    val name: String,
    val trainerId: Int,
    val rival: String,
    val money: Int,
    /** Bit 0 = Boulder Badge … bit 7 = Earth Badge. */
    val badges: Int,
    val owned: Int,
    val seen: Int,
    val hours: Int,
    val minutes: Int,
    val party: List<PartyMon>,
) {
    val badgeCount: Int get() = Integer.bitCount(badges and 0xFF)
    val playTime: String get() = "%d:%02d".format(hours, minutes)
    val idText: String get() = "%05d".format(trainerId)
}

/**
 * Reads Pokémon Red / Blue / Yellow save files (international releases; Japanese saves use a
 * different layout). Offsets: Bulbapedia, "Save data structure (Generation I)".
 */
object Gen1Save {
    /** 32 KB of cartridge RAM. Some emulators add a few bytes at the end, which are ignored. */
    const val SIZE = 0x8000

    private const val PLAYER_NAME = 0x2598
    private const val DEX_OWNED = 0x25A3
    private const val DEX_SEEN = 0x25B6
    private const val MONEY = 0x25F3
    private const val RIVAL_NAME = 0x25F6
    private const val BADGES = 0x2602
    private const val PLAYER_ID = 0x2605
    private const val HOURS = 0x2CED
    private const val MINUTES = 0x2CEF
    private const val PARTY = 0x2F2C
    private const val PARTY_DATA = PARTY + 0x08
    private const val PARTY_NICKNAMES = PARTY + 0x152
    private const val PARTY_ENTRY = 0x2C
    private const val NAME_LENGTH = 11

    const val CHECKSUM_FROM = 0x2598
    const val CHECKSUM_TO = 0x3522
    const val CHECKSUM_AT = 0x3523

    private fun u8(b: ByteArray, i: Int) = b[i].toInt() and 0xFF

    /** The game's own checksum over the main data: add every byte, then invert. */
    fun checksum(b: ByteArray): Int {
        var sum = 0
        for (i in CHECKSUM_FROM..CHECKSUM_TO) sum += u8(b, i)
        return sum.inv() and 0xFF
    }

    /** Right size and the game would accept it ("Continue" would appear). */
    fun isValid(b: ByteArray): Boolean = b.size >= SIZE && checksum(b) == u8(b, CHECKSUM_AT)

    /** Writes the correct checksum (used by tests to build saves). */
    fun fixChecksum(b: ByteArray) { b[CHECKSUM_AT] = checksum(b).toByte() }

    private fun bcd(b: ByteArray, at: Int, count: Int): Int {
        var v = 0
        for (i in at until at + count) {
            val x = u8(b, i)
            v = v * 100 + (x shr 4) * 10 + (x and 0x0F)
        }
        return v
    }

    /** Counts the first 151 bits of a Pokédex flag array. */
    private fun dexCount(b: ByteArray, at: Int): Int {
        var n = 0
        for (i in 0 until 151) if ((u8(b, at + i / 8) shr (i % 8)) and 1 == 1) n++
        return n
    }

    /** Null if the file is too small or its checksum is wrong (empty or damaged save). */
    fun parse(b: ByteArray): TrainerCard? {
        if (!isValid(b)) return null
        val count = u8(b, PARTY).coerceAtMost(6)
        val party = (0 until count).mapNotNull { i ->
            val index = u8(b, PARTY_DATA + i * PARTY_ENTRY)
            if (!Species.isValid(index)) return@mapNotNull null
            PartyMon(
                species = Species.name(index),
                nickname = Gen1Text.decode(b, PARTY_NICKNAMES + i * NAME_LENGTH, NAME_LENGTH),
                level = u8(b, PARTY_DATA + i * PARTY_ENTRY + 0x21),
            )
        }
        return TrainerCard(
            name = Gen1Text.decode(b, PLAYER_NAME, NAME_LENGTH),
            trainerId = (u8(b, PLAYER_ID) shl 8) or u8(b, PLAYER_ID + 1),
            rival = Gen1Text.decode(b, RIVAL_NAME, NAME_LENGTH),
            money = bcd(b, MONEY, 3),
            badges = Integer.reverse(u8(b, BADGES)) ushr 24, // stored Boulder = MSB
            owned = dexCount(b, DEX_OWNED),
            seen = dexCount(b, DEX_SEEN),
            hours = u8(b, HOURS),
            minutes = u8(b, MINUTES),
            party = party,
        )
    }

    /** Test helper: a valid save with a name and party. Each mon is (internal index, nickname, level). */
    fun build(name: String, party: List<Triple<Int, String, Int>>, badges: Int = 0, hours: Int = 0, minutes: Int = 0): ByteArray {
        val b = ByteArray(SIZE)
        Gen1Text.encode(name, NAME_LENGTH).copyInto(b, PLAYER_NAME)
        Gen1Text.encode("BLUE", NAME_LENGTH).copyInto(b, RIVAL_NAME)
        b[BADGES] = badges.toByte()
        b[HOURS] = hours.toByte()
        b[MINUTES] = minutes.toByte()
        b[PARTY] = party.size.toByte()
        party.forEachIndexed { i, (index, nick, level) ->
            b[PARTY + 1 + i] = index.toByte()
            b[PARTY_DATA + i * PARTY_ENTRY] = index.toByte()
            b[PARTY_DATA + i * PARTY_ENTRY + 0x21] = level.toByte()
            Gen1Text.encode(nick, NAME_LENGTH).copyInto(b, PARTY_NICKNAMES + i * NAME_LENGTH)
        }
        b[PARTY + 1 + party.size] = 0xFF.toByte()
        fixChecksum(b)
        return b
    }
}
