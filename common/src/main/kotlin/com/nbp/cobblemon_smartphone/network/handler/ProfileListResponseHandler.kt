package com.nbp.cobblemon_smartphone.network.handler

import com.cobblemon.mod.common.api.net.ClientNetworkPacketHandler
import com.nbp.cobblemon_smartphone.client.social.SocialProfileCache
import com.nbp.cobblemon_smartphone.network.packet.ProfileListResponsePacket
import net.minecraft.client.Minecraft

object ProfileListResponseHandler : ClientNetworkPacketHandler<ProfileListResponsePacket> {
    override fun handle(packet: ProfileListResponsePacket, client: Minecraft) {
        client.execute {
            SocialProfileCache.acceptList(packet.targetUuid, packet.listType, packet.trainers)
        }
    }
}
