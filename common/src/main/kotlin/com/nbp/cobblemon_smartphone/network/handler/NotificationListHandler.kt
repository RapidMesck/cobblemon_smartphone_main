package com.nbp.cobblemon_smartphone.network.handler

import com.cobblemon.mod.common.api.net.ClientNetworkPacketHandler
import com.nbp.cobblemon_smartphone.client.social.SocialNotificationCache
import com.nbp.cobblemon_smartphone.network.packet.NotificationListPacket
import net.minecraft.client.Minecraft

object NotificationListHandler : ClientNetworkPacketHandler<NotificationListPacket> {
    override fun handle(packet: NotificationListPacket, client: Minecraft) {
        SocialNotificationCache.acceptList(packet.notifications)
    }
}
