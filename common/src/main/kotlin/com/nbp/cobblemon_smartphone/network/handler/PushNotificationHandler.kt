package com.nbp.cobblemon_smartphone.network.handler

import com.cobblemon.mod.common.api.net.ClientNetworkPacketHandler
import com.nbp.cobblemon_smartphone.client.social.SocialNotificationCache
import com.nbp.cobblemon_smartphone.network.packet.PushNotificationPacket
import net.minecraft.client.Minecraft

object PushNotificationHandler : ClientNetworkPacketHandler<PushNotificationPacket> {
    override fun handle(packet: PushNotificationPacket, client: Minecraft) {
        SocialNotificationCache.push(packet.notification)
    }
}
