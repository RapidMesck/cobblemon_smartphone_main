package com.nbp.cobblemon_smartphone.util

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PokeInfoFormFilterTest {
    @Test
    fun `generic spawn applies to every form`() {
        assertTrue(PokeInfoDataProvider.matchesSpawnForm(null, emptySet(), setOf("alolan"), "Alolan", "alola", setOf("alolan")))
    }

    @Test
    fun `explicit form only applies to matching form`() {
        assertTrue(PokeInfoDataProvider.matchesSpawnForm("alola", emptySet(), setOf("alolan"), "Alolan", "alola", setOf("alolan")))
        assertFalse(PokeInfoDataProvider.matchesSpawnForm("alola", emptySet(), setOf("alolan"), "Normal", "normal", emptySet()))
    }

    @Test
    fun `only aspects belonging to forms affect form filtering`() {
        val known = setOf("alolan", "galarian")
        assertTrue(PokeInfoDataProvider.matchesSpawnForm(null, setOf("shiny"), known, "Normal", "normal", emptySet()))
        assertTrue(PokeInfoDataProvider.matchesSpawnForm(null, setOf("alolan", "shiny"), known, "Alolan", "alola", setOf("alolan")))
        assertFalse(PokeInfoDataProvider.matchesSpawnForm(null, setOf("alolan"), known, "Normal", "normal", emptySet()))
    }
}
