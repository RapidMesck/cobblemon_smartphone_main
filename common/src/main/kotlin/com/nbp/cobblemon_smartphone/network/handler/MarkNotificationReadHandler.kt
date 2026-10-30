package com.nbp.cobblemon_smartphone.network.handler

import com.cobblemon.mod.common.api.net.ServerNetworkPacketHandler
import com.nbp.cobblemon_smartphone.CobblemonSmartphone
import com.nbp.cobblemon_smartphone.network.SocialRequestLimiter
import com.nbp.cobblemon_smartphone.network.packet.MarkNotificationReadPacket
import com.nbp.cobblemon_smartphone.social.SocialData
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer

object MarkNotificationReadHandler : ServerNetworkPacketHandler<MarkNotificationReadPacket> {
    override fun handle(packet: MarkNotificationReadPacket, server: MinecraftServer, player: ServerPlayer) {
        server.execute {
            if (!CobblemonSmartphone.config.features.enableSocial) return@execute
            if (!SocialRequestLimiter.allow(player.uuid, SocialRequestLimiter.Action.MARK_NOTIFICATION_READ)) return@execute

            val data = SocialData.get(server)
            data.markNotificationRead(player.uuid, packet.notificationId)
        }
    }
}
