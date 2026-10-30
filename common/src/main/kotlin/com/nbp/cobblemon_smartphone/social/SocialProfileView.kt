package com.nbp.cobblemon_smartphone.social

import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.server.MinecraftServer
import java.util.UUID

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
    val skinUrl: String? = null
) {
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
        buffer.writeBoolean(skinUrl != null)
        skinUrl?.let { buffer.writeUtf(it) }
    }

    companion object {
        fun of(
            profile: SocialProfile,
            viewerUuid: UUID,
            postCount: Int,
            isOnline: Boolean,
            server: MinecraftServer? = null
        ): SocialProfileView = SocialProfileView(
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
            skinUrl = server?.let { SkinUrlResolver.resolve(it, profile.uuid) }
        )

        fun decode(buffer: RegistryFriendlyByteBuf): SocialProfileView {
            val uuid = buffer.readUUID()
            val name = buffer.readUtf()
            val bio = buffer.readUtf()
            val favoriteSpecies = if (buffer.readBoolean()) buffer.readUtf() else null
            val aspectCount = buffer.readVarInt()
            val favoriteAspects = (0 until aspectCount).map { buffer.readUtf() }.toSet()
            val postCount = buffer.readVarInt()
            val followerCount = buffer.readVarInt()
            val followingCount = buffer.readVarInt()
            val isOnline = buffer.readBoolean()
            val isFollowedByMe = buffer.readBoolean()
            val skinUrl = if (buffer.readBoolean()) buffer.readUtf() else null
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
                skinUrl = skinUrl
            )
        }
    }
}
