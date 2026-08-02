package com.nbp.cobblemon_smartphone.client

import com.nbp.cobblemon_smartphone.network.packet.SpeciesDetailResponsePacket
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class PokeInfoClientStateTest {
    @Test
    fun `ignores stale and wrong species responses`() {
        val expectedId = PokeInfoClientState.nextRequestId()
        PokeInfoClientState.beginDetailRequest(expectedId, 25)

        val stale = SpeciesDetailResponsePacket(
            expectedId - 1, 25, SpeciesDetailResponsePacket.Status.SUCCESS, emptyList()
        )
        val wrongSpecies = SpeciesDetailResponsePacket(
            expectedId, 26, SpeciesDetailResponsePacket.Status.SUCCESS, emptyList()
        )
        PokeInfoClientState.accept(stale)
        PokeInfoClientState.accept(wrongSpecies)

        assertNull(PokeInfoClientState.consumeDetailResponse(expectedId))

        val expected = SpeciesDetailResponsePacket(
            expectedId, 25, SpeciesDetailResponsePacket.Status.NOT_FOUND, emptyList()
        )
        PokeInfoClientState.accept(expected)
        assertSame(expected, PokeInfoClientState.consumeDetailResponse(expectedId))
        assertNull(PokeInfoClientState.consumeDetailResponse(expectedId))
    }
}
