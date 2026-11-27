package com.nbp.cobblemon_smartphone.network.handler

import com.cobblemon.mod.common.api.net.ServerNetworkPacketHandler
import com.nbp.cobblemon_smartphone.network.packet.RotomDroneStatePacket
import com.nbp.cobblemon_smartphone.server.RotomDroneServerManager
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer

object RotomDroneStateHandler : ServerNetworkPacketHandler<RotomDroneStatePacket> {
    override fun handle(packet: RotomDroneStatePacket, server: MinecraftServer, player: ServerPlayer) {
        server.execute {
            RotomDroneServerManager.handleClientUpdate(player, packet)
        }
    }
}
