package com.nbp.cobblemon_smartphone.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PokeInfoLayoutTest {
    @Test
    fun `page height includes only sections on that page`() {
        assertEquals(10 + 20 + 2 * 4, PokeInfoLayout.totalHeight(listOf(10, 20)))
        assertEquals(30 + 4, PokeInfoLayout.totalHeight(listOf(30)))
    }

    @Test
    fun `disabled sections do not affect height or separators`() {
        val height = PokeInfoLayout.totalHeight(
            formHeight = 10,
            topHeight = 20,
            optionalSections = listOf(true to 30, false to 1_000, true to 40)
        )

        assertEquals(10 + 20 + 30 + 40 + 4 * 4, height)
    }
}
