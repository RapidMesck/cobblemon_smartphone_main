package com.nbp.cobblemon_smartphone.social

import com.nbp.cobblemon_smartphone.network.SocialRequestLimiter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class SocialProfileTest {

    @Test
    fun `profile serializes to and from NBT`() {
        val uuid = UUID.randomUUID()
        val follower = UUID.randomUUID()
        val profile = SocialProfile(
            uuid = uuid,
            name = "Cynthia",
            bio = "Campeã de Sinnoh | Mestra de Garchomp",
            favoriteSpecies = "cobblemon:garchomp",
            favoriteAspects = setOf("gender-female"),
            followers = mutableSetOf(follower),
            following = mutableSetOf()
        )

        val nbt = profile.toNbt()
        val restored = SocialProfile.fromNbt(nbt)

        assertNotNull(restored)
        assertEquals(uuid, restored?.uuid)
        assertEquals("Cynthia", restored?.name)
        assertEquals("Campeã de Sinnoh | Mestra de Garchomp", restored?.bio)
        assertEquals("cobblemon:garchomp", restored?.favoriteSpecies)
        assertTrue(restored?.favoriteAspects?.contains("gender-female") == true)
        assertEquals(1, restored?.followers?.size)
        assertTrue(restored?.followers?.contains(follower) == true)
    }

    @Test
    fun `request limiter regulates profile actions`() {
        val player = UUID.randomUUID()
        SocialRequestLimiter.clear(player)

        assertTrue(SocialRequestLimiter.allow(player, SocialRequestLimiter.Action.REQUEST_PROFILE, 1_000L))
        assertFalse(SocialRequestLimiter.allow(player, SocialRequestLimiter.Action.REQUEST_PROFILE, 1_100L))
        assertTrue(SocialRequestLimiter.allow(player, SocialRequestLimiter.Action.REQUEST_PROFILE, 1_300L))

        assertTrue(SocialRequestLimiter.allow(player, SocialRequestLimiter.Action.UPDATE_PROFILE, 1_300L))
        assertFalse(SocialRequestLimiter.allow(player, SocialRequestLimiter.Action.UPDATE_PROFILE, 1_800L))
        assertTrue(SocialRequestLimiter.allow(player, SocialRequestLimiter.Action.UPDATE_PROFILE, 2_400L))

        assertTrue(SocialRequestLimiter.allow(player, SocialRequestLimiter.Action.TOGGLE_FOLLOW, 2_400L))
        assertFalse(SocialRequestLimiter.allow(player, SocialRequestLimiter.Action.TOGGLE_FOLLOW, 2_600L))
        assertTrue(SocialRequestLimiter.allow(player, SocialRequestLimiter.Action.TOGGLE_FOLLOW, 3_000L))
    }
}
