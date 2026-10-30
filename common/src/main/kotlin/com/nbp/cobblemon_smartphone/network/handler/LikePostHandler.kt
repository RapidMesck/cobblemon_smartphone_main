package com.nbp.cobblemon_smartphone.network.handler

import com.cobblemon.mod.common.api.net.ServerNetworkPacketHandler
import com.nbp.cobblemon_smartphone.CobblemonSmartphone
import com.nbp.cobblemon_smartphone.network.packet.LikePostPacket
import com.nbp.cobblemon_smartphone.social.SocialData
import com.nbp.cobblemon_smartphone.network.SocialRequestLimiter
import com.nbp.cobblemon_smartphone.network.packet.FeedPostUpdatePacket
import com.nbp.cobblemon_smartphone.social.SocialPostView
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer

object LikePostHandler : ServerNetworkPacketHandler<LikePostPacket> {
    override fun handle(packet: LikePostPacket, server: MinecraftServer, player: ServerPlayer) {
        server.execute {
            if (!CobblemonSmartphone.config.features.enableSocial) return@execute
            if (!SocialRequestLimiter.allow(player.uuid, SocialRequestLimiter.Action.LIKE)) return@execute

            val data = SocialData.get(server)
            // A post-banned player may still read the feed, but not interact with it.
            if (data.isPostBanned(player.uuid)) return@execute

            if (data.setLike(packet.postId, player.uuid, packet.liked)) {
                val post = data.postById(packet.postId) ?: return@execute
                server.playerList.players.forEach { viewer ->
                    FeedPostUpdatePacket(0L, SocialPostView.of(post, viewer.uuid, server)).sendToPlayer(viewer)
                }

                if (packet.liked && post.authorUuid != player.uuid) {
                    val notif = data.addNotification(
                        targetUuid = post.authorUuid,
                        actorUuid = player.uuid,
                        actorName = player.gameProfile.name,
                        type = com.nbp.cobblemon_smartphone.social.SocialNotificationType.LIKE,
                        referenceId = post.id
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
            }
        }
    }
}
