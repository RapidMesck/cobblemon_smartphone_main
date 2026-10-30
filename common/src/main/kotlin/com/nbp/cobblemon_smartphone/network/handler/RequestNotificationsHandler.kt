package com.nbp.cobblemon_smartphone.network.handler

import com.cobblemon.mod.common.api.net.ServerNetworkPacketHandler
import com.nbp.cobblemon_smartphone.CobblemonSmartphone
import com.nbp.cobblemon_smartphone.network.SocialRequestLimiter
import com.nbp.cobblemon_smartphone.network.packet.NotificationListPacket
import com.nbp.cobblemon_smartphone.network.packet.RequestNotificationsPacket
import com.nbp.cobblemon_smartphone.social.SocialData
import com.nbp.cobblemon_smartphone.social.SocialNotificationView
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer

object RequestNotificationsHandler : ServerNetworkPacketHandler<RequestNotificationsPacket> {
    override fun handle(packet: RequestNotificationsPacket, server: MinecraftServer, player: ServerPlayer) {
        server.execute {
            if (!CobblemonSmartphone.config.features.enableSocial) return@execute
            if (!SocialRequestLimiter.allow(player.uuid, SocialRequestLimiter.Action.REQUEST_NOTIFICATIONS)) return@execute

            val data = SocialData.get(server)
            val notifs = data.notificationsFor(player.uuid)
            val views = notifs.map { SocialNotificationView.of(it, server) }
            NotificationListPacket(views).sendToPlayer(player)
        }
    }
}
