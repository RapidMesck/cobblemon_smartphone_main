package com.nbp.cobblemon_smartphone.social

import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.Tag
import java.util.UUID

/**
 * A single post in the global feed.
 *
 * [authorName] is a cached copy of the author's username so offline (or renamed) authors still
 * render; [authorUuid] stays the canonical identity.
 */
data class SocialPost(
    val id: Long,
    val authorUuid: UUID,
    val authorName: String,
    val text: String,
    val timestamp: Long,
    val attachment: PokemonAttachment?,
    val likes: MutableSet<UUID> = mutableSetOf()
) {
    fun isLikedBy(uuid: UUID): Boolean = likes.contains(uuid)

    fun toNbt(): CompoundTag {
        val tag = CompoundTag()
        tag.putLong(ID_KEY, id)
        tag.putUUID(AUTHOR_UUID_KEY, authorUuid)
        tag.putString(AUTHOR_NAME_KEY, authorName)
        tag.putString(TEXT_KEY, text)
        tag.putLong(TIMESTAMP_KEY, timestamp)
        attachment?.let { tag.put(ATTACHMENT_KEY, it.toNbt()) }

        val likeList = ListTag()
        likes.forEach { likeList.add(net.minecraft.nbt.NbtUtils.createUUID(it)) }
        tag.put(LIKES_KEY, likeList)
        return tag
    }

    companion object {
        private const val ID_KEY = "id"
        private const val AUTHOR_UUID_KEY = "author"
        private const val AUTHOR_NAME_KEY = "author_name"
        private const val TEXT_KEY = "text"
        private const val TIMESTAMP_KEY = "timestamp"
        private const val ATTACHMENT_KEY = "attachment"
        private const val LIKES_KEY = "likes"

        fun fromNbt(tag: CompoundTag): SocialPost? {
            if (!tag.hasUUID(AUTHOR_UUID_KEY)) return null
            val likes = tag.getList(LIKES_KEY, Tag.TAG_INT_ARRAY.toInt())
                .mapNotNull { runCatching { net.minecraft.nbt.NbtUtils.loadUUID(it) }.getOrNull() }
                .toMutableSet()

            return SocialPost(
                id = tag.getLong(ID_KEY),
                authorUuid = tag.getUUID(AUTHOR_UUID_KEY),
                authorName = tag.getString(AUTHOR_NAME_KEY),
                text = tag.getString(TEXT_KEY),
                timestamp = tag.getLong(TIMESTAMP_KEY),
                attachment = if (tag.contains(ATTACHMENT_KEY)) {
                    PokemonAttachment.fromNbt(tag.getCompound(ATTACHMENT_KEY))
                } else {
                    null
                },
                likes = likes
            )
        }
    }
}
