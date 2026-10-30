package com.nbp.cobblemon_smartphone.network.handler

import com.cobblemon.mod.common.api.net.ClientNetworkPacketHandler
import com.nbp.cobblemon_smartphone.client.social.SocialProfileCache
import com.nbp.cobblemon_smartphone.network.packet.ProfileResponsePacket
import net.minecraft.client.Minecraft

object ProfileResponseHandler : ClientNetworkPacketHandler<ProfileResponsePacket> {
    override fun handle(packet: ProfileResponsePacket, client: Minecraft) {
        SocialProfileCache.accept(packet.profile, packet.posts)
    }
}
