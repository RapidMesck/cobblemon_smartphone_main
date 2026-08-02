package com.nbp.cobblemon_smartphone.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PokeInfoEvolutionFormattingTest {
    @Test
    fun `bisharp defeat requirement remains structured and translatable`() {
        val text = PokeInfoDataProvider.defeatRequirementText(
            amount = 3,
            species = "bisharp",
            heldItem = "cobblemon:kings_rock",
            type = null,
            fallback = ""
        )

        assertEquals("cobblemon_smartphone.pokeinfo.evolution.requirement.defeat_holding", text.key)
        assertEquals(listOf("3", "pokemon:bisharp", "item:cobblemon:kings_rock"), text.args)
    }
}
