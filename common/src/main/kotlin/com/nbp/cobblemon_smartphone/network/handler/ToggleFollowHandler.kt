package com.nbp.cobblemon_smartphone.network.handler

import com.cobblemon.mod.common.api.net.ServerNetworkPacketHandler
import com.nbp.cobblemon_smartphone.CobblemonSmartphone
import com.nbp.cobblemon_smartphone.network.SocialRequestLimiter
import com.nbp.cobblemon_smartphone.network.packet.ProfileResponsePacket
import com.nbp.cobblemon_smartphone.network.packet.ToggleFollowPacket
import com.nbp.cobblemon_smartphone.social.SocialData
import com.nbp.cobblemon_smartphone.social.SocialPostView
import com.nbp.cobblemon_smartphone.social.SocialProfileView
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer

object ToggleFollowHandler : ServerNetworkPacketHandler<ToggleFollowPacket> {
    override fun handle(packet: ToggleFollowPacket, server: MinecraftServer, player: ServerPlayer) {
        server.execute {
            if (!CobblemonSmartphone.config.features.enableSocial) return@execute
            if (!SocialRequestLimiter.allow(player.uuid, SocialRequestLimiter.Action.TOGGLE_FOLLOW)) return@execute

            val data = SocialData.get(server)
            val targetPlayer = server.playerList.getPlayer(packet.targetUuid)
            val targetName = targetPlayer?.gameProfile?.name ?: data.profileFor(packet.targetUuid).name

            val isNowFollowing = data.toggleFollow(player.uuid, player.gameProfile.name, packet.targetUuid, targetName)

            if (isNowFollowing) {
                val notif = data.addNotification(
                    targetUuid = packet.targetUuid,
                    actorUuid = player.uuid,
                    actorName = player.gameProfile.name,
                    type = com.nbp.cobblemon_smartphone.social.SocialNotificationType.FOLLOW
                )
                if (notif != null && targetPlayer != null) {
                    com.nbp.cobblemon_smartphone.network.packet.PushNotificationPacket(
                        com.nbp.cobblemon_smartphone.social.SocialNotificationView.of(notif, server)
                    ).sendToPlayer(targetPlayer)
                }
            }

            val profile = data.profileFor(packet.targetUuid, targetName)
            val userPosts = data.feed().filter { it.authorUuid == packet.targetUuid }
            val postViews = userPosts.map { SocialPostView.of(it, player.uuid, server) }
            val profileView = SocialProfileView.of(profile, player.uuid, userPosts.size, targetPlayer != null, server)

            ProfileResponsePacket(profileView, postViews).sendToPlayer(player)
        }
    }
}
