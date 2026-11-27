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

    @Test
    fun `mutual follow detection works correctly`() {
        val viewerUuid = UUID.randomUUID()
        val targetUuid = UUID.randomUUID()

        // 1. Neither follows
        val targetProfile = SocialProfile(
            uuid = targetUuid,
            name = "Red",
            followers = mutableSetOf(),
            following = mutableSetOf()
        )
        val view1 = SocialProfileView.of(targetProfile, viewerUuid, 0, true)
        assertFalse(view1.isFollowedByMe)
        assertFalse(view1.isFollowingMe)
        assertFalse(view1.isMutualFollow)

        // 2. Only viewer follows target
        targetProfile.followers.add(viewerUuid)
        val view2 = SocialProfileView.of(targetProfile, viewerUuid, 0, true)
        assertTrue(view2.isFollowedByMe)
        assertFalse(view2.isFollowingMe)
        assertFalse(view2.isMutualFollow)

        // 3. Both follow each other (mutual)
        targetProfile.following.add(viewerUuid)
        val view3 = SocialProfileView.of(targetProfile, viewerUuid, 0, true)
        assertTrue(view3.isFollowedByMe)
        assertTrue(view3.isFollowingMe)
        assertTrue(view3.isMutualFollow)
    }

    @Test
    fun `extended profile fields serialize to and from NBT`() {
        val uuid = UUID.randomUUID()
        val mon = PokemonAttachment(
            species = "cobblemon:garchomp",
            aspects = setOf("shiny"),
            level = 78,
            nickname = "Landshark",
            ivs = listOf(31, 31, 31, 12, 31, 31),
            evs = listOf(4, 252, 0, 0, 0, 252),
            gender = "FEMALE",
            ability = "cobblemon.ability.roughskin",
            nature = "cobblemon.nature.jolly",
            types = listOf("dragon", "ground")
        )
        val profile = SocialProfile(
            uuid = uuid,
            name = "Cynthia",
            bio = "Sinnoh Champion",
            status = "Looking for Battle",
            bannerType = "dragon",
            pinnedPostId = 42L,
            teamShowcase = mutableListOf(mon),
            showTeam = false,
            acceptInteractions = false,
            dmMutualOnly = true,
            battleWins = 15,
            battleLosses = 2,
            tradesCompleted = 7,
            shiniesCaught = 4,
            legendariesCaught = 3
        )

        val restored = SocialProfile.fromNbt(profile.toNbt())
        assertNotNull(restored)
        assertEquals("Looking for Battle", restored?.status)
        assertEquals("dragon", restored?.bannerType)
        assertEquals(42L, restored?.pinnedPostId)
        assertEquals(1, restored?.teamShowcase?.size)
        assertEquals("cobblemon:garchomp", restored?.teamShowcase?.first()?.species)
        assertFalse(restored!!.showTeam)
        assertFalse(restored.acceptInteractions)
        assertTrue(restored.dmMutualOnly)
        assertEquals(15, restored.battleWins)
        assertEquals(2, restored.battleLosses)
        assertEquals(7, restored.tradesCompleted)
        assertEquals(4, restored.shiniesCaught)
        assertEquals(3, restored.legendariesCaught)
    }

    @Test
    fun `team privacy hides showcase from other viewers but keeps visible to owner`() {
        val ownerUuid = UUID.randomUUID()
        val otherUuid = UUID.randomUUID()
        val profile = SocialProfile(
            uuid = ownerUuid,
            name = "Red",
            teamShowcase = mutableListOf(PokemonAttachment("cobblemon:pikachu", emptySet(), 88, null)),
            showTeam = false
        )

        val otherView = SocialProfileView.of(profile, otherUuid, 0, true)
        assertTrue(otherView.teamShowcase.isEmpty())
        assertFalse(otherView.showTeam)

        val ownerView = SocialProfileView.of(profile, ownerUuid, 0, true)
        assertEquals(1, ownerView.teamShowcase.size)
    }

    @Test
    fun `request limiter regulates pin post and profile list actions`() {
        val player = UUID.randomUUID()
        SocialRequestLimiter.clear(player)

        assertTrue(SocialRequestLimiter.allow(player, SocialRequestLimiter.Action.PIN_POST, 1_000L))
        assertFalse(SocialRequestLimiter.allow(player, SocialRequestLimiter.Action.PIN_POST, 1_200L))
        assertTrue(SocialRequestLimiter.allow(player, SocialRequestLimiter.Action.PIN_POST, 1_600L))

        assertTrue(SocialRequestLimiter.allow(player, SocialRequestLimiter.Action.REQUEST_PROFILE_LIST, 1_600L))
        assertFalse(SocialRequestLimiter.allow(player, SocialRequestLimiter.Action.REQUEST_PROFILE_LIST, 1_700L))
        assertTrue(SocialRequestLimiter.allow(player, SocialRequestLimiter.Action.REQUEST_PROFILE_LIST, 1_900L))
    }

    @Test
    fun `banner type sanitization rejects unknown or forged values`() {
        assertEquals("dragon", SocialProfile.sanitizeBannerType("  DRAGON "))
        assertEquals("fire", SocialProfile.sanitizeBannerType("fire"))
        assertEquals("", SocialProfile.sanitizeBannerType("../../inject"))
        assertEquals("", SocialProfile.sanitizeBannerType("not_a_type"))
        assertEquals("", SocialProfile.sanitizeBannerType(""))
    }

    @Test
    fun `findProfile is read-only and does not create ghost profiles`() {
        val data = SocialData()
        val unknown = UUID.randomUUID()

        assertEquals(null, data.findProfile(unknown))
        assertEquals(0, data.allProfiles().size)

        data.updateProfileSettings(
            uuid = unknown,
            name = "Brock",
            bio = "Pewter Gym",
            status = "Open to Trades",
            bannerType = "ROCK",
            showTeam = true,
            acceptInteractions = true,
            dmMutualOnly = false
        )
        val saved = data.findProfile(unknown)
        assertNotNull(saved)
        assertEquals("rock", saved?.bannerType)
        assertEquals(1, data.allProfiles().size)
    }

    @Test
    fun `profile packets round-trip cleanly across network buffer`() {
        val buf = net.minecraft.network.RegistryFriendlyByteBuf(
            io.netty.buffer.Unpooled.buffer(),
            net.minecraft.core.RegistryAccess.EMPTY
        )
        val update = com.nbp.cobblemon_smartphone.network.packet.UpdateProfilePacket(
            bio = "Hello world",
            favoritePartySlot = 2,
            status = "Shiny Hunting",
            bannerType = "water",
            syncTeamAction = 1,
            showTeam = false,
            acceptInteractions = true,
            dmMutualOnly = true
        )
        update.encode(buf)
        val decodedUpdate = com.nbp.cobblemon_smartphone.network.packet.UpdateProfilePacket.decode(buf)
        assertEquals("Hello world", decodedUpdate.bio)
        assertEquals(2, decodedUpdate.favoritePartySlot)
        assertEquals("Shiny Hunting", decodedUpdate.status)
        assertEquals("water", decodedUpdate.bannerType)
        assertEquals(1, decodedUpdate.syncTeamAction)
        assertFalse(decodedUpdate.showTeam)
        assertTrue(decodedUpdate.acceptInteractions)
        assertTrue(decodedUpdate.dmMutualOnly)

        val listBuf = net.minecraft.network.RegistryFriendlyByteBuf(
            io.netty.buffer.Unpooled.buffer(),
            net.minecraft.core.RegistryAccess.EMPTY
        )
        val target = UUID.randomUUID()
        val trainer = SocialTrainerSummary(
            uuid = UUID.randomUUID(),
            name = "Misty",
            status = "Looking for Battle",
            bannerType = "water",
            isOnline = true,
            isFollowedByMe = true,
            isFollowingMe = true,
            skinUrl = null
        )
        val listPkt = com.nbp.cobblemon_smartphone.network.packet.ProfileListResponsePacket(
            targetUuid = target,
            listType = com.nbp.cobblemon_smartphone.network.packet.RequestProfileListPacket.ListType.FOLLOWERS,
            trainers = listOf(trainer)
        )
        listPkt.encode(listBuf)
        val decodedList = com.nbp.cobblemon_smartphone.network.packet.ProfileListResponsePacket.decode(listBuf)
        assertEquals(target, decodedList.targetUuid)
        assertEquals(1, decodedList.trainers.size)
        assertEquals("Misty", decodedList.trainers.first().name)
        assertTrue(decodedList.trainers.first().isMutualFollow)
    }
}
