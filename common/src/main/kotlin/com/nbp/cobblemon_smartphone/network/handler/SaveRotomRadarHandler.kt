package com.nbp.cobblemon_smartphone.network.handler

import com.cobblemon.mod.common.api.net.ServerNetworkPacketHandler
import com.nbp.cobblemon_smartphone.network.packet.SaveRotomRadarPacket
import com.nbp.cobblemon_smartphone.network.packet.SyncRotomRadarPacket
import com.nbp.cobblemon_smartphone.rotom.RotomRadarStorage
import com.nbp.cobblemon_smartphone.server.RotomServerManager
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer

object SaveRotomRadarHandler : ServerNetworkPacketHandler<SaveRotomRadarPacket> {
    override fun handle(packet: SaveRotomRadarPacket, server: MinecraftServer, player: ServerPlayer) {
        server.execute {
            RotomRadarStorage.write(player, packet.config)
            RotomServerManager.updatePlayerConfig(player.uuid, packet.config)
            SyncRotomRadarPacket(packet.config).sendToPlayer(player)
        }
    }
}
