package io.github.hiramrg.hermes.core

/** What the header of an N64 ROM says. */
data class N64Header(val title: String, val gameCode: String) {
    /** Last letter of the game code: E = North America, P/D/F/I/S = Europe, J = Japan. */
    val region: Char get() = gameCode.lastOrNull() ?: '?'
    val isJapanese: Boolean get() = region == 'J'

    val isStadium: Boolean get() = title.uppercase().replace(" ", "").startsWith("POKEMONSTADIUM")
    val isStadium2: Boolean get() = title.uppercase().replace(" ", "").startsWith("POKEMONSTADIUM2")
}

object N64Rom {
    /** Bytes needed to read the header. */
    const val HEADER_SIZE = 0x40

    /**
     * N64 ROMs come in three byte orders. Returns the first [HEADER_SIZE] bytes in the native
     * (big-endian, .z64) order, or null if the file doesn't start like an N64 ROM.
     */
    fun normalize(bytes: ByteArray): ByteArray? {
        if (bytes.size < HEADER_SIZE) return null
        val head = bytes.copyOfRange(0, HEADER_SIZE)
        val b0 = head[0].toInt() and 0xFF
        val b1 = head[1].toInt() and 0xFF
        return when {
            b0 == 0x80 && b1 == 0x37 -> head // .z64
            b0 == 0x37 && b1 == 0x80 -> ByteArray(HEADER_SIZE) { i -> head[i xor 1] } // .v64: 16-bit swapped
            b0 == 0x40 && b1 == 0x12 -> ByteArray(HEADER_SIZE) { i -> head[(i and 3.inv()) or (3 - (i and 3))] } // .n64: 32-bit swapped
            else -> null
        }
    }

    fun readHeader(bytes: ByteArray): N64Header? {
        val h = normalize(bytes) ?: return null
        fun text(from: Int, to: Int) = buildString {
            for (i in from until to) {
                val c = h[i].toInt() and 0xFF
                if (c in 0x20..0x7E) append(c.toChar())
            }
        }.trim()
        return N64Header(title = text(0x20, 0x34), gameCode = text(0x3B, 0x3F))
    }

    /** True if a Game Boy cart from that region can talk to this Stadium (Japanese only with Japanese). */
    fun regionsMatch(stadium: N64Header, cart: GbHeader): Boolean = stadium.isJapanese == cart.isJapanese
}
