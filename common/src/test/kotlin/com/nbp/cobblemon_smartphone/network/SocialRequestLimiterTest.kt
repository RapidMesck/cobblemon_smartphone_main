package com.nbp.cobblemon_smartphone.network

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class SocialRequestLimiterTest {
    @Test
    fun `limits each player and operation independently`() {
        val first = UUID.randomUUID()
        val second = UUID.randomUUID()
        SocialRequestLimiter.clearAll()

        assertTrue(SocialRequestLimiter.allow(first, SocialRequestLimiter.Action.FEED_PAGE, 1_000L))
        assertFalse(SocialRequestLimiter.allow(first, SocialRequestLimiter.Action.FEED_PAGE, 1_100L))
        assertTrue(SocialRequestLimiter.allow(first, SocialRequestLimiter.Action.THREAD_PAGE, 1_100L))
        assertTrue(SocialRequestLimiter.allow(second, SocialRequestLimiter.Action.FEED_PAGE, 1_100L))
        assertTrue(SocialRequestLimiter.allow(first, SocialRequestLimiter.Action.FEED_PAGE, 1_250L))
    }

    @Test
    fun `clearing a player removes their limits`() {
        val player = UUID.randomUUID()
        SocialRequestLimiter.clearAll()
        assertTrue(SocialRequestLimiter.allow(player, SocialRequestLimiter.Action.CALL_START, 2_000L))
        SocialRequestLimiter.clear(player)
        assertTrue(SocialRequestLimiter.allow(player, SocialRequestLimiter.Action.CALL_START, 2_001L))
    }
}
