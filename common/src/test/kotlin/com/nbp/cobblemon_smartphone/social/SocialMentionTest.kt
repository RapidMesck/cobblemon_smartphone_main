package com.nbp.cobblemon_smartphone.social

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SocialMentionTest {

    @Test
    fun `extractMentions finds valid usernames`() {
        val text = "Hello @Ash, did you see @Gary_Oak and @Misty99 battle?"
        val mentions = SocialMentionHelper.extractMentions(text)

        assertEquals(3, mentions.size)
        assertTrue(mentions.contains("Ash"))
        assertTrue(mentions.contains("Gary_Oak"))
        assertTrue(mentions.contains("Misty99"))
    }

    @Test
    fun `extractMentions ignores emails or invalid handles`() {
        val text = "Contact test@example.com for info or @!"
        val mentions = SocialMentionHelper.extractMentions(text)

        assertEquals(0, mentions.size)
    }

    @Test
    fun `hasMention checks presence case insensitively`() {
        val text = "Hey @Brock check out this Onix!"

        assertTrue(SocialMentionHelper.hasMention(text, "Brock"))
        assertTrue(SocialMentionHelper.hasMention(text, "brock"))
        assertTrue(SocialMentionHelper.hasMention(text, "BROCK"))
        assertFalse(SocialMentionHelper.hasMention(text, "Pikachu"))
    }
}
