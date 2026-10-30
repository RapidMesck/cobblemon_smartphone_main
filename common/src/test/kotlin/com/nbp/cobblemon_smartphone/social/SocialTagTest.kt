package com.nbp.cobblemon_smartphone.social

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SocialTagTest {

    @Test
    fun `extractHashtags finds valid hashtags`() {
        val text = "Just caught a shiny Charizard! #Cobblemon #Shiny_Pokemon #FireType"
        val tags = SocialTagHelper.extractHashtags(text)

        assertEquals(3, tags.size)
        assertTrue(tags.contains("Cobblemon"))
        assertTrue(tags.contains("Shiny_Pokemon"))
        assertTrue(tags.contains("FireType"))
    }

    @Test
    fun `extractHashtags handles no hashtags or empty text`() {
        assertEquals(0, SocialTagHelper.extractHashtags("No tags here").size)
        assertEquals(0, SocialTagHelper.extractHashtags("").size)
        assertEquals(0, SocialTagHelper.extractHashtags("#").size)
    }

    @Test
    fun `hasHashtag matches case insensitively`() {
        val text = "Looking for a trade in #PvP arena!"

        assertTrue(SocialTagHelper.hasHashtag(text, "pvp"))
        assertTrue(SocialTagHelper.hasHashtag(text, "PVP"))
        assertTrue(SocialTagHelper.hasHashtag(text, "#PvP"))
        assertFalse(SocialTagHelper.hasHashtag(text, "Cobblemon"))
    }
}
