package com.nbp.cobblemon_smartphone.network.packet

import com.nbp.cobblemon_smartphone.social.SocialCommentView
import com.nbp.cobblemon_smartphone.util.smartphoneResource
import net.minecraft.network.RegistryFriendlyByteBuf

class AddCommentPacket(
    val postId: Long,
    val text: String
) : CobblemonSmartphoneNetworkPacket<AddCommentPacket> {
    companion object {
        val ID = smartphoneResource("add_comment")
        fun decode(buffer: RegistryFriendlyByteBuf) = AddCommentPacket(
            buffer.readVarLong(),
            buffer.readUtf(1024)
        )
    }

    override val id = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeVarLong(postId)
        buffer.writeUtf(text, 1024)
    }
}

class DeleteCommentPacket(
    val postId: Long,
    val commentId: Long
) : CobblemonSmartphoneNetworkPacket<DeleteCommentPacket> {
    companion object {
        val ID = smartphoneResource("delete_comment")
        fun decode(buffer: RegistryFriendlyByteBuf) = DeleteCommentPacket(
            buffer.readVarLong(),
            buffer.readVarLong()
        )
    }

    override val id = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeVarLong(postId)
        buffer.writeVarLong(commentId)
    }
}

class RequestCommentsPacket(
    val postId: Long
) : CobblemonSmartphoneNetworkPacket<RequestCommentsPacket> {
    companion object {
        val ID = smartphoneResource("request_comments")
        fun decode(buffer: RegistryFriendlyByteBuf) = RequestCommentsPacket(buffer.readVarLong())
    }

    override val id = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeVarLong(postId)
    }
}

class CommentsListPacket(
    val postId: Long,
    val comments: List<SocialCommentView>
) : CobblemonSmartphoneNetworkPacket<CommentsListPacket> {
    companion object {
        val ID = smartphoneResource("comments_list")
        fun decode(buffer: RegistryFriendlyByteBuf): CommentsListPacket {
            val postId = buffer.readVarLong()
            val count = buffer.readVarInt()
            val comments = (0 until count).map { SocialCommentView.decode(buffer) }
            return CommentsListPacket(postId, comments)
        }
    }

    override val id = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeVarLong(postId)
        buffer.writeVarInt(comments.size)
        comments.forEach { it.encode(buffer) }
    }
}
