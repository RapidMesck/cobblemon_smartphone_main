package com.nbp.cobblemon_smartphone.social

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.pokedex.PokedexEntryProgress
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.server.MinecraftServer
import java.util.UUID

/**
 * Lightweight trainer summary used in follower/following lists and the trainer directory.
 */
data class SocialTrainerSummary(
    val uuid: UUID,
    val name: String,
    val status: String = "",
    val bannerType: String = "",
    val isOnline: Boolean = false,
    val isFollowedByMe: Boolean = false,
    val isFollowingMe: Boolean = false,
    val skinUrl: String? = null
) {
    val isMutualFollow: Boolean
        get() = isFollowedByMe && isFollowingMe

    fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeUUID(uuid)
        buffer.writeUtf(name)
        buffer.writeUtf(status)
        buffer.writeUtf(bannerType)
        buffer.writeBoolean(isOnline)
        buffer.writeBoolean(isFollowedByMe)
        buffer.writeBoolean(isFollowingMe)
        buffer.writeBoolean(skinUrl != null)
        skinUrl?.let { buffer.writeUtf(it) }
    }

    companion object {
        fun of(
            profile: SocialProfile,
            viewerUuid: UUID,
            isOnline: Boolean,
            server: MinecraftServer? = null
        ): SocialTrainerSummary = SocialTrainerSummary(
            uuid = profile.uuid,
            name = profile.name,
            status = profile.status,
            bannerType = profile.bannerType,
            isOnline = isOnline,
            isFollowedByMe = profile.followers.contains(viewerUuid),
            isFollowingMe = profile.following.contains(viewerUuid),
            skinUrl = server?.let { SkinUrlResolver.resolve(it, profile.uuid) }
        )

        fun decode(buffer: RegistryFriendlyByteBuf): SocialTrainerSummary {
            val uuid = buffer.readUUID()
            val name = buffer.readUtf()
            val status = buffer.readUtf()
            val bannerType = buffer.readUtf()
            val isOnline = buffer.readBoolean()
            val isFollowedByMe = buffer.readBoolean()
            val isFollowingMe = buffer.readBoolean()
            val skinUrl = if (buffer.readBoolean()) buffer.readUtf() else null
            return SocialTrainerSummary(
                uuid = uuid,
                name = name,
                status = status,
                bannerType = bannerType,
                isOnline = isOnline,
                isFollowedByMe = isFollowedByMe,
                isFollowingMe = isFollowingMe,
                skinUrl = skinUrl
            )
        }
    }
}

/**
 * Client projection of a [SocialProfile].
 */
data class SocialProfileView(
    val uuid: UUID,
    val name: String,
    val bio: String,
    val favoriteSpecies: String?,
    val favoriteAspects: Set<String>,
    val postCount: Int,
    val followerCount: Int,
    val followingCount: Int,
    val isOnline: Boolean,
    val isFollowedByMe: Boolean,
    val isFollowingMe: Boolean = false,
    val skinUrl: String? = null,
    val status: String = "",
    val bannerType: String = "",
    val pinnedPostId: Long = 0L,
    val teamShowcase: List<PokemonAttachment> = emptyList(),
    val showTeam: Boolean = true,
    val acceptInteractions: Boolean = true,
    val dmMutualOnly: Boolean = false,
    val pokedexCaught: Int = 0,
    val pokedexSeen: Int = 0,
    val battleWins: Int = 0,
    val battleLosses: Int = 0,
    val tradesCompleted: Int = 0,
    val shiniesCaught: Int = 0,
    val legendariesCaught: Int = 0
) {
    val isMutualFollow: Boolean
        get() = isFollowedByMe && isFollowingMe

    fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeUUID(uuid)
        buffer.writeUtf(name)
        buffer.writeUtf(bio)
        buffer.writeBoolean(favoriteSpecies != null)
        favoriteSpecies?.let { buffer.writeUtf(it) }
        buffer.writeVarInt(favoriteAspects.size)
        favoriteAspects.forEach { buffer.writeUtf(it) }
        buffer.writeVarInt(postCount)
        buffer.writeVarInt(followerCount)
        buffer.writeVarInt(followingCount)
        buffer.writeBoolean(isOnline)
        buffer.writeBoolean(isFollowedByMe)
        buffer.writeBoolean(isFollowingMe)
        buffer.writeBoolean(skinUrl != null)
        skinUrl?.let { buffer.writeUtf(it) }
        buffer.writeUtf(status)
        buffer.writeUtf(bannerType)
        buffer.writeVarLong(pinnedPostId)
        buffer.writeVarInt(teamShowcase.size)
        teamShowcase.forEach { it.encode(buffer) }
        buffer.writeBoolean(showTeam)
        buffer.writeBoolean(acceptInteractions)
        buffer.writeBoolean(dmMutualOnly)
        buffer.writeVarInt(pokedexCaught)
        buffer.writeVarInt(pokedexSeen)
        buffer.writeVarInt(battleWins)
        buffer.writeVarInt(battleLosses)
        buffer.writeVarInt(tradesCompleted)
        buffer.writeVarInt(shiniesCaught)
        buffer.writeVarInt(legendariesCaught)
    }

    companion object {
        fun of(
            profile: SocialProfile,
            viewerUuid: UUID,
            postCount: Int,
            isOnline: Boolean,
            server: MinecraftServer? = null
        ): SocialProfileView {
            val isSelf = viewerUuid == profile.uuid
            val visibleTeam = if (isSelf || profile.showTeam) profile.teamShowcase.toList() else emptyList()
            val (caught, seen) = resolvePokedexStats(server, profile.uuid)
            return SocialProfileView(
                uuid = profile.uuid,
                name = profile.name,
                bio = profile.bio,
                favoriteSpecies = profile.favoriteSpecies,
                favoriteAspects = profile.favoriteAspects,
                postCount = postCount,
                followerCount = profile.followers.size,
                followingCount = profile.following.size,
                isOnline = isOnline,
                isFollowedByMe = profile.followers.contains(viewerUuid),
                isFollowingMe = profile.following.contains(viewerUuid),
                skinUrl = server?.let { SkinUrlResolver.resolve(it, profile.uuid) },
                status = profile.status,
                bannerType = profile.bannerType,
                pinnedPostId = profile.pinnedPostId,
                teamShowcase = visibleTeam,
                showTeam = profile.showTeam,
                acceptInteractions = profile.acceptInteractions,
                dmMutualOnly = profile.dmMutualOnly,
                pokedexCaught = caught,
                pokedexSeen = seen,
                battleWins = profile.battleWins,
                battleLosses = profile.battleLosses,
                tradesCompleted = profile.tradesCompleted,
                shiniesCaught = profile.shiniesCaught,
                legendariesCaught = profile.legendariesCaught
            )
        }

        private fun resolvePokedexStats(server: MinecraftServer?, uuid: UUID): Pair<Int, Int> {
            if (server == null) return 0 to 0
            return runCatching {
                val dex = Cobblemon.playerDataManager.getPokedexData(uuid)
                var caught = 0
                var seen = 0
                dex.speciesRecords.values.forEach { record ->
                    when (record.getKnowledge()) {
                        PokedexEntryProgress.OWNED -> {
                            caught++
                            seen++
                        }
                        PokedexEntryProgress.SEEN -> seen++
                        else -> Unit
                    }
                }
                caught to seen
            }.getOrDefault(0 to 0)
        }

        fun decode(buffer: RegistryFriendlyByteBuf): SocialProfileView {
            val uuid = buffer.readUUID()
            val name = buffer.readUtf(64)
            val bio = buffer.readUtf(512)
            val favoriteSpecies = if (buffer.readBoolean()) buffer.readUtf(128) else null
            val aspectCount = buffer.readVarInt().coerceIn(0, 64)
            val favoriteAspects = (0 until aspectCount).map { buffer.readUtf(64) }.toSet()
            val postCount = buffer.readVarInt()
            val followerCount = buffer.readVarInt()
            val followingCount = buffer.readVarInt()
            val isOnline = buffer.readBoolean()
            val isFollowedByMe = buffer.readBoolean()
            val isFollowingMe = buffer.readBoolean()
            val skinUrl = if (buffer.readBoolean()) buffer.readUtf(512) else null
            val status = buffer.readUtf(128)
            val bannerType = buffer.readUtf(32)
            val pinnedPostId = buffer.readVarLong()
            val teamCount = buffer.readVarInt().coerceIn(0, SocialProfile.MAX_TEAM_SIZE)
            val teamShowcase = (0 until teamCount).map { PokemonAttachment.decode(buffer) }
            val showTeam = buffer.readBoolean()
            val acceptInteractions = buffer.readBoolean()
            val dmMutualOnly = buffer.readBoolean()
            val pokedexCaught = buffer.readVarInt()
            val pokedexSeen = buffer.readVarInt()
            val battleWins = buffer.readVarInt()
            val battleLosses = buffer.readVarInt()
            val tradesCompleted = buffer.readVarInt()
            val shiniesCaught = buffer.readVarInt()
            val legendariesCaught = buffer.readVarInt()
            return SocialProfileView(
                uuid = uuid,
                name = name,
                bio = bio,
                favoriteSpecies = favoriteSpecies,
                favoriteAspects = favoriteAspects,
                postCount = postCount,
                followerCount = followerCount,
                followingCount = followingCount,
                isOnline = isOnline,
                isFollowedByMe = isFollowedByMe,
                isFollowingMe = isFollowingMe,
                skinUrl = skinUrl,
                status = status,
                bannerType = bannerType,
                pinnedPostId = pinnedPostId,
                teamShowcase = teamShowcase,
                showTeam = showTeam,
                acceptInteractions = acceptInteractions,
                dmMutualOnly = dmMutualOnly,
                pokedexCaught = pokedexCaught,
                pokedexSeen = pokedexSeen,
                battleWins = battleWins,
                battleLosses = battleLosses,
                tradesCompleted = tradesCompleted,
                shiniesCaught = shiniesCaught,
                legendariesCaught = legendariesCaught
            )
        }
    }
}
