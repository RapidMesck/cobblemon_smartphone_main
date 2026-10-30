package com.nbp.cobblemon_smartphone.social

import net.minecraft.nbt.CompoundTag
import java.util.UUID

/**
 * A single comment on a [SocialPost].
 */
data class SocialComment(
    val id: Long,
    val postId: Long,
    val authorUuid: UUID,
    val authorName: String,
    val text: String,
    val timestamp: Long
) {
    fun toNbt(): CompoundTag {
        val tag = CompoundTag()
        tag.putLong(ID_KEY, id)
        tag.putLong(POST_ID_KEY, postId)
        tag.putUUID(AUTHOR_UUID_KEY, authorUuid)
        tag.putString(AUTHOR_NAME_KEY, authorName)
        tag.putString(TEXT_KEY, text)
        tag.putLong(TIMESTAMP_KEY, timestamp)
        return tag
    }

    companion object {
        private const val ID_KEY = "id"
        private const val POST_ID_KEY = "post_id"
        private const val AUTHOR_UUID_KEY = "author"
        private const val AUTHOR_NAME_KEY = "author_name"
        private const val TEXT_KEY = "text"
        private const val TIMESTAMP_KEY = "timestamp"

        fun fromNbt(tag: CompoundTag): SocialComment? {
            if (!tag.hasUUID(AUTHOR_UUID_KEY)) return null
            return SocialComment(
                id = tag.getLong(ID_KEY),
                postId = tag.getLong(POST_ID_KEY),
                authorUuid = tag.getUUID(AUTHOR_UUID_KEY),
                authorName = tag.getString(AUTHOR_NAME_KEY),
                text = tag.getString(TEXT_KEY),
                timestamp = tag.getLong(TIMESTAMP_KEY)
            )
        }
    }
}
