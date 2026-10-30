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
    val photo: SocialPhotoRef? = null,
    val likes: MutableSet<UUID> = mutableSetOf(),
    val comments: MutableList<SocialComment> = mutableListOf(),
    val poll: SocialPoll? = null
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
        photo?.let { tag.put(PHOTO_KEY, it.toNbt()) }
        poll?.let { tag.put(POLL_KEY, it.toNbt()) }

        val likeList = ListTag()
        likes.forEach { likeList.add(net.minecraft.nbt.NbtUtils.createUUID(it)) }
        tag.put(LIKES_KEY, likeList)

        val commentList = ListTag()
        comments.forEach { commentList.add(it.toNbt()) }
        tag.put(COMMENTS_KEY, commentList)
        return tag
    }

    companion object {
        private const val ID_KEY = "id"
        private const val AUTHOR_UUID_KEY = "author"
        private const val AUTHOR_NAME_KEY = "author_name"
        private const val TEXT_KEY = "text"
        private const val TIMESTAMP_KEY = "timestamp"
        private const val ATTACHMENT_KEY = "attachment"
        private const val PHOTO_KEY = "photo"
        private const val LIKES_KEY = "likes"
        private const val COMMENTS_KEY = "comments"
        private const val POLL_KEY = "poll"

        fun fromNbt(tag: CompoundTag): SocialPost? {
            if (!tag.hasUUID(AUTHOR_UUID_KEY)) return null
            val likes = tag.getList(LIKES_KEY, Tag.TAG_INT_ARRAY.toInt())
                .mapNotNull { runCatching { net.minecraft.nbt.NbtUtils.loadUUID(it) }.getOrNull() }
                .toMutableSet()

            val comments = if (tag.contains(COMMENTS_KEY)) {
                tag.getList(COMMENTS_KEY, Tag.TAG_COMPOUND.toInt())
                    .mapNotNull { SocialComment.fromNbt(it as CompoundTag) }
                    .toMutableList()
            } else {
                mutableListOf()
            }

            val poll = if (tag.contains(POLL_KEY)) {
                SocialPoll.fromNbt(tag.getCompound(POLL_KEY))
            } else null

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
                photo = if (tag.contains(PHOTO_KEY)) SocialPhotoRef.fromNbt(tag.getCompound(PHOTO_KEY)) else null,
                likes = likes,
                comments = comments,
                poll = poll
            )
        }
    }
}
