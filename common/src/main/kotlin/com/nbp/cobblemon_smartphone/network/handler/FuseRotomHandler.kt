package com.nbp.cobblemon_smartphone.network.handler

import com.cobblemon.mod.common.api.net.ServerNetworkPacketHandler
import com.nbp.cobblemon_smartphone.network.packet.FuseRotomPacket
import com.nbp.cobblemon_smartphone.server.RotomServerManager
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer

object FuseRotomHandler : ServerNetworkPacketHandler<FuseRotomPacket> {
    override fun handle(packet: FuseRotomPacket, server: MinecraftServer, player: ServerPlayer) {
        server.execute {
            RotomServerManager.fuseRotom(player, packet.entityUuid)
        }
    }
}
