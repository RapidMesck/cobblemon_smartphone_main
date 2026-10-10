package com.nbp.cobblemon_smartphone.client

import com.nbp.cobblemon_smartphone.network.packet.SpeciesDetailResponsePacket
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class PokeInfoClientStateTest {
    @Test
    fun `ignores stale and wrong species responses with speciesId`() {
        val expectedId = PokeInfoClientState.nextRequestId()
        PokeInfoClientState.beginDetailRequest(expectedId, "cobblemon:pikachu")

        val stale = SpeciesDetailResponsePacket(
            expectedId - 1, "cobblemon:pikachu", SpeciesDetailResponsePacket.Status.SUCCESS, emptyList()
        )
        val wrongSpecies = SpeciesDetailResponsePacket(
            expectedId, "cobblemon:raichu", SpeciesDetailResponsePacket.Status.SUCCESS, emptyList()
        )
        PokeInfoClientState.accept(stale)
        PokeInfoClientState.accept(wrongSpecies)

        assertNull(PokeInfoClientState.consumeDetailResponse(expectedId))

        val expected = SpeciesDetailResponsePacket(
            expectedId, "cobblemon:pikachu", SpeciesDetailResponsePacket.Status.NOT_FOUND, emptyList()
        )
        PokeInfoClientState.accept(expected)
        assertSame(expected, PokeInfoClientState.consumeDetailResponse(expectedId))
        assertNull(PokeInfoClientState.consumeDetailResponse(expectedId))
    }

    @Test
    fun `supports backwards compatible dexNumber requests`() {
        val expectedId = PokeInfoClientState.nextRequestId()
        PokeInfoClientState.beginDetailRequest(expectedId, 25)

        val wrongSpecies = SpeciesDetailResponsePacket(
            expectedId, 26, SpeciesDetailResponsePacket.Status.SUCCESS, emptyList()
        )
        PokeInfoClientState.accept(wrongSpecies)
        assertNull(PokeInfoClientState.consumeDetailResponse(expectedId))

        val expected = SpeciesDetailResponsePacket(
            expectedId, 25, SpeciesDetailResponsePacket.Status.SUCCESS, emptyList()
        )
        PokeInfoClientState.accept(expected)
        assertSame(expected, PokeInfoClientState.consumeDetailResponse(expectedId))
    }
}
