package io.github.hiramrg.hermes.core

/** The Generation I character set (international releases). */
object Gen1Text {
    const val TERMINATOR = 0x50

    private val table: Map<Int, String> = buildMap {
        for (i in 0..25) put(0x80 + i, ('A' + i).toString())
        for (i in 0..25) put(0xA0 + i, ('a' + i).toString())
        for (i in 0..9) put(0xF6 + i, ('0' + i).toString())
        put(0x7F, " ")
        put(0x9A, "("); put(0x9B, ")"); put(0x9C, ":"); put(0x9D, ";"); put(0x9E, "["); put(0x9F, "]")
        put(0xBA, "é"); put(0xBB, "'d"); put(0xBC, "'l"); put(0xBD, "'s"); put(0xBE, "'t"); put(0xBF, "'v")
        // German / French / Italian / Spanish releases
        put(0xC0, "Ä"); put(0xC1, "Ö"); put(0xC2, "Ü"); put(0xC3, "ä"); put(0xC4, "ö"); put(0xC5, "ü")
        put(0xE0, "'"); put(0xE1, "PK"); put(0xE2, "MN"); put(0xE3, "-")
        put(0xE6, "?"); put(0xE7, "!"); put(0xE8, ".")
        put(0xEF, "♂"); put(0xF1, "×"); put(0xF2, "."); put(0xF3, "/"); put(0xF4, ","); put(0xF5, "♀")
    }

    /** Decodes [length] bytes starting at [offset], stopping at the terminator. */
    fun decode(bytes: ByteArray, offset: Int, length: Int): String = buildString {
        for (i in offset until minOf(offset + length, bytes.size)) {
            val c = bytes[i].toInt() and 0xFF
            if (c == TERMINATOR) break
            append(table[c] ?: "")
        }
    }

    /** Encodes plain text (A–Z, a–z, 0–9, space) with a terminator, padded to [length]. For tests. */
    fun encode(text: String, length: Int): ByteArray {
        val reverse = table.entries.filter { it.value.length == 1 }.associate { it.value[0] to it.key }
        val out = ByteArray(length) { TERMINATOR.toByte() }
        text.take(length - 1).forEachIndexed { i, ch -> out[i] = (reverse[ch] ?: 0x7F).toByte() }
        return out
    }
}
