package com.nbp.cobblemon_smartphone.network.handler

import com.cobblemon.mod.common.api.net.ServerNetworkPacketHandler
import com.nbp.cobblemon_smartphone.network.packet.RequestDatapackScreenDataPacket
import com.nbp.cobblemon_smartphone.ui.datapack.DatapackScreenExecutor
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer

object RequestDatapackScreenDataHandler : ServerNetworkPacketHandler<RequestDatapackScreenDataPacket> {
    override fun handle(packet: RequestDatapackScreenDataPacket, server: MinecraftServer, player: ServerPlayer) {
        server.execute {
            DatapackScreenExecutor.sendDataSnapshot(player, packet.screenId)
        }
    }
}
