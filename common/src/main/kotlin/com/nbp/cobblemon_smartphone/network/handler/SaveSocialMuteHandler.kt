package com.nbp.cobblemon_smartphone.network.handler

import com.cobblemon.mod.common.api.net.ServerNetworkPacketHandler
import com.nbp.cobblemon_smartphone.network.packet.SaveSocialMutePacket
import com.nbp.cobblemon_smartphone.util.SocialMuteStorage
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer

object SaveSocialMuteHandler : ServerNetworkPacketHandler<SaveSocialMutePacket> {
    override fun handle(packet: SaveSocialMutePacket, server: MinecraftServer, player: ServerPlayer) {
        server.execute {
            SocialMuteStorage.write(player, packet.muted)
        }
    }
}
