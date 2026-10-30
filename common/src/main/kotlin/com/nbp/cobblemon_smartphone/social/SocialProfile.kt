package com.nbp.cobblemon_smartphone.social

import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.NbtUtils
import net.minecraft.nbt.StringTag
import net.minecraft.nbt.Tag
import java.util.UUID

/**
 * Persistent social profile for a player.
 */
data class SocialProfile(
    val uuid: UUID,
    var name: String,
    var bio: String = "",
    var favoriteSpecies: String? = null,
    var favoriteAspects: Set<String> = emptySet(),
    val followers: MutableSet<UUID> = mutableSetOf(),
    val following: MutableSet<UUID> = mutableSetOf()
) {
    fun toNbt(): CompoundTag {
        val tag = CompoundTag()
        tag.putUUID(UUID_KEY, uuid)
        tag.putString(NAME_KEY, name)
        tag.putString(BIO_KEY, bio)
        favoriteSpecies?.let { tag.putString(FAVORITE_SPECIES_KEY, it) }

        val aspectList = ListTag()
        favoriteAspects.forEach { aspectList.add(StringTag.valueOf(it)) }
        tag.put(FAVORITE_ASPECTS_KEY, aspectList)

        val followerList = ListTag()
        followers.forEach { followerList.add(NbtUtils.createUUID(it)) }
        tag.put(FOLLOWERS_KEY, followerList)

        val followingList = ListTag()
        following.forEach { followingList.add(NbtUtils.createUUID(it)) }
        tag.put(FOLLOWING_KEY, followingList)

        return tag
    }

    companion object {
        private const val UUID_KEY = "uuid"
        private const val NAME_KEY = "name"
        private const val BIO_KEY = "bio"
        private const val FAVORITE_SPECIES_KEY = "favorite_species"
        private const val FAVORITE_ASPECTS_KEY = "favorite_aspects"
        private const val FOLLOWERS_KEY = "followers"
        private const val FOLLOWING_KEY = "following"

        fun fromNbt(tag: CompoundTag): SocialProfile? {
            if (!tag.hasUUID(UUID_KEY)) return null
            val uuid = tag.getUUID(UUID_KEY)
            val name = tag.getString(NAME_KEY)
            val bio = tag.getString(BIO_KEY)
            val favoriteSpecies = if (tag.contains(FAVORITE_SPECIES_KEY)) tag.getString(FAVORITE_SPECIES_KEY) else null
            val favoriteAspects = if (tag.contains(FAVORITE_ASPECTS_KEY)) {
                tag.getList(FAVORITE_ASPECTS_KEY, Tag.TAG_STRING.toInt()).map { it.asString }.toSet()
            } else {
                emptySet()
            }

            val followers = if (tag.contains(FOLLOWERS_KEY)) {
                tag.getList(FOLLOWERS_KEY, Tag.TAG_INT_ARRAY.toInt())
                    .mapNotNull { runCatching { NbtUtils.loadUUID(it) }.getOrNull() }
                    .toMutableSet()
            } else {
                mutableSetOf()
            }

            val following = if (tag.contains(FOLLOWING_KEY)) {
                tag.getList(FOLLOWING_KEY, Tag.TAG_INT_ARRAY.toInt())
                    .mapNotNull { runCatching { NbtUtils.loadUUID(it) }.getOrNull() }
                    .toMutableSet()
            } else {
                mutableSetOf()
            }

            return SocialProfile(
                uuid = uuid,
                name = name,
                bio = bio,
                favoriteSpecies = favoriteSpecies,
                favoriteAspects = favoriteAspects,
                followers = followers,
                following = following
            )
        }
    }
}
