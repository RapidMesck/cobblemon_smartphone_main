package com.nbp.cobblemon_smartphone.network.handler

import com.cobblemon.mod.common.api.net.ClientNetworkPacketHandler
import com.nbp.cobblemon_smartphone.client.social.SocialClientSession
import com.nbp.cobblemon_smartphone.network.packet.SocialCapabilitiesPacket
import net.minecraft.client.Minecraft

object SocialCapabilitiesHandler : ClientNetworkPacketHandler<SocialCapabilitiesPacket> {
    override fun handle(packet: SocialCapabilitiesPacket, client: Minecraft) {
        SocialClientSession.apply(packet.capabilities)
    }
}
