package io.github.hiramrg.hermes.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreTest {

    // ---------------------------------------------------------------- species & text

    @Test fun speciesTableHasAll151() {
        assertEquals(151, Species.allNames.size)
        assertEquals(151, Species.allNames.toSet().size)
        assertEquals("Pikachu", Species.name(0x54))
        assertEquals("Bulbasaur", Species.name(0x99))
        assertEquals("Mew", Species.name(0x15))
        assertEquals("Victreebel", Species.name(0xBE))
        assertEquals("MissingNo.", Species.name(0x1F))
    }

    @Test fun textRoundTrips() {
        val bytes = Gen1Text.encode("ASH red9", 11)
        assertEquals("ASH red9", Gen1Text.decode(bytes, 0, 11))
    }

    // ---------------------------------------------------------------- saves

    @Test fun parsesTrainerAndParty() {
        val save = Gen1Save.build(
            "ASH",
            listOf(Triple(0x54, "PIKACHU", 25), Triple(0xB4, "CHARIZARD", 52)),
            badges = 0b1100_0000, // Boulder + Cascade (stored MSB first)
            hours = 12, minutes = 7,
        )
        val card = Gen1Save.parse(save)
        assertNotNull(card)
        card!!
        assertEquals("ASH", card.name)
        assertEquals("BLUE", card.rival)
        assertEquals(2, card.badgeCount)
        assertEquals(0b11, card.badges) // Boulder = bit 0, Cascade = bit 1
        assertEquals("12:07", card.playTime)
        assertEquals(2, card.party.size)
        assertEquals(PartyMon("Pikachu", "PIKACHU", 25), card.party[0])
        assertEquals(PartyMon("Charizard", "CHARIZARD", 52), card.party[1])
    }

    @Test fun rejectsBadChecksumAndShortFiles() {
        val save = Gen1Save.build("ASH", emptyList())
        assertTrue(Gen1Save.isValid(save))
        save[0x2600] = (save[0x2600] + 1).toByte()
        assertFalse(Gen1Save.isValid(save))
        assertNull(Gen1Save.parse(save))
        assertFalse(Gen1Save.isValid(ByteArray(1000)))
    }

    @Test fun acceptsEmulatorFooter() {
        val save = Gen1Save.build("RED", emptyList()) + ByteArray(48)
        assertTrue(Gen1Save.isValid(save))
    }

    @Test fun findsSavesBehindAHeader() {
        val save = Gen1Save.build("RED", listOf(Triple(0x54, "PIKACHU", 5)))
        val withHeader = ByteArray(0x40) + save
        assertEquals(0x40, Gen1Save.locate(withHeader))
        assertEquals("RED", Gen1Save.parse(withHeader)!!.name)
        assertTrue(Gen1Save.extract(withHeader)!!.contentEquals(save))
        assertTrue(Gen1Save.diagnose(ByteArray(Gen1Save.SIZE)).contains("empty"))
    }

    // ---------------------------------------------------------------- ROM headers

    private fun gbRom(title: String, japanese: Boolean = false): ByteArray {
        val b = ByteArray(GbRom.HEADER_END)
        title.forEachIndexed { i, c -> b[0x134 + i] = c.code.toByte() }
        b[0x14A] = if (japanese) 0 else 1
        var x = 0
        for (i in 0x134 until 0x14D) x = (x - (b[i].toInt() and 0xFF) - 1) and 0xFF
        b[0x14D] = x.toByte()
        return b
    }

    @Test fun identifiesGameBoyGames() {
        assertEquals(PokemonGame.RED, GbRom.readHeader(gbRom("POKEMON RED"))!!.game)
        assertEquals(PokemonGame.BLUE, GbRom.readHeader(gbRom("POKEMON BLUE"))!!.game)
        val yellow = gbRom("POKEMON YELLOW").also { it[0x143] = 0x80.toByte() } // Color flag
        assertEquals(PokemonGame.YELLOW, GbRom.readHeader(yellow)!!.game)
        assertEquals(PokemonGame.RED, GbRom.identify("POKEMON ROT"))
        assertEquals(PokemonGame.YELLOW, GbRom.identify("POKEMON JAUNE"))
        assertNull(GbRom.identify("POKEMON GOLD"))
        assertNull(GbRom.identify("TETRIS"))
        assertTrue(GbRom.readHeader(gbRom("POKEMON RED"))!!.headerChecksumOk)
        assertTrue(GbRom.readHeader(gbRom("POKEMON RED", japanese = true))!!.isJapanese)
    }

    private fun z64(title: String, code: String): ByteArray {
        val b = ByteArray(N64Rom.HEADER_SIZE)
        b[0] = 0x80.toByte(); b[1] = 0x37; b[2] = 0x12; b[3] = 0x40
        title.forEachIndexed { i, c -> b[0x20 + i] = c.code.toByte() }
        code.forEachIndexed { i, c -> b[0x3B + i] = c.code.toByte() }
        return b
    }

    @Test fun readsN64HeadersInEveryByteOrder() {
        val z = z64("POKEMON STADIUM", "NPOE")
        val v = ByteArray(z.size) { z[it xor 1] }
        val n = ByteArray(z.size) { z[(it and 3.inv()) or (3 - (it and 3))] }
        for (rom in listOf(z, v, n)) {
            val h = N64Rom.readHeader(rom)!!
            assertTrue(h.isStadium)
            assertFalse(h.isStadium2)
            assertEquals('E', h.region)
        }
        assertTrue(N64Rom.readHeader(z64("POKEMON STADIUM 2", "NP3E"))!!.isStadium2)
        assertNull(N64Rom.readHeader(ByteArray(64)))
    }

    @Test fun regionCheck() {
        val us = N64Rom.readHeader(z64("POKEMON STADIUM", "NPOE"))!!
        assertTrue(N64Rom.regionsMatch(us, GbRom.readHeader(gbRom("POKEMON RED"))!!))
        assertFalse(N64Rom.regionsMatch(us, GbRom.readHeader(gbRom("POKEMON RED", japanese = true))!!))
    }

    // ---------------------------------------------------------------- sync decisions

    @Test fun syncDecisions() {
        val original = Gen1Save.build("ASH", listOf(Triple(0x54, "PIKACHU", 25)))
        val afterStadium = Gen1Save.build("ASH", listOf(Triple(0x54, "PIKACHU", 26)))
        val playedElsewhere = Gen1Save.build("ASH", listOf(Triple(0x54, "PIKACHU", 30)))
        val h = SyncPlan.hash(original)

        assertEquals(SyncAction.NOTHING_NEW, SyncPlan.decide(h, original, h, h))
        assertEquals(SyncAction.COPY_TO_ORIGINAL, SyncPlan.decide(h, afterStadium, h, h))
        assertEquals(SyncAction.COPY_TO_ORIGINAL, SyncPlan.decide(h, afterStadium, h, null))
        assertEquals(SyncAction.CONFLICT, SyncPlan.decide(h, afterStadium, h, SyncPlan.hash(playedElsewhere)))
        assertEquals(SyncAction.SLOT_INVALID, SyncPlan.decide(h, null, h, h))
        assertEquals(SyncAction.SLOT_INVALID, SyncPlan.decide(h, ByteArray(Gen1Save.SIZE) { 1 }, h, h))
    }
}
