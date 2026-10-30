package com.nbp.cobblemon_smartphone.network.handler

import com.cobblemon.mod.common.api.net.ServerNetworkPacketHandler
import com.nbp.cobblemon_smartphone.CobblemonSmartphone
import com.nbp.cobblemon_smartphone.network.SocialRequestLimiter
import com.nbp.cobblemon_smartphone.network.packet.CommentsListPacket
import com.nbp.cobblemon_smartphone.network.packet.DeleteCommentPacket
import com.nbp.cobblemon_smartphone.network.packet.FeedPostUpdatePacket
import com.nbp.cobblemon_smartphone.social.SocialCommentView
import com.nbp.cobblemon_smartphone.social.SocialData
import com.nbp.cobblemon_smartphone.social.SocialPostView
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer

object DeleteCommentHandler : ServerNetworkPacketHandler<DeleteCommentPacket> {
    private const val OP_PERMISSION_LEVEL = 2

    override fun handle(packet: DeleteCommentPacket, server: MinecraftServer, player: ServerPlayer) {
        server.execute {
            if (!CobblemonSmartphone.config.features.enableSocial) return@execute
            if (!SocialRequestLimiter.allow(player.uuid, SocialRequestLimiter.Action.DELETE_COMMENT)) return@execute

            val data = SocialData.get(server)
            val post = data.postById(packet.postId) ?: return@execute
            val comment = post.comments.firstOrNull { it.id == packet.commentId } ?: return@execute

            // Allowed: comment author, post author, or operator
            val allowed = comment.authorUuid == player.uuid || post.authorUuid == player.uuid || player.hasPermissions(OP_PERMISSION_LEVEL)
            if (!allowed) {
                player.displayClientMessage(
                    Component.translatable("message.nbp.social.delete_comment_denied").withColor(0xfd0100),
                    true
                )
                return@execute
            }

            data.removeComment(packet.postId, packet.commentId)

            // Broadcast updated post to update feed comment count
            server.playerList.players.forEach { viewer ->
                FeedPostUpdatePacket(0L, SocialPostView.of(post, viewer.uuid, server)).sendToPlayer(viewer)
            }

            // Broadcast updated comments list
            val commentViews = post.comments.map { SocialCommentView.of(it, server) }
            val listPacket = CommentsListPacket(post.id, commentViews)
            server.playerList.players.forEach { viewer ->
                listPacket.sendToPlayer(viewer)
            }
        }
    }
}
