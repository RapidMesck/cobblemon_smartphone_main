package com.nbp.cobblemon_smartphone.social

import net.minecraft.network.RegistryFriendlyByteBuf
import java.util.UUID

/**
 * Wire/client projection of a [SocialPost].
 *
 * Intentionally not the storage model: the server keeps the full `likes` UUID set, but the client
 * only ever needs the count plus whether *it* liked the post. Sending the whole set would waste
 * bandwidth and expose who liked what.
 */
data class SocialPostView(
    val id: Long,
    val authorUuid: UUID,
    val authorName: String,
    val text: String,
    val timestamp: Long,
    val attachment: PokemonAttachment?,
    val likeCount: Int,
    val likedByMe: Boolean
) {
    fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeVarLong(id)
        buffer.writeUUID(authorUuid)
        buffer.writeUtf(authorName)
        buffer.writeUtf(text)
        buffer.writeLong(timestamp)
        buffer.writeBoolean(attachment != null)
        attachment?.let {
            buffer.writeUtf(it.species)
            buffer.writeVarInt(it.aspects.size)
            it.aspects.forEach { aspect -> buffer.writeUtf(aspect) }
            buffer.writeVarInt(it.level)
            buffer.writeBoolean(it.nickname != null)
            it.nickname?.let { nick -> buffer.writeUtf(nick) }
        }
        buffer.writeVarInt(likeCount)
        buffer.writeBoolean(likedByMe)
    }

    companion object {
        fun of(post: SocialPost, viewer: UUID): SocialPostView = SocialPostView(
            id = post.id,
            authorUuid = post.authorUuid,
            authorName = post.authorName,
            text = post.text,
            timestamp = post.timestamp,
            attachment = post.attachment,
            likeCount = post.likes.size,
            likedByMe = post.isLikedBy(viewer)
        )

        fun decode(buffer: RegistryFriendlyByteBuf): SocialPostView {
            val id = buffer.readVarLong()
            val authorUuid = buffer.readUUID()
            val authorName = buffer.readUtf()
            val text = buffer.readUtf()
            val timestamp = buffer.readLong()
            val attachment = if (buffer.readBoolean()) {
                val species = buffer.readUtf()
                val aspectCount = buffer.readVarInt()
                val aspects = (0 until aspectCount).map { buffer.readUtf() }.toSet()
                val level = buffer.readVarInt()
                val nickname = if (buffer.readBoolean()) buffer.readUtf() else null
                PokemonAttachment(species, aspects, level, nickname)
            } else {
                null
            }
            return SocialPostView(
                id = id,
                authorUuid = authorUuid,
                authorName = authorName,
                text = text,
                timestamp = timestamp,
                attachment = attachment,
                likeCount = buffer.readVarInt(),
                likedByMe = buffer.readBoolean()
            )
        }
    }
}
