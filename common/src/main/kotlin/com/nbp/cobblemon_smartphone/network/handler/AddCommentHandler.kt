package com.nbp.cobblemon_smartphone.network.handler

import com.cobblemon.mod.common.api.net.ServerNetworkPacketHandler
import com.nbp.cobblemon_smartphone.CobblemonSmartphone
import com.nbp.cobblemon_smartphone.network.SocialRequestLimiter
import com.nbp.cobblemon_smartphone.network.packet.AddCommentPacket
import com.nbp.cobblemon_smartphone.network.packet.CommentsListPacket
import com.nbp.cobblemon_smartphone.network.packet.FeedPostUpdatePacket
import com.nbp.cobblemon_smartphone.social.SocialCommentView
import com.nbp.cobblemon_smartphone.social.SocialData
import com.nbp.cobblemon_smartphone.social.SocialPostView
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import java.util.UUID

object AddCommentHandler : ServerNetworkPacketHandler<AddCommentPacket> {
    private const val ERROR_COLOR = 0xfd0100
    private val lastComment = mutableMapOf<UUID, Long>()

    override fun handle(packet: AddCommentPacket, server: MinecraftServer, player: ServerPlayer) {
        server.execute {
            if (!CobblemonSmartphone.config.features.enableSocial) {
                player.displayClientMessage(Component.translatable("message.nbp.social.disabled").withColor(ERROR_COLOR), true)
                return@execute
            }

            val data = SocialData.get(server)
            if (data.isPostBanned(player.uuid)) {
                player.displayClientMessage(Component.translatable("message.nbp.social.post_banned").withColor(ERROR_COLOR), true)
                return@execute
            }

            if (!SocialRequestLimiter.allow(player.uuid, SocialRequestLimiter.Action.COMMENT)) return@execute

            val cooldown = CobblemonSmartphone.config.cooldowns.socialComment
            if (cooldown > 0) {
                val now = System.currentTimeMillis() / 1000
                val elapsed = now - (lastComment[player.uuid] ?: 0)
                if (elapsed < cooldown) {
                    player.displayClientMessage(
                        Component.translatable("message.nbp.social.cooldown", (cooldown - elapsed).toInt())
                            .withColor(ERROR_COLOR),
                        true
                    )
                    return@execute
                }
            }

            val maxLen = CobblemonSmartphone.config.social.maxCommentLength
            val text = packet.text.trim().take(maxLen)
            if (text.isEmpty()) {
                player.displayClientMessage(Component.translatable("message.nbp.social.empty_comment").withColor(ERROR_COLOR), true)
                return@execute
            }

            val post = data.postById(packet.postId) ?: return@execute
            data.addComment(post.id, player.uuid, player.gameProfile.name, text) ?: return@execute
            lastComment[player.uuid] = System.currentTimeMillis() / 1000

            // Broadcast updated post to all online players (so feed comment count updates)
            server.playerList.players.forEach { viewer ->
                FeedPostUpdatePacket(0L, SocialPostView.of(post, viewer.uuid, server)).sendToPlayer(viewer)
            }

            // Broadcast updated comments list
            val commentViews = post.comments.map { SocialCommentView.of(it, server) }
            val listPacket = CommentsListPacket(post.id, commentViews)
            server.playerList.players.forEach { viewer ->
                listPacket.sendToPlayer(viewer)
            }

            if (post.authorUuid != player.uuid) {
                val notif = data.addNotification(
                    targetUuid = post.authorUuid,
                    actorUuid = player.uuid,
                    actorName = player.gameProfile.name,
                    type = com.nbp.cobblemon_smartphone.social.SocialNotificationType.COMMENT,
                    referenceId = post.id,
                    message = text.take(50)
                )
                if (notif != null) {
                    val authorPlayer = server.playerList.getPlayer(post.authorUuid)
                    if (authorPlayer != null) {
                        com.nbp.cobblemon_smartphone.network.packet.PushNotificationPacket(
                            com.nbp.cobblemon_smartphone.social.SocialNotificationView.of(notif, server)
                        ).sendToPlayer(authorPlayer)
                    }
                }
            }

            val mentions = com.nbp.cobblemon_smartphone.social.SocialMentionHelper.extractMentions(text)
            for (mentioned in mentions) {
                val targetPlayer = server.playerList.getPlayerByName(mentioned)
                val targetUuid = targetPlayer?.uuid ?: data.profileByName(mentioned)?.uuid
                if (targetUuid != null && targetUuid != player.uuid && targetUuid != post.authorUuid) {
                    val notif = data.addNotification(
                        targetUuid = targetUuid,
                        actorUuid = player.uuid,
                        actorName = player.gameProfile.name,
                        type = com.nbp.cobblemon_smartphone.social.SocialNotificationType.MENTION,
                        referenceId = post.id,
                        message = text.take(50)
                    )
                    if (notif != null && targetPlayer != null) {
                        com.nbp.cobblemon_smartphone.network.packet.PushNotificationPacket(
                            com.nbp.cobblemon_smartphone.social.SocialNotificationView.of(notif, server)
                        ).sendToPlayer(targetPlayer)
                    }
                }
            }
        }
    }
}
