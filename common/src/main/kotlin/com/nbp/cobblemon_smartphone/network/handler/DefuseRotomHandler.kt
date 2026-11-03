package com.nbp.cobblemon_smartphone.network.handler

import com.cobblemon.mod.common.api.net.ServerNetworkPacketHandler
import com.nbp.cobblemon_smartphone.network.packet.DefuseRotomPacket
import com.nbp.cobblemon_smartphone.server.RotomServerManager
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer

object DefuseRotomHandler : ServerNetworkPacketHandler<DefuseRotomPacket> {
    override fun handle(packet: DefuseRotomPacket, server: MinecraftServer, player: ServerPlayer) {
        server.execute {
            RotomServerManager.defuseRotom(player)
        }
    }
}
