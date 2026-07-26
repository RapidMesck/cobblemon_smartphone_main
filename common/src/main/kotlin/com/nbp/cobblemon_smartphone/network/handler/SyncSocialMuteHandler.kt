package com.nbp.cobblemon_smartphone.network.handler

import com.cobblemon.mod.common.api.net.ClientNetworkPacketHandler
import com.nbp.cobblemon_smartphone.client.social.SocialMute
import com.nbp.cobblemon_smartphone.network.packet.SyncSocialMutePacket
import net.minecraft.client.Minecraft

object SyncSocialMuteHandler : ClientNetworkPacketHandler<SyncSocialMutePacket> {
    override fun handle(packet: SyncSocialMutePacket, client: Minecraft) {
        SocialMute.set(packet.muted)
    }
}
