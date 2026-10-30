package com.nbp.cobblemon_smartphone.social

import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.server.MinecraftServer
import java.util.UUID

/**
 * Client projection of a [SocialComment].
 */
data class SocialCommentView(
    val id: Long,
    val postId: Long,
    val authorUuid: UUID,
    val authorName: String,
    val text: String,
    val timestamp: Long,
    val skinUrl: String? = null
) {
    fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeVarLong(id)
        buffer.writeVarLong(postId)
        buffer.writeUUID(authorUuid)
        buffer.writeUtf(authorName)
        buffer.writeUtf(text)
        buffer.writeLong(timestamp)
        buffer.writeBoolean(skinUrl != null)
        skinUrl?.let { buffer.writeUtf(it) }
    }

    companion object {
        fun of(comment: SocialComment, server: MinecraftServer? = null): SocialCommentView = SocialCommentView(
            id = comment.id,
            postId = comment.postId,
            authorUuid = comment.authorUuid,
            authorName = comment.authorName,
            text = comment.text,
            timestamp = comment.timestamp,
            skinUrl = server?.let { SkinUrlResolver.resolve(it, comment.authorUuid) }
        )

        fun decode(buffer: RegistryFriendlyByteBuf): SocialCommentView {
            val id = buffer.readVarLong()
            val postId = buffer.readVarLong()
            val authorUuid = buffer.readUUID()
            val authorName = buffer.readUtf()
            val text = buffer.readUtf()
            val timestamp = buffer.readLong()
            val skinUrl = if (buffer.readBoolean()) buffer.readUtf() else null
            return SocialCommentView(
                id = id,
                postId = postId,
                authorUuid = authorUuid,
                authorName = authorName,
                text = text,
                timestamp = timestamp,
                skinUrl = skinUrl
            )
        }
    }
}
