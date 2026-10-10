package com.nbp.cobblemon_smartphone.util

import com.cobblemon.mod.common.pokemon.Species
import net.minecraft.resources.ResourceLocation
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PokeInfoSpeciesSortTest {
    @Test
    fun `canonical species identification differentiates official and beta pokemon`() {
        val bulbasaurLabels = listOf("gen1", "starter")
        val barundaLabels = listOf("gen1beta")
        val customMonLabels = emptyList<String>()

        assertTrue(PokeInfoDataProvider.isCanonicalSpecies(bulbasaurLabels))
        assertFalse(PokeInfoDataProvider.isCanonicalSpecies(barundaLabels))
        assertFalse(PokeInfoDataProvider.isCanonicalSpecies(customMonLabels))
    }
}
